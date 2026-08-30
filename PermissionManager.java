package com.server.economy.security;

import org.bukkit.command.CommandSender;

/**
 * Central registry of permission node constants and lookup helpers.
 * Keeping permission strings in one place avoids typos scattered across commands.
 */
public final class PermissionManager {

    public static final String BALANCE = "economy.balance";
    public static final String PAY = "economy.pay";
    public static final String HISTORY = "economy.history";

    public static final String ADMIN = "economy.admin";
    public static final String ADMIN_GIVE = "economy.admin.give";
    public static final String ADMIN_TAKE = "economy.admin.take";
    public static final String ADMIN_SET = "economy.admin.set";
    public static final String ADMIN_RESET = "economy.admin.reset";

    public static final String RELOAD = "economy.reload";

    private PermissionManager() {
    }

    public static boolean has(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    /**
     * Returns true if the sender has either the specific admin sub-permission
     * or the umbrella {@code economy.admin} permission.
     */
    public static boolean hasAdminOrSpecific(CommandSender sender, String specificPermission) {
        return sender.hasPermission(ADMIN) || sender.hasPermission(specificPermission);
    }
}
