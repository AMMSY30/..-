package com.server.economy.commands;

import com.server.economy.commands.subcommands.Subcommand;
import com.server.economy.config.MessageManager;
import com.server.economy.security.PermissionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements {@code /economy <subcommand> ...}, dispatching to the registered
 * {@link Subcommand} implementations.
 */
public final class EconomyCommand implements CommandExecutor, TabCompleter {

    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();
    private final MessageManager messages;

    public EconomyCommand(MessageManager messages, List<Subcommand> handlers) {
        this.messages = messages;
        for (Subcommand handler : handlers) {
            subcommands.put(handler.getName().toLowerCase(), handler);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!PermissionManager.has(sender, PermissionManager.ADMIN)
                && subcommands.values().stream().noneMatch(sub -> sender.hasPermission(sub.getPermission()))) {
            messages.send(sender, "errors.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        Subcommand handler = subcommands.get(args[0].toLowerCase());
        if (handler == null) {
            sendHelp(sender);
            return true;
        }

        if (!PermissionManager.hasAdminOrSpecific(sender, handler.getPermission())) {
            messages.send(sender, "errors.no-permission");
            return true;
        }

        String[] remainingArgs = args.length > 1
                ? java.util.Arrays.copyOfRange(args, 1, args.length)
                : new String[0];
        handler.execute(sender, remainingArgs);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        messages.send(sender, "admin.help-header");
        for (Subcommand handler : subcommands.values()) {
            if (PermissionManager.hasAdminOrSpecific(sender, handler.getPermission())) {
                messages.send(sender, "admin.help-entry", "usage", handler.getUsage());
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> results = new ArrayList<>();
            for (Subcommand handler : subcommands.values()) {
                if (handler.getName().startsWith(partial) && PermissionManager.hasAdminOrSpecific(sender, handler.getPermission())) {
                    results.add(handler.getName());
                }
            }
            return results;
        }

        if (args.length > 1) {
            Subcommand handler = subcommands.get(args[0].toLowerCase());
            if (handler != null && PermissionManager.hasAdminOrSpecific(sender, handler.getPermission())) {
                String[] remainingArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
                return handler.tabComplete(sender, remainingArgs);
            }
        }

        return List.of();
    }
}
