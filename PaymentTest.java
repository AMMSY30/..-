package economy;

import com.server.economy.config.ConfigManager;
import com.server.economy.model.Account;
import com.server.economy.model.EconomyResult;
import com.server.economy.security.TransactionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

/**
 * Tests the validation rules {@link TransactionValidator} applies to
 * player-to-player payments: self-payment prevention, invalid/negative/zero
 * amounts, minimum payment amounts, insufficient funds, and the maximum
 * transaction limit.
 */
@ExtendWith(MockitoExtension.class)
class PaymentTest {

    @Mock
    private ConfigManager configManager;

    private TransactionValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(configManager.getMaxTransactionAmount()).thenReturn(BigDecimal.valueOf(1_000_000));
        lenient().when(configManager.getMaxBalance()).thenReturn(BigDecimal.valueOf(1_000_000_000));
        lenient().when(configManager.getMinPaymentAmount()).thenReturn(BigDecimal.valueOf(0.01));
        validator = new TransactionValidator(configManager);
    }

    private Account accountWithBalance(BigDecimal balance) {
        return new Account(UUID.randomUUID(), "Payer", balance, Instant.now(), Instant.now());
    }

    @Test
    void payment_withSufficientFunds_isValid() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Account senderAccount = new Account(sender, "Sender", BigDecimal.valueOf(100), Instant.now(), Instant.now());

        EconomyResult result = validator.validatePayment(sender, receiver, BigDecimal.valueOf(50), senderAccount);

        assertNull(result, "A valid payment should not produce a failure result.");
    }

    @Test
    void payment_toSelf_isRejected() {
        UUID player = UUID.randomUUID();
        Account account = accountWithBalance(BigDecimal.valueOf(100));

        EconomyResult result = validator.validatePayment(player, player, BigDecimal.TEN, account);

        assertNotNull(result);
        assertEquals(EconomyResult.Status.SELF_TARGET_NOT_ALLOWED, result.getStatus());
    }

    @Test
    void payment_withInsufficientFunds_isRejected() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Account senderAccount = accountWithBalance(BigDecimal.valueOf(10));

        EconomyResult result = validator.validatePayment(sender, receiver, BigDecimal.valueOf(50), senderAccount);

        assertNotNull(result);
        assertEquals(EconomyResult.Status.INSUFFICIENT_FUNDS, result.getStatus());
    }

    @Test
    void payment_withNegativeAmount_isRejected() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Account senderAccount = accountWithBalance(BigDecimal.valueOf(100));

        EconomyResult result = validator.validatePayment(sender, receiver, BigDecimal.valueOf(-10), senderAccount);

        assertNotNull(result);
        assertEquals(EconomyResult.Status.INVALID_AMOUNT, result.getStatus());
    }

    @Test
    void payment_withZeroAmount_isRejected() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Account senderAccount = accountWithBalance(BigDecimal.valueOf(100));

        EconomyResult result = validator.validatePayment(sender, receiver, BigDecimal.ZERO, senderAccount);

        assertNotNull(result);
        assertEquals(EconomyResult.Status.INVALID_AMOUNT, result.getStatus());
    }

    @Test
    void payment_belowMinimumAmount_isRejected() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Account senderAccount = accountWithBalance(BigDecimal.valueOf(100));

        EconomyResult result = validator.validatePayment(sender, receiver, BigDecimal.valueOf(0.001), senderAccount);

        assertNotNull(result);
        assertEquals(EconomyResult.Status.INVALID_AMOUNT, result.getStatus());
    }

    @Test
    void payment_exceedingMaxTransactionAmount_isRejected() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Account senderAccount = accountWithBalance(BigDecimal.valueOf(10_000_000));

        EconomyResult result = validator.validatePayment(sender, receiver, BigDecimal.valueOf(2_000_000), senderAccount);

        assertNotNull(result);
        assertEquals(EconomyResult.Status.AMOUNT_TOO_LARGE, result.getStatus());
    }

    @Test
    void payment_withMissingSenderAccount_isRejected() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        EconomyResult result = validator.validatePayment(sender, receiver, BigDecimal.TEN, null);

        assertNotNull(result);
        assertEquals(EconomyResult.Status.ACCOUNT_NOT_FOUND, result.getStatus());
    }

    @Test
    void depositLimit_exceedingMaxBalance_isRejected() {
        EconomyResult result = validator.validateDepositLimit(BigDecimal.valueOf(999_999_990), BigDecimal.valueOf(100));

        assertNotNull(result);
        assertEquals(EconomyResult.Status.AMOUNT_TOO_LARGE, result.getStatus());
    }

    @Test
    void depositLimit_withinMaxBalance_isValid() {
        EconomyResult result = validator.validateDepositLimit(BigDecimal.valueOf(100), BigDecimal.valueOf(50));

        assertNull(result);
    }
}
