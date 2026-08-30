package com.server.economy;

import com.server.economy.api.EconomyAPI;
import com.server.economy.commands.BalanceCommand;
import com.server.economy.commands.EconomyCommand;
import com.server.economy.commands.MoneyCommand;
import com.server.economy.commands.PayCommand;
import com.server.economy.commands.subcommands.GiveCommand;
import com.server.economy.commands.subcommands.HistoryCommand;
import com.server.economy.commands.subcommands.ResetCommand;
import com.server.economy.commands.subcommands.SetCommand;
import com.server.economy.commands.subcommands.Subcommand;
import com.server.economy.commands.subcommands.TakeCommand;
import com.server.economy.commands.subcommands.ReloadCommand;
import com.server.economy.config.ConfigManager;
import com.server.economy.config.MessageManager;
import com.server.economy.database.DatabaseManager;
import com.server.economy.economy.EconomyManager;
import com.server.economy.hooks.PlaceholderAPIHook;
import com.server.economy.hooks.VaultHook;
import com.server.economy.listeners.PlayerJoinListener;
import com.server.economy.listeners.PlayerQuitListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main entry point for the EconomyCore plugin.
 *
 * <p>Startup sequence: load configuration, connect to MySQL and run migrations
 * on a background thread, then - only once the database is confirmed reachable -
 * wire up the economy managers, commands, listeners, and optional integrations.
 * If the database cannot be reached, the plugin disables itself rather than
 * running in a broken state.</p>
 */
public final class EconomyCore extends JavaPlugin {

    private ExecutorService databaseExecutor;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private EconomyAPI economyAPI;
    private VaultHook vaultHook;

    @Override
    public void onEnable() {
        this.databaseExecutor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "EconomyCore-DB-Worker");
            thread.setDaemon(true);
            return thread;
        });

        this.configManager = new ConfigManager(this);
        configManager.load();

        this.messageManager = new MessageManager(this);
        messageManager.load();

        this.databaseManager = new DatabaseManager(this);

        // Connecting to MySQL is blocking I/O and must never run on the main thread.
        CompletableFuture.runAsync(() -> databaseManager.initialize(configManager), databaseExecutor)
                .thenRun(() -> Bukkit.getScheduler().runTask(this, this::finishEnable))
                .exceptionally(throwable -> {
                    getLogger().severe("Failed to connect to the MySQL database: " + throwable.getMessage());
                    Bukkit.getScheduler().runTask(this, () -> {
                        getLogger().severe("EconomyCore cannot start without a working database connection. "
                                + "Check database.yml and disable the plugin.");
                        Bukkit.getPluginManager().disablePlugin(this);
                    });
                    return null;
                });
    }

    /**
     * Runs on the main thread once the database connection is confirmed healthy.
     */
    private void finishEnable() {
        this.economyManager = new EconomyManager(this, databaseManager, configManager, databaseExecutor);
        this.economyAPI = new EconomyAPI(economyManager, configManager);

        Bukkit.getServicesManager().register(EconomyAPI.class, economyAPI, this, ServicePriority.Normal);

        registerCommands();
        registerListeners();
        registerHooks();

        getLogger().info("EconomyCore has been enabled successfully.");
    }

    private void registerCommands() {
        BalanceCommand balanceCommand = new BalanceCommand(this, economyManager, messageManager);
        setExecutor("balance", balanceCommand, balanceCommand);
        MoneyCommand moneyCommand = new MoneyCommand(balanceCommand);
        setExecutor("money", moneyCommand, moneyCommand);
        PayCommand payCommand = new PayCommand(this, economyManager, messageManager);
        setExecutor("pay", payCommand, payCommand);

        List<Subcommand> subcommands = List.of(
                new GiveCommand(this, economyManager, messageManager),
                new TakeCommand(this, economyManager, messageManager),
                new SetCommand(this, economyManager, messageManager),
                new ResetCommand(this, economyManager, messageManager),
                new HistoryCommand(this, economyManager, messageManager),
                new ReloadCommand(configManager, messageManager)
        );
        EconomyCommand economyCommand = new EconomyCommand(messageManager, subcommands);
        setExecutor("economy", economyCommand, economyCommand);
    }

    private void setExecutor(String name, org.bukkit.command.CommandExecutor executor,
                              org.bukkit.command.TabCompleter completer) {
        var command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml and could not be registered.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this, economyManager), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(economyManager), this);
    }

    private void registerHooks() {
        this.vaultHook = new VaultHook(this, economyManager);
        vaultHook.register();

        new PlaceholderAPIHook(this, economyManager).registerIfAvailable();
    }

    @Override
    public void onDisable() {
        if (vaultHook != null) {
            vaultHook.unregister();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        if (databaseExecutor != null) {
            databaseExecutor.shutdown();
            try {
                if (!databaseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    databaseExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                databaseExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        getLogger().info("EconomyCore has been disabled.");
    }

    public EconomyAPI getEconomyAPI() {
        return economyAPI;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }
}
