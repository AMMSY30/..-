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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Handles single-account balance mutations: deposits, withdrawals, and admin
 * set/reset operations. Every mutation is performed inside a single JDBC
 * transaction with a row lock ({@code SELECT ... FOR UPDATE}) so concurrent
 * operations against the same account cannot race each other.
 */
public final class BalanceManager {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final AccountManager accountManager;
    private final TransactionValidator validator;
    private final EconomySecurity security;
    private final Executor asyncExecutor;

    public BalanceManager(Plugin plugin, DatabaseManager databaseManager, ConfigManager configManager,
                           AccountManager accountManager, TransactionValidator validator,
                           EconomySecurity security, Executor asyncExecutor) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.accountManager = accountManager;
        this.validator = validator;
        this.security = security;
        this.asyncExecutor = asyncExecutor;
    }

    public BigDecimal getBalance(UUID playerId) {
        return accountManager.getCached(playerId).map(Account::getBalance).orElse(BigDecimal.ZERO);
    }

    public boolean canAfford(UUID playerId, BigDecimal amount) {
        return getBalance(playerId).compareTo(amount) >= 0;
    }

    public CompletableFuture<EconomyResult> deposit(UUID playerId, BigDecimal amount, TransactionType type,
                                                      String metadata) {
        EconomyResult validation = validator.validateAmount(amount);
        if (validation != null) {
            return CompletableFuture.completedFuture(validation);
        }
        return mutate(playerId, amount, true, type, metadata);
    }

    public CompletableFuture<EconomyResult> withdraw(UUID playerId, BigDecimal amount, TransactionType type,
                                                       String metadata) {
        EconomyResult validation = validator.validateAmount(amount);
        if (validation != null) {
            return CompletableFuture.completedFuture(validation);
        }
        return mutate(playerId, amount, false, type, metadata);
    }

    public CompletableFuture<EconomyResult> setBalance(UUID playerId, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            return CompletableFuture.completedFuture(EconomyResult.failure(
                    EconomyResult.Status.INVALID_AMOUNT, "The balance cannot be set to a negative value."));
        }
        if (amount.compareTo(configManager.getMaxBalance()) > 0) {
            return CompletableFuture.completedFuture(EconomyResult.failure(
                    EconomyResult.Status.AMOUNT_TOO_LARGE, "The amount exceeds the maximum allowed balance."));
        }
        return runLocked(playerId, () -> executeSet(playerId, amount, TransactionType.ADMIN_SET, "set by admin"));
    }

    public CompletableFuture<EconomyResult> resetBalance(UUID playerId) {
        BigDecimal startingBalance = configManager.getStartingBalance();
        return runLocked(playerId, () -> executeSet(playerId, startingBalance, TransactionType.ADMIN_RESET,
                "reset to starting balance"));
    }

    private CompletableFuture<EconomyResult> mutate(UUID playerId, BigDecimal amount, boolean isDeposit,
                                                      TransactionType type, String metadata) {
        return runLocked(playerId, () -> {
            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    Optional<Account> maybeAccount =
                            databaseManager.getPlayerRepository().findByUuidForUpdate(connection, playerId);
                    if (maybeAccount.isEmpty()) {
                        connection.rollback();
                        return EconomyResult.failure(EconomyResult.Status.ACCOUNT_NOT_FOUND,
                                "That player does not have an account.");
                    }

                    Account account = maybeAccount.get();
                    BigDecimal newBalance;
                    if (isDeposit) {
                        EconomyResult limitCheck = validator.validateDepositLimit(account.getBalance(), amount);
                        if (limitCheck != null) {
                            connection.rollback();
                            return limitCheck;
                        }
                        newBalance = account.getBalance().add(amount);
                    } else {
                        EconomyResult withdrawCheck = validator.validateWithdrawal(account.getBalance(), amount);
                        if (withdrawCheck != null) {
                            connection.rollback();
                            return withdrawCheck;
                        }
                        newBalance = account.getBalance().subtract(amount);
                    }

                    databaseManager.getPlayerRepository().updateBalance(connection, playerId, newBalance);

                    UUID sender = isDeposit ? null : playerId;
                    UUID receiver = isDeposit ? playerId : null;
                    Transaction transaction = Transaction.newTransaction(sender, receiver, amount, type, metadata);
                    databaseManager.getTransactionRepository().record(connection, transaction);

                    connection.commit();

                    Account updated = account.withBalance(newBalance);
                    accountManager.putCache(updated);
                    security.audit((isDeposit ? "Deposit " : "Withdraw ") + amount + " for " + playerId
                            + " (" + type + ")");

                    return EconomyResult.success(newBalance);
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Balance mutation failed for " + playerId + ": " + e.getMessage());
                return EconomyResult.failure(EconomyResult.Status.DATABASE_ERROR,
                        "A database error occurred while processing this operation.");
            }
        });
    }

    private EconomyResult executeSet(UUID playerId, BigDecimal newBalance, TransactionType type, String metadata) {
        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Account> maybeAccount =
                        databaseManager.getPlayerRepository().findByUuidForUpdate(connection, playerId);
                if (maybeAccount.isEmpty()) {
                    connection.rollback();
                    return EconomyResult.failure(EconomyResult.Status.ACCOUNT_NOT_FOUND,
                            "That player does not have an account.");
                }

                Account account = maybeAccount.get();
                BigDecimal previous = account.getBalance();
                databaseManager.getPlayerRepository().updateBalance(connection, playerId, newBalance);

                BigDecimal delta = newBalance.subtract(previous);
                if (delta.signum() != 0) {
                    UUID sender = delta.signum() < 0 ? playerId : null;
                    UUID receiver = delta.signum() > 0 ? playerId : null;
                    Transaction transaction = Transaction.newTransaction(sender, receiver, delta.abs(), type, metadata);
                    databaseManager.getTransactionRepository().record(connection, transaction);
                }

                connection.commit();

                Account updated = account.withBalance(newBalance);
                accountManager.putCache(updated);
                security.audit("Set balance for " + playerId + " to " + newBalance + " (" + type + ")");

                return EconomyResult.success(newBalance);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Set balance failed for " + playerId + ": " + e.getMessage());
            return EconomyResult.failure(EconomyResult.Status.DATABASE_ERROR,
                    "A database error occurred while processing this operation.");
        }
    }

    /**
     * Runs the given blocking operation asynchronously while holding the
     * per-player lock from {@link EconomySecurity}, guaranteeing only one
     * balance mutation for a given account executes at a time.
     */
    private CompletableFuture<EconomyResult> runLocked(UUID playerId, java.util.function.Supplier<EconomyResult> task) {
        return CompletableFuture.supplyAsync(() -> {
            if (!security.tryLock(playerId)) {
                return EconomyResult.failure(EconomyResult.Status.UNKNOWN_ERROR,
                        "Another economy operation is already in progress for this account. Please try again.");
            }
            try {
                return task.get();
            } catch (Exception e) {
                plugin.getLogger().severe("Unexpected error during balance operation for " + playerId
                        + ": " + e.getMessage());
                throw new CompletionException(e);
            } finally {
                security.releaseLock(playerId);
            }
        }, asyncExecutor);
    }
}
