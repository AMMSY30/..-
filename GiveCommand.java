package com.server.economy.commands.subcommands;

import com.server.economy.config.MessageManager;
import com.server.economy.economy.EconomyManager;
import com.server.economy.security.PermissionManager;
import com.server.economy.util.FormatUtil;
import com.server.economy.util.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements {@code /economy give <player> <amount>}.
 */
public final class GiveCommand implements Subcommand {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public GiveCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public String getPermission() {
        return PermissionManager.ADMIN_GIVE;
    }

    @Override
    public String getUsage() {
        return "/economy give <player> <amount>";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 2) {
            messages.send(sender, "usage.generic", "usage", getUsage());
            return;
        }

        String targetName = args[0];
        var amountOpt = NumberUtil.parseAmount(args[1]);
        if (amountOpt.isEmpty() || NumberUtil.isNegativeOrZero(amountOpt.get())) {
            messages.send(sender, "errors.invalid-amount", "input", args[1]);
            return;
        }

        economyManager.getAccount(targetName).thenAccept(accountOpt -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (accountOpt.isEmpty()) {
                messages.send(sender, "errors.player-not-found", "player", targetName);
                return;
            }
            var account = accountOpt.get();
            economyManager.adminGive(account.getPlayerId(), amountOpt.get(), sender.getName())
                    .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!result.isSuccess()) {
                            messages.send(sender, "errors.generic", "reason", result.getMessage());
                            return;
                        }
                        String formatted = FormatUtil.formatCurrency(amountOpt.get(), economyManager.getConfigManager());
                        messages.send(sender, "admin.give", "amount", formatted, "player", account.getPlayerName());
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
