package economy;

import com.server.economy.model.Account;
import com.server.economy.model.EconomyResult;
import com.server.economy.model.Transaction;
import com.server.economy.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the core economy model invariants: accounts cannot be created
 * with a negative balance, transactions cannot be created with a non-positive
 * amount, and {@link EconomyResult} correctly separates success and failure states.
 *
 * <p>These tests intentionally exercise the model layer directly rather than a
 * live database, since {@code EconomyManager} itself requires a MySQL
 * connection; {@link PaymentTest} and {@link TransactionTest} cover the
 * remaining business rules using the same in-memory approach.</p>
 */
class EconomyManagerTest {

    @Test
    void accountCreation_withPositiveBalance_succeeds() {
        Account account = new Account(UUID.randomUUID(), "Steve", BigDecimal.valueOf(100), Instant.now(), Instant.now());
        assertEquals(BigDecimal.valueOf(100), account.getBalance());
    }

    @Test
    void accountCreation_withZeroBalance_succeeds() {
        Account account = new Account(UUID.randomUUID(), "Steve", BigDecimal.ZERO, Instant.now(), Instant.now());
        assertEquals(BigDecimal.ZERO, account.getBalance());
    }

    @Test
    void accountCreation_withNegativeBalance_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new Account(UUID.randomUUID(), "Steve", BigDecimal.valueOf(-10), Instant.now(), Instant.now()));
    }

    @Test
    void accountWithBalance_doesNotMutateOriginal() {
        Account original = new Account(UUID.randomUUID(), "Steve", BigDecimal.valueOf(50), Instant.now(), Instant.now());
        Account updated = original.withBalance(BigDecimal.valueOf(75));

        assertEquals(BigDecimal.valueOf(50), original.getBalance());
        assertEquals(BigDecimal.valueOf(75), updated.getBalance());
    }

    @Test
    void startingBalanceTransaction_isValid() {
        UUID receiver = UUID.randomUUID();
        Transaction transaction = Transaction.newTransaction(null, receiver, BigDecimal.valueOf(100),
                TransactionType.STARTING_BALANCE, "initial account creation");

        assertNull(transaction.getSender());
        assertEquals(receiver, transaction.getReceiver());
        assertEquals(TransactionType.STARTING_BALANCE, transaction.getType());
    }

    @Test
    void economyResult_success_exposesNewBalance() {
        EconomyResult result = EconomyResult.success(BigDecimal.valueOf(150));

        assertTrue(result.isSuccess());
        assertEquals(EconomyResult.Status.SUCCESS, result.getStatus());
        assertEquals(BigDecimal.valueOf(150), result.getNewBalance());
    }

    @Test
    void economyResult_failure_hasNullBalance() {
        EconomyResult result = EconomyResult.failure(EconomyResult.Status.INSUFFICIENT_FUNDS, "Not enough money.");

        assertFalse(result.isSuccess());
        assertEquals(EconomyResult.Status.INSUFFICIENT_FUNDS, result.getStatus());
        assertNull(result.getNewBalance());
    }

    @Test
    void economyResult_failure_cannotUseSuccessStatus() {
        assertThrows(IllegalArgumentException.class, () ->
                EconomyResult.failure(EconomyResult.Status.SUCCESS, "invalid"));
    }
}
