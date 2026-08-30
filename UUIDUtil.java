package com.server.economy.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Helpers for resolving player names to UUIDs and back without duplicating
 * Bukkit lookup logic throughout the plugin.
 */
public final class UUIDUtil {

    private UUIDUtil() {
    }

    /**
     * Resolves a player name to a UUID, checking online players first and
     * falling back to the offline-player cache. Does not perform a blocking
     * network lookup against Mojang; the caller is responsible for ensuring
     * the target has played on the server before, or has an existing account.
     */
    public static Optional<UUID> resolveUuid(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }

        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(playerName);
        if (offline != null && offline.hasPlayedBefore()) {
            return Optional.of(offline.getUniqueId());
        }

        return Optional.empty();
    }

    public static String resolveName(UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return name != null ? name : "Unknown";
    }

    public static boolean isValidUuid(String candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            UUID.fromString(candidate);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
