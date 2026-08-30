package com.server.economy.commands.subcommands;

import com.server.economy.config.ConfigManager;
import com.server.economy.config.MessageManager;
import com.server.economy.security.PermissionManager;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Implements {@code /economy reload}, reloading {@code config.yml} and {@code messages.yml}.
 * {@code database.yml} is intentionally not hot-reloaded, since changing the
 * active connection pool at runtime risks leaving in-flight transactions in an
 * inconsistent state; a full server restart is required for database changes.
 */
public final class ReloadCommand implements Subcommand {

    private final ConfigManager configManager;
    private final MessageManager messages;

    public ReloadCommand(ConfigManager configManager, MessageManager messages) {
        this.configManager = configManager;
        this.messages = messages;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return PermissionManager.RELOAD;
    }

    @Override
    public String getUsage() {
        return "/economy reload";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        configManager.reload();
        messages.reload();
        messages.send(sender, "admin.reload");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
