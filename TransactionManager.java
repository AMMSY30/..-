package com.server.economy.economy;

import com.server.economy.database.DatabaseManager;
import com.server.economy.model.Transaction;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Handles reading and writing transaction history records.
 *
 * <p>Writes made as part of a payment are recorded inline on the payment's own
 * JDBC connection/transaction by {@link PaymentManager}; this class is used for
 * standalone writes (admin operations) and for all history reads.</p>
 */
public final class TransactionManager {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final Executor asyncExecutor;

    public TransactionManager(Plugin plugin, DatabaseManager databaseManager, Executor asyncExecutor) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<Transaction> record(Transaction transaction) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return databaseManager.getTransactionRepository().record(transaction);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to record transaction: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }

    public CompletableFuture<List<Transaction>> getHistory(UUID playerId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return databaseManager.getTransactionRepository().findHistory(playerId, limit);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to fetch transaction history: " + e.getMessage());
                throw new CompletionException(e);
            }
        }, asyncExecutor);
    }
}
