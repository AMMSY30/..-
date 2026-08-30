package com.server.economy.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a player's economy account.
 *
 * <p>{@code Account} instances never mutate their own balance. Balance changes are
 * performed by the economy layer, which persists the change and returns a new,
 * updated {@code Account} snapshot via {@link #withBalance(BigDecimal)}.</p>
 */
public final class Account {

    private final UUID playerId;
    private final String playerName;
    private final BigDecimal balance;
    private final Instant createdAt;
    private final Instant updatedAt;

    public Account(UUID playerId, String playerName, BigDecimal balance, Instant createdAt, Instant updatedAt) {
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        this.playerName = Objects.requireNonNull(playerName, "playerName must not be null");
        this.balance = Objects.requireNonNull(balance, "balance must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (balance.signum() < 0) {
            throw new IllegalArgumentException("Account balance cannot be negative: " + balance);
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Returns a new {@code Account} with an updated balance and refreshed
     * {@code updatedAt} timestamp. This instance is left unmodified.
     */
    public Account withBalance(BigDecimal newBalance) {
        return new Account(playerId, playerName, newBalance, createdAt, Instant.now());
    }

    /**
     * Returns a new {@code Account} with an updated cached player name.
     */
    public Account withPlayerName(String newName) {
        return new Account(playerId, newName, balance, createdAt, updatedAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        Account account = (Account) o;
        return playerId.equals(account.playerId);
    }

    @Override
    public int hashCode() {
        return playerId.hashCode();
    }

    @Override
    public String toString() {
        return "Account{" +
                "playerId=" + playerId +
                ", playerName='" + playerName + '\'' +
                ", balance=" + balance +
                '}';
    }
}
