package com.server.economy.listeners;

import com.server.economy.economy.EconomyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Removes a player's account from the in-memory cache on quit. Balances are
 * always written through to the database on every mutation, so there is no
 * "save on quit" step required - this purely frees memory.
 */
public final class PlayerQuitListener implements Listener {

    private final EconomyManager economyManager;

    public PlayerQuitListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        economyManager.unloadAccount(event.getPlayer().getUniqueId());
    }
}
