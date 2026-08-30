package com.server.economy.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * Implements {@code /money [player]} as a friendly alias of {@code /balance}.
 */
public final class MoneyCommand implements CommandExecutor, TabCompleter {

    private final BalanceCommand balanceCommand;

    public MoneyCommand(BalanceCommand balanceCommand) {
        this.balanceCommand = balanceCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return balanceCommand.onCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return balanceCommand.onTabComplete(sender, command, alias, args);
    }
}
