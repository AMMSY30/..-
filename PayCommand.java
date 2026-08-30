package com.server.economy.commands;

import com.server.economy.config.MessageManager;
import com.server.economy.economy.EconomyManager;
import com.server.economy.security.PermissionManager;
import com.server.economy.util.FormatUtil;
import com.server.economy.util.NumberUtil;
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
 * Implements {@code /pay <player> <amount>}.
 */
public final class PayCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public PayCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            messages.send(sender, "errors.player-only");
            return true;
        }

        if (!PermissionManager.has(sender, PermissionManager.PAY)) {
            messages.send(sender, "errors.no-permission");
            return true;
        }

        if (args.length != 2) {
            messages.send(sender, "usage.pay");
            return true;
        }

        String targetName = args[0];
        var amountOpt = NumberUtil.parseAmount(args[1]);
        if (amountOpt.isEmpty() || NumberUtil.isNegativeOrZero(amountOpt.get())) {
            messages.send(sender, "errors.invalid-amount", "input", args[1]);
            return true;
        }

        Player targetOnline = Bukkit.getPlayerExact(targetName);
        if (targetOnline != null && targetOnline.getUniqueId().equals(payer.getUniqueId())) {
            messages.send(sender, "errors.cannot-pay-self");
            return true;
        }

        economyManager.getAccount(targetName).thenAccept(targetOpt -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (targetOpt.isEmpty()) {
                messages.send(sender, "errors.player-not-found", "player", targetName);
                return;
            }

            var target = targetOpt.get();
            economyManager.pay(payer.getUniqueId(), target.getPlayerId(), amountOpt.get())
                    .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!result.isSuccess()) {
                            messages.send(sender, "errors.generic", "reason", result.getMessage());
                            return;
                        }
                        String formatted = FormatUtil.formatCurrency(amountOpt.get(), economyManager.getConfigManager());
                        messages.send(sender, "pay.sent", "amount", formatted, "player", target.getPlayerName());

                        Player targetPlayer = Bukkit.getPlayer(target.getPlayerId());
                        if (targetPlayer != null) {
                            messages.send(targetPlayer, "pay.received", "amount", formatted, "player", payer.getName());
                        }
                    })).exceptionally(throwable -> {
                        Bukkit.getScheduler().runTask(plugin, () -> messages.send(sender, "errors.database-error"));
                        return null;
                    });
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
