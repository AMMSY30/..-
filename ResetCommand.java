package com.server.economy.commands.subcommands;

import com.server.economy.config.MessageManager;
import com.server.economy.economy.EconomyManager;
import com.server.economy.security.PermissionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements {@code /economy reset <player>}.
 */
public final class ResetCommand implements Subcommand {

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public ResetCommand(Plugin plugin, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public String getName() {
        return "reset";
    }

    @Override
    public String getPermission() {
        return PermissionManager.ADMIN_RESET;
    }

    @Override
    public String getUsage() {
        return "/economy reset <player>";
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
            economyManager.adminReset(account.getPlayerId())
                    .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!result.isSuccess()) {
                            messages.send(sender, "errors.generic", "reason", result.getMessage());
                            return;
                        }
                        messages.send(sender, "admin.reset", "player", account.getPlayerName());
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
