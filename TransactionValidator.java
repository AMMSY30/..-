package com.server.economy.security;

import com.server.economy.config.ConfigManager;
import com.server.economy.model.Account;
import com.server.economy.model.EconomyResult;
import com.server.economy.util.NumberUtil;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Central validation gate for every economy operation. No balance mutation
 * should occur anywhere in the plugin without first passing through here.
 */
public final class TransactionValidator {

    private final ConfigManager configManager;

    public TransactionValidator(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Validates a raw amount for a generic economy operation (deposit/withdraw/set).
     * Returns {@code null} when the amount is valid, or a failure {@link EconomyResult}
     * describing the first violation found.
     */
    public EconomyResult validateAmount(BigDecimal amount) {
        if (amount == null) {
            return EconomyResult.failure(EconomyResult.Status.INVALID_AMOUNT, "The amount could not be read.");
        }
        if (NumberUtil.isNegativeOrZero(amount)) {
            return EconomyResult.failure(EconomyResult.Status.INVALID_AMOUNT,
                    "The amount must be a positive number.");
        }
        if (amount.compareTo(configManager.getMaxTransactionAmount()) > 0) {
            return EconomyResult.failure(EconomyResult.Status.AMOUNT_TOO_LARGE,
                    "The amount exceeds the maximum allowed transaction of "
                            + configManager.getMaxTransactionAmount() + ".");
        }
        return null;
    }

    /**
     * Validates a player-to-player payment: both accounts must exist, the amount must
     * be valid, the sender may not pay themselves, and the sender must be able to afford it.
     */
    public EconomyResult validatePayment(UUID sender, UUID receiver, BigDecimal amount, Account senderAccount) {
        if (sender.equals(receiver)) {
            return EconomyResult.failure(EconomyResult.Status.SELF_TARGET_NOT_ALLOWED,
                    "You cannot pay yourself.");
        }

        EconomyResult amountCheck = validateAmount(amount);
        if (amountCheck != null) {
            return amountCheck;
        }

        if (amount.compareTo(configManager.getMinPaymentAmount()) < 0) {
            return EconomyResult.failure(EconomyResult.Status.INVALID_AMOUNT,
                    "The minimum payment amount is " + configManager.getMinPaymentAmount() + ".");
        }

        if (senderAccount == null) {
            return EconomyResult.failure(EconomyResult.Status.ACCOUNT_NOT_FOUND,
                    "Your account could not be found.");
        }

        if (senderAccount.getBalance().compareTo(amount) < 0) {
            return EconomyResult.failure(EconomyResult.Status.INSUFFICIENT_FUNDS,
                    "You do not have enough money to complete this payment.");
        }

        return null;
    }

    /**
     * Validates that depositing {@code amount} onto {@code current} would not exceed
     * the configured maximum balance.
     */
    public EconomyResult validateDepositLimit(BigDecimal current, BigDecimal amount) {
        if (NumberUtil.wouldOverflow(current, amount, configManager.getMaxBalance())) {
            return EconomyResult.failure(EconomyResult.Status.AMOUNT_TOO_LARGE,
                    "This deposit would exceed the maximum allowed account balance.");
        }
        return null;
    }

    public EconomyResult validateWithdrawal(BigDecimal current, BigDecimal amount) {
        EconomyResult amountCheck = validateAmount(amount);
        if (amountCheck != null) {
            return amountCheck;
        }
        if (current.compareTo(amount) < 0) {
            return EconomyResult.failure(EconomyResult.Status.INSUFFICIENT_FUNDS,
                    "Insufficient funds for this withdrawal.");
        }
        return null;
    }
}
