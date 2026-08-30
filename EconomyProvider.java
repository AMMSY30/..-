package com.server.economy.api;

/**
 * Marker interface implemented by {@link EconomyAPI}. Other plugins can depend
 * on this interface type rather than the concrete implementation, which keeps
 * the public contract stable even if the internal implementation changes.
 */
public interface EconomyProvider {

    /**
     * The currency name shown to players, e.g. "Dollar".
     */
    String getCurrencyName();

    /**
     * The plural currency name shown to players, e.g. "Dollars".
     */
    String getCurrencyNamePlural();

    /**
     * The currency symbol shown in balances, e.g. "$".
     */
    String getCurrencySymbol();

    /**
     * The number of decimal places balances are displayed and stored with.
     */
    int getDecimalPrecision();
}
