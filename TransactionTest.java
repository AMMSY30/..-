package economy;

import com.server.economy.config.ConfigManager;
import com.server.economy.model.Transaction;
import com.server.economy.model.TransactionType;
import com.server.economy.security.TransactionValidator;
import com.server.economy.util.NumberUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * Tests transaction creation, amount parsing, and the generic amount
 * validation rules shared by deposits, withdrawals, and admin operations.
 */
@ExtendWith(MockitoExtension.class)
class TransactionTest {

    @Mock
    private ConfigManager configManager;

    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(configManager.getMaxTransactionAmount()).thenReturn(BigDecimal.valueOf(1_000_000));
        validator = new TransactionValidator(configManager);
    }

    @Test
    void transaction_withPositiveAmount_isCreated() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Transaction transaction = Transaction.newTransaction(sender, receiver, BigDecimal.valueOf(25),
                TransactionType.PLAYER_PAYMENT, null);

        assertEquals(sender, transaction.getSender());
        assertEquals(receiver, transaction.getReceiver());
        assertEquals(BigDecimal.valueOf(25), transaction.getAmount());
        assertEquals(TransactionType.PLAYER_PAYMENT, transaction.getType());
        assertEquals(-1, transaction.getId());
    }

    @Test
    void transaction_withZeroAmount_throws() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.newTransaction(sender, receiver, BigDecimal.ZERO, TransactionType.PLAYER_PAYMENT, null));
    }

    @Test
    void transaction_withNegativeAmount_throws() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.newTransaction(sender, receiver, BigDecimal.valueOf(-5), TransactionType.PLAYER_PAYMENT, null));
    }

    @Test
    void transaction_withNoSenderAndNoReceiver_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                Transaction.newTransaction(null, null, BigDecimal.TEN, TransactionType.ADMIN_GIVE, null));
    }

    @Test
    void transaction_withIdAssigned_returnsNewInstance() {
        Transaction original = Transaction.newTransaction(null, UUID.randomUUID(), BigDecimal.TEN,
                TransactionType.ADMIN_GIVE, "test");
        Transaction withId = original.withId(42L);

        assertEquals(-1, original.getId());
        assertEquals(42L, withId.getId());
    }

    @Test
    void numberUtil_parsesValidAmount() {
        Optional<BigDecimal> parsed = NumberUtil.parseAmount("125.50");
        assertTrue(parsed.isPresent());
        assertEquals(0, parsed.get().compareTo(BigDecimal.valueOf(125.50)));
    }

    @Test
    void numberUtil_rejectsBlankInput() {
        assertTrue(NumberUtil.parseAmount("").isEmpty());
        assertTrue(NumberUtil.parseAmount(null).isEmpty());
    }

    @Test
    void numberUtil_rejectsMalformedInput() {
        assertTrue(NumberUtil.parseAmount("not-a-number").isEmpty());
    }

    @Test
    void numberUtil_rejectsExcessivePrecision() {
        assertTrue(NumberUtil.parseAmount("1.123456789").isEmpty());
    }

    @Test
    void validator_rejectsAmountAboveMaxTransaction() {
        var result = validator.validateAmount(BigDecimal.valueOf(2_000_000));
        assertNotNull(result);
        assertEquals(com.server.economy.model.EconomyResult.Status.AMOUNT_TOO_LARGE, result.getStatus());
    }

    @Test
    void validator_acceptsAmountWithinLimits() {
        var result = validator.validateAmount(BigDecimal.valueOf(500));
        assertNull(result);
    }
}
