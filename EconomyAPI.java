package com.server.economy.api;

import com.server.economy.config.ConfigManager;
import com.server.economy.economy.EconomyManager;
import com.server.economy.model.EconomyResult;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main entry point into the EconomyCore public API.
 *
 * <p>Other WhaleMC plugins should obtain an instance via:</p>
 * <pre>{@code
 * RegisteredServiceProvider<EconomyAPI> provider =
 *         Bukkit.getServicesManager().getRegistration(EconomyAPI.class);
 * if (provider != null) {
 *     EconomyAPI economy = provider.getProvider();
 * }
 * }</pre>
 *
 * <p>All balance-mutating operations are asynchronous and return a
 * {@link CompletableFuture}&lt;{@link EconomyResult}&gt; rather than a plain boolean,
 * so callers can distinguish why an operation failed.</p>
 */
public final class EconomyAPI implements EconomyProvider {

    private final EconomyManager economyManager;
    private final ConfigManager configManager;
    private final AccountAPI accountAPI;
    private final TransactionAPI transactionAPI;

    public EconomyAPI(EconomyManager economyManager, ConfigManager configManager) {
        this.economyManager = economyManager;
        this.configManager = configManager;
        this.accountAPI = new AccountAPI(economyManager);
        this.transactionAPI = new TransactionAPI(economyManager);
    }

    public AccountAPI accounts() {
        return accountAPI;
    }

    public TransactionAPI transactions() {
        return transactionAPI;
    }

    /**
     * Returns a player's current balance. Reads from the in-memory cache and
     * returns zero if the player has no loaded account (e.g. an offline
     * player nobody has looked up yet this session). For a reliable read
     * that works for any player regardless of online status, use
     * {@link #getBalanceAsync(UUID)} instead.
     */
    public BigDecimal getBalance(UUID playerId) {
        return economyManager.getBalance(playerId);
    }

    /**
     * Reliably resolves a player's balance whether or not their account is
     * currently cached, falling back to an asynchronous database read for
     * offline players. This is the recommended method for other plugins
     * looking up balances for players who may not be online.
     */
    public CompletableFuture<BigDecimal> getBalanceAsync(UUID playerId) {
        return economyManager.getBalanceAsync(playerId);
    }

    /**
     * Returns {@code true} if the player's balance is at least {@code amount}.
     */
    public boolean canAfford(UUID playerId, BigDecimal amount) {
        return economyManager.canAfford(playerId, amount);
    }

    public CompletableFuture<EconomyResult> deposit(UUID playerId, BigDecimal amount) {
        return economyManager.deposit(playerId, amount);
    }

    public CompletableFuture<EconomyResult> withdraw(UUID playerId, BigDecimal amount) {
        return economyManager.withdraw(playerId, amount);
    }

    public CompletableFuture<EconomyResult> transfer(UUID senderId, UUID receiverId, BigDecimal amount) {
        return economyManager.pay(senderId, receiverId, amount);
    }

    @Override
    public String getCurrencyName() {
        return configManager.getCurrencyName();
    }

    @Override
    public String getCurrencyNamePlural() {
        return configManager.getCurrencyNamePlural();
    }

    @Override
    public String getCurrencySymbol() {
        return configManager.getCurrencySymbol();
    }

    @Override
    public int getDecimalPrecision() {
        return configManager.getDecimalPrecision();
    }
}
