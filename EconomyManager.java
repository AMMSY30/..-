package com.server.economy.economy;

import com.server.economy.config.ConfigManager;
import com.server.economy.database.DatabaseManager;
import com.server.economy.model.Account;
import com.server.economy.model.EconomyResult;
import com.server.economy.model.Transaction;
import com.server.economy.model.TransactionType;
import com.server.economy.security.EconomySecurity;
import com.server.economy.security.TransactionValidator;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Top-level entry point into the economy system. Wires together the account,
 * balance, payment, and transaction managers and exposes a single cohesive API
 * for commands, hooks, and the public plugin API to use.
 */
public final class EconomyManager {

    private final AccountManager accountManager;
    private final BalanceManager balanceManager;
    private final PaymentManager paymentManager;
    private final TransactionManager transactionManager;
    private final ConfigManager configManager;

    public EconomyManager(Plugin plugin, DatabaseManager databaseManager, ConfigManager configManager,
                           Executor asyncExecutor) {
        this.configManager = configManager;

        TransactionValidator validator = new TransactionValidator(configManager);
        EconomySecurity security = new EconomySecurity(plugin);

        this.accountManager = new AccountManager(plugin, databaseManager, configManager, asyncExecutor);
        this.balanceManager = new BalanceManager(plugin, databaseManager, configManager, accountManager,
                validator, security, asyncExecutor);
        this.paymentManager = new PaymentManager(plugin, databaseManager, configManager, accountManager,
                validator, security, asyncExecutor);
        this.transactionManager = new TransactionManager(plugin, databaseManager, asyncExecutor);
    }

    // ----- Account lifecycle -----

    public CompletableFuture<Account> loadOrCreateAccount(UUID playerId, String playerName) {
        return accountManager.loadOrCreate(playerId, playerName);
    }

    public void unloadAccount(UUID playerId) {
        accountManager.unload(playerId);
    }

    public boolean hasAccount(UUID playerId) {
        return accountManager.hasAccount(playerId);
    }

    public Optional<Account> getCachedAccount(UUID playerId) {
        return accountManager.getCached(playerId);
    }

    public CompletableFuture<Optional<Account>> getAccount(UUID playerId) {
        return accountManager.fetch(playerId);
    }

    public CompletableFuture<Optional<Account>> getAccount(String playerName) {
        return accountManager.fetchByName(playerName);
    }

    // ----- Balance operations -----

    public BigDecimal getBalance(UUID playerId) {
        return balanceManager.getBalance(playerId);
    }

    /**
     * Resolves a player's balance reliably, whether or not the account is
     * currently cached. Checks the in-memory cache first (no I/O for online
     * or recently-online players) and falls back to an asynchronous database
     * read for accounts that are not loaded - typically offline players being
     * looked up by another plugin (shops, leaderboards, etc).
     *
     * <p>Prefer this over {@link #getBalance(UUID)} whenever the target
     * player may be offline, since {@code getBalance} silently returns
     * {@link BigDecimal#ZERO} for any account that isn't cached.</p>
     */
    public CompletableFuture<BigDecimal> getBalanceAsync(UUID playerId) {
        return accountManager.fetch(playerId)
                .thenApply(accountOpt -> accountOpt.map(Account::getBalance).orElse(BigDecimal.ZERO));
    }

    public boolean canAfford(UUID playerId, BigDecimal amount) {
        return balanceManager.canAfford(playerId, amount);
    }

    public CompletableFuture<EconomyResult> deposit(UUID playerId, BigDecimal amount) {
        return balanceManager.deposit(playerId, amount, TransactionType.API_DEPOSIT, "API deposit");
    }

    public CompletableFuture<EconomyResult> withdraw(UUID playerId, BigDecimal amount) {
        return balanceManager.withdraw(playerId, amount, TransactionType.API_WITHDRAW, "API withdraw");
    }

    public CompletableFuture<EconomyResult> adminGive(UUID playerId, BigDecimal amount, String executorName) {
        return balanceManager.deposit(playerId, amount, TransactionType.ADMIN_GIVE, "given by " + executorName);
    }

    public CompletableFuture<EconomyResult> adminTake(UUID playerId, BigDecimal amount, String executorName) {
        return balanceManager.withdraw(playerId, amount, TransactionType.ADMIN_TAKE, "taken by " + executorName);
    }

    public CompletableFuture<EconomyResult> adminSet(UUID playerId, BigDecimal amount) {
        return balanceManager.setBalance(playerId, amount);
    }

    public CompletableFuture<EconomyResult> adminReset(UUID playerId) {
        return balanceManager.resetBalance(playerId);
    }

    // ----- Payments -----

    public CompletableFuture<EconomyResult> pay(UUID senderId, UUID receiverId, BigDecimal amount) {
        return paymentManager.pay(senderId, receiverId, amount);
    }

    // ----- Transactions -----

    public CompletableFuture<List<Transaction>> getHistory(UUID playerId, int limit) {
        return transactionManager.getHistory(playerId, limit);
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
