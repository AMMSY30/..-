package com.server.economy.commands.subcommands;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * A single {@code /economy <subcommand>} handler.
 */
public interface Subcommand {

    /**
     * The subcommand's literal name, e.g. {@code "give"}.
     */
    String getName();

    /**
     * The permission required to run this subcommand.
     */
    String getPermission();

    /**
     * A short usage string shown on invalid arguments, e.g. {@code "/economy give <player> <amount>"}.
     */
    String getUsage();

    /**
     * Executes the subcommand. {@code args} excludes the subcommand literal itself.
     */
    void execute(CommandSender sender, String[] args);

    /**
     * Provides tab completion suggestions. {@code args} excludes the subcommand literal itself.
     */
    default List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
