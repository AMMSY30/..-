package com.server.economy.util;

import com.server.economy.config.ConfigManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats monetary amounts for display in chat, commands, and PlaceholderAPI output.
 */
public final class FormatUtil {

    private FormatUtil() {
    }

    /**
     * Formats an amount with thousands separators and the currency symbol, e.g. {@code $1,250.00}.
     */
    public static String formatCurrency(BigDecimal amount, ConfigManager configManager) {
        int precision = configManager.getDecimalPrecision();
        BigDecimal rounded = amount.setScale(precision, RoundingMode.HALF_UP);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        StringBuilder pattern = new StringBuilder("#,##0");
        if (precision > 0) {
            pattern.append('.');
            pattern.append("0".repeat(precision));
        }
        DecimalFormat format = new DecimalFormat(pattern.toString(), symbols);
        return configManager.getCurrencySymbol() + format.format(rounded);
    }

    /**
     * Formats an amount with the singular or plural currency name, e.g. {@code 1 Dollar} / {@code 2 Dollars}.
     */
    public static String formatCurrencyName(BigDecimal amount, ConfigManager configManager) {
        int precision = configManager.getDecimalPrecision();
        BigDecimal rounded = amount.setScale(precision, RoundingMode.HALF_UP);
        String name = rounded.compareTo(BigDecimal.ONE) == 0
                ? configManager.getCurrencyName()
                : configManager.getCurrencyNamePlural();
        return rounded.toPlainString() + " " + name;
    }

    /**
     * Formats a raw amount with a fixed number of decimal places and no symbol, used
     * for PlaceholderAPI's raw balance placeholder.
     */
    public static String formatPlain(BigDecimal amount, int precision) {
        return amount.setScale(precision, RoundingMode.HALF_UP).toPlainString();
    }
}
