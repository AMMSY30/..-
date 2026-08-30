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
 * Handles player-to-player payments as a single atomic database transaction:
 * both accounts are locked, validated, updated, and the transaction record is
 * written before the commit. If any step fails the entire operation rolls back,
 * so a payment can never leave one account debited without the other credited.
 */
public final class PaymentManager {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final AccountManager accountManager;
    private final TransactionValidator validator;
    private final EconomySecurity security;
    private final Executor asyncExecutor;

    public PaymentManager(Plugin plugin, DatabaseManager databaseManager, ConfigManager configManager,
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

    public CompletableFuture<EconomyResult> pay(UUID senderId, UUID receiverId, BigDecimal amount) {
        if (!configManager.isPaymentsEnabled()) {
            return CompletableFuture.completedFuture(EconomyResult.failure(
                    EconomyResult.Status.PERMISSION_DENIED, "Payments are currently disabled."));
        }
        if (senderId.equals(receiverId)) {
            return CompletableFuture.completedFuture(EconomyResult.failure(
                    EconomyResult.Status.SELF_TARGET_NOT_ALLOWED, "You cannot pay yourself."));
        }

        EconomyResult amountCheck = validator.validateAmount(amount);
        if (amountCheck != null) {
            return CompletableFuture.completedFuture(amountCheck);
        }

        if (security.isRateLimited(senderId)) {
            return CompletableFuture.completedFuture(EconomyResult.failure(
                    EconomyResult.Status.UNKNOWN_ERROR, "You are sending payments too quickly. Please wait a moment."));
        }

        return CompletableFuture.supplyAsync(() -> executePayment(senderId, receiverId, amount), asyncExecutor);
    }

    private EconomyResult executePayment(UUID senderId, UUID receiverId, BigDecimal amount) {
        // Always acquire per-account locks in a consistent order (by UUID comparison)
        // to prevent two simultaneous opposite-direction payments from deadlocking.
        UUID first = senderId.compareTo(receiverId) < 0 ? senderId : receiverId;
        UUID second = senderId.compareTo(receiverId) < 0 ? receiverId : senderId;

        if (!security.tryLock(first)) {
            return EconomyResult.failure(EconomyResult.Status.UNKNOWN_ERROR,
                    "Another economy operation is already in progress for one of these accounts. Please try again.");
        }
        if (!security.tryLock(second)) {
            security.releaseLock(first);
            return EconomyResult.failure(EconomyResult.Status.UNKNOWN_ERROR,
                    "Another economy operation is already in progress for one of these accounts. Please try again.");
        }

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Row locks are acquired in the same fixed UUID order as above.
                Optional<Account> firstAccount =
                        databaseManager.getPlayerRepository().findByUuidForUpdate(connection, first);
                Optional<Account> secondAccount =
                        databaseManager.getPlayerRepository().findByUuidForUpdate(connection, second);

                Optional<Account> senderAccount = first.equals(senderId) ? firstAccount : secondAccount;
                Optional<Account> receiverAccount = first.equals(receiverId) ? firstAccount : secondAccount;

                if (senderAccount.isEmpty()) {
                    connection.rollback();
                    return EconomyResult.failure(EconomyResult.Status.ACCOUNT_NOT_FOUND,
                            "Your account could not be found.");
                }
                if (receiverAccount.isEmpty()) {
                    connection.rollback();
                    return EconomyResult.failure(EconomyResult.Status.ACCOUNT_NOT_FOUND,
                            "That player does not have an account yet.");
                }

                EconomyResult paymentValidation =
                        validator.validatePayment(senderId, receiverId, amount, senderAccount.get());
                if (paymentValidation != null) {
                    connection.rollback();
                    return paymentValidation;
                }

                EconomyResult depositLimit =
                        validator.validateDepositLimit(receiverAccount.get().getBalance(), amount);
                if (depositLimit != null) {
                    connection.rollback();
                    return depositLimit;
                }

                BigDecimal newSenderBalance = senderAccount.get().getBalance().subtract(amount);
                BigDecimal newReceiverBalance = receiverAccount.get().getBalance().add(amount);

                databaseManager.getPlayerRepository().updateBalance(connection, senderId, newSenderBalance);
                databaseManager.getPlayerRepository().updateBalance(connection, receiverId, newReceiverBalance);

                Transaction transaction = Transaction.newTransaction(
                        senderId, receiverId, amount, TransactionType.PLAYER_PAYMENT, null);
                databaseManager.getTransactionRepository().record(connection, transaction);

                connection.commit();

                accountManager.putCache(senderAccount.get().withBalance(newSenderBalance));
                accountManager.putCache(receiverAccount.get().withBalance(newReceiverBalance));

                security.audit("Payment of " + amount + " from " + senderId + " to " + receiverId);

                return EconomyResult.success(newSenderBalance);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Payment failed from " + senderId + " to " + receiverId + ": " + e.getMessage());
            return EconomyResult.failure(EconomyResult.Status.DATABASE_ERROR,
                    "A database error occurred while processing this payment.");
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error during payment: " + e.getMessage());
            return EconomyResult.failure(EconomyResult.Status.UNKNOWN_ERROR,
                    "An unexpected error occurred while processing this payment.");
        } finally {
            security.releaseLock(first);
            security.releaseLock(second);
        }
    }
}
