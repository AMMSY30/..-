package com.server.economy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Parsing and validation helpers for economy monetary amounts.
 *
 * <p>All economy math is performed using {@link BigDecimal} to avoid the rounding
 * and precision errors inherent to floating point types when handling currency.</p>
 */
public final class NumberUtil {

    private NumberUtil() {
    }

    /**
     * Attempts to parse a user-supplied amount string into a {@link BigDecimal}.
     * Rejects blank input, malformed numbers, NaN/infinite-style input, and
     * anything Bukkit/BigDecimal cannot represent safely.
     */
    public static Optional<BigDecimal> parseAmount(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            BigDecimal value = new BigDecimal(input.trim());
            if (value.scale() > 8) {
                // Reject absurd precision that could be used to abuse rounding.
                return Optional.empty();
            }
            return Optional.of(value);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    public static boolean isNegativeOrZero(BigDecimal amount) {
        return amount == null || amount.signum() <= 0;
    }

    /**
     * Rounds an amount to the configured decimal precision using HALF_UP rounding,
     * which is the conventional rounding mode for currency operations.
     */
    public static BigDecimal round(BigDecimal amount, int decimalPrecision) {
        return amount.setScale(decimalPrecision, RoundingMode.HALF_UP);
    }

    /**
     * Safely adds two amounts, guarding against a result that exceeds the supplied
     * maximum allowed balance.
     */
    public static boolean wouldOverflow(BigDecimal current, BigDecimal delta, BigDecimal maxAllowed) {
        BigDecimal result = current.add(delta);
        return result.compareTo(maxAllowed) > 0;
    }
}
