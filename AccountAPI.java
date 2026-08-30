package com.server.economy.api;

import com.server.economy.economy.EconomyManager;
import com.server.economy.model.Account;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for reading account information. Obtained via {@link EconomyAPI#accounts()}.
 */
public final class AccountAPI {

    private final EconomyManager economyManager;

    public AccountAPI(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    /**
     * Returns {@code true} if the player has an economy account, checking the
     * in-memory cache only (does not query the database).
     */
    public boolean hasAccount(UUID playerId) {
        return economyManager.hasAccount(playerId);
    }

    /**
     * Returns the cached account for a player, if currently loaded (i.e. the
     * player is online or was recently online).
     */
    public Optional<Account> getCachedAccount(UUID playerId) {
        return economyManager.getCachedAccount(playerId);
    }

    /**
     * Asynchronously fetches an account by UUID, checking the cache first and
     * falling back to the database.
     */
    public CompletableFuture<Optional<Account>> getAccount(UUID playerId) {
        return economyManager.getAccount(playerId);
    }

    /**
     * Asynchronously fetches an account by player name.
     */
    public CompletableFuture<Optional<Account>> getAccount(String playerName) {
        return economyManager.getAccount(playerName);
    }

    /**
     * Ensures an account exists for the given player, creating one with the
     * configured starting balance if necessary.
     */
    public CompletableFuture<Account> loadOrCreateAccount(UUID playerId, String playerName) {
        return economyManager.loadOrCreateAccount(playerId, playerName);
    }
}
