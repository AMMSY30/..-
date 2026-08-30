package com.server.economy.commands.subcommands;

import com.server.economy.config.MessageManager;
import com.server.economy.economy.EconomyManager;
import com.server.economy.model.Transaction;
import com.server.economy.security.PermissionManager;
import com.server.economy.util.FormatUtil;
import com.server.economy.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements {@code /economy history <player>}, showing that player's most
 * recent transactions.
 */
public final class HistoryCommand implements Subcommand {

    private static final int MAX_ENTRIES = 10;

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public HistoryCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public String getPermission() {
        return PermissionManager.HISTORY;
    }

    @Override
    public String getUsage() {
        return "/economy history <player>";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            messages.send(sender, "usage.generic", "usage", getUsage());
            return;
        }

        String targetName = args[0];
        economyManager.getAccount(targetName).thenAccept(accountOpt -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (accountOpt.isEmpty()) {
                messages.send(sender, "errors.player-not-found", "player", targetName);
                return;
            }
            var account = accountOpt.get();
            economyManager.getHistory(account.getPlayerId(), MAX_ENTRIES)
                    .thenAccept(history -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (history.isEmpty()) {
                            messages.send(sender, "history.empty", "player", account.getPlayerName());
                            return;
                        }
                        messages.send(sender, "history.header", "player", account.getPlayerName());
                        for (Transaction transaction : history) {
                            String amount = FormatUtil.formatCurrency(transaction.getAmount(), economyManager.getConfigManager());
                            String time = TimeUtil.formatRelative(transaction.getTimestamp());
                            messages.send(sender, "history.entry",
                                    "type", transaction.getType().name(),
                                    "amount", amount,
                                    "time", time);
                        }
                    })).exceptionally(throwable -> {
                        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "errors.database-error"));
                        return null;
                    });
        })).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "errors.database-error"));
            return null;
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
