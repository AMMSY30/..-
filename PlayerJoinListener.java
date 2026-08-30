package com.server.economy.listeners;

import com.server.economy.economy.EconomyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Loads (or creates) a player's economy account asynchronously as soon as they join,
 * so their balance is ready in cache before any command or plugin needs it.
 */
public final class PlayerJoinListener implements Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;

    public PlayerJoinListener(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        economyManager.loadOrCreateAccount(player.getUniqueId(), player.getName())
                .exceptionally(throwable -> {
                    plugin.getLogger().severe("Failed to load economy account for "
                            + player.getName() + ": " + throwable.getMessage());
                    return null;
                });
    }
}
