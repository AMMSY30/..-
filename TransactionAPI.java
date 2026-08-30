package com.server.economy.api;

import com.server.economy.economy.EconomyManager;
import com.server.economy.model.EconomyResult;
import com.server.economy.model.Transaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for initiating transfers and reading transaction history.
 * Obtained via {@link EconomyAPI#transactions()}.
 */
public final class TransactionAPI {

    private final EconomyManager economyManager;

    public TransactionAPI(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    /**
     * Transfers money from one player to another, applying the same validation
     * and atomicity guarantees as the {@code /pay} command.
     */
    public CompletableFuture<EconomyResult> transfer(UUID senderId, UUID receiverId, BigDecimal amount) {
        return economyManager.pay(senderId, receiverId, amount);
    }

    /**
     * Returns the most recent transactions involving the given player, most
     * recent first, up to {@code limit} entries (capped at 100).
     */
    public CompletableFuture<List<Transaction>> getHistory(UUID playerId, int limit) {
        return economyManager.getHistory(playerId, limit);
    }
}
