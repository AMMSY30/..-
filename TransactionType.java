package com.server.economy.model;

/**
 * Represents the category of an economy transaction.
 */
public enum TransactionType {

    /** Money moved from one player to another via /pay. */
    PLAYER_PAYMENT,

    /** Money added to an account by an administrator. */
    ADMIN_GIVE,

    /** Money removed from an account by an administrator. */
    ADMIN_TAKE,

    /** An account balance was force-set by an administrator. */
    ADMIN_SET,

    /** An account balance was reset to the configured starting balance. */
    ADMIN_RESET,

    /** Money deposited into an account through the API. */
    API_DEPOSIT,

    /** Money withdrawn from an account through the API. */
    API_WITHDRAW,

    /** Balance granted when an account is first created. */
    STARTING_BALANCE
}
