package com.server.economy.commands;

import com.server.economy.config.MessageManager;
import com.server.economy.economy.EconomyManager;
import com.server.economy.security.PermissionManager;
import com.server.economy.util.FormatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements {@code /balance [player]}.
 */
public final class BalanceCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public BalanceCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionManager.has(sender, PermissionManager.BALANCE)) {
            messages.send(sender, "errors.no-permission");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "errors.player-only");
                return true;
            }
            var balance = economyManager.getBalance(player.getUniqueId());
            messages.send(sender, "balance.self", "amount", FormatUtil.formatCurrency(balance, economyManager.getConfigManager()));
            return true;
        }

        String targetName = args[0];
        economyManager.getAccount(targetName).thenAccept(accountOpt -> Bukkit.getScheduler().runTask(
                plugin, () -> {
                    if (accountOpt.isEmpty()) {
                        messages.send(sender, "errors.player-not-found", "player", targetName);
                        return;
                    }
                    var account = accountOpt.get();
                    messages.send(sender, "balance.other",
                            "player", account.getPlayerName(),
                            "amount", FormatUtil.formatCurrency(account.getBalance(), economyManager.getConfigManager()));
                })).exceptionally(throwable -> {
            Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "errors.database-error"));
            return null;
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
