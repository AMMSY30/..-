package com.server.economy.economy;

import com.server.economy.config.ConfigManager;
import com.server.economy.database.DatabaseManager;
import com.server.economy.model.Account;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Owns the in-memory account cache and is responsible for creating new accounts
 * and loading/unloading them as players join and leave.
 *
 * <p>All database access happens off the main thread via the supplied {@link Executor}.
 * The cache itself may be safely read from the main thread once an account is loaded.</p>
 */
public final class AccountManager {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final Executor asyncExecutor;

    private final Map<UUID, Account> cache = new ConcurrentHashMap<>();

    public AccountManager(Plugin plugin, DatabaseManager databaseManager, ConfigManager configManager,
                           Executor asyncExecutor) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * Returns a cached account, if currently loaded.
     */
    public Optional<Account> getCached(UUID playerId) {
        return Optional.ofNullable(cache.get(playerId));
    }

    public void putCache(Account account) {
        cache.put(account.getPlayerId(), account);
    }

    public void invalidate(UUID playerId) {
        cache.remove(playerId);
    }

    /**
     * Loads a player's account from the database into the cache, creating a new
     * account with the configured starting balance if none exists yet. Safe to
     * call from the main thread; the actual database work runs asynchronously.
     */
    public CompletableFuture<Account> loadOrCreate(UUID playerId, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var existing = databaseManager.getPlayerRepository().findByUuid(playerId);
                if (existing.isPresent()) {
                    Account account = existing.get();
                    if (!account.getPlayerName().equals(playerName)) {
                        databaseManager.getPlayerRepository().updatePlayerName(playerId, playerName);
                        account = account.withPlayerName(playerName);
                    }
                    cache.put(playerId, account);
                    return account;
                }

                var startingBalance = configManager.getStartingBalance();
                databaseManager.getPlayerRepository().createAccount(playerId, playerName, startingBalance);
                var created = databaseManager.getPlayerRepository().findByUuid(playerId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Account creation succeeded but the account could not be re-read."));
                cache.put(playerId, created);
                return created;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load/create account for " + playerName + ": " + e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, asyncExecutor);
    }

    /**
     * Removes a player's account from the cache. Balances are always persisted
     * immediately on change, so no explicit save-on-quit is required.
     */
    public void unload(UUID playerId) {
        cache.remove(playerId);
    }

    public CompletableFuture<Optional<Account>> fetch(UUID playerId) {
        Account cached = cache.get(playerId);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return databaseManager.getPlayerRepository().findByUuid(playerId);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to fetch account " + playerId + ": " + e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<Optional<Account>> fetchByName(String playerName) {
        for (Account account : cache.values()) {
            if (account.getPlayerName().equalsIgnoreCase(playerName)) {
                return CompletableFuture.completedFuture(Optional.of(account));
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return databaseManager.getPlayerRepository().findByName(playerName);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to fetch account by name " + playerName + ": " + e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, asyncExecutor);
    }

    public boolean hasAccount(UUID playerId) {
        return cache.containsKey(playerId);
    }
}
