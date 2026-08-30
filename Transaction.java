package com.server.economy.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of a completed economy transaction.
 */
public final class Transaction {

    private final long id;
    private final UUID sender;
    private final UUID receiver;
    private final BigDecimal amount;
    private final TransactionType type;
    private final Instant timestamp;
    private final String metadata;

    /**
     * @param id        database-assigned identifier, or {@code -1} for a transaction not yet persisted
     * @param sender    the player who initiated the transaction, or {@code null} for system-originated transactions
     * @param receiver  the player who received the transaction, or {@code null} when money leaves the economy
     * @param amount    the transaction amount, always positive
     * @param type      the transaction category
     * @param timestamp when the transaction occurred
     * @param metadata  optional free-form context (e.g. admin executor name), may be {@code null}
     */
    public Transaction(long id, UUID sender, UUID receiver, BigDecimal amount, TransactionType type,
                        Instant timestamp, String metadata) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.metadata = metadata;

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive: " + amount);
        }
        if (sender == null && receiver == null) {
            throw new IllegalArgumentException("A transaction must have at least a sender or a receiver.");
        }
    }

    public static Transaction newTransaction(UUID sender, UUID receiver, BigDecimal amount,
                                              TransactionType type, String metadata) {
        return new Transaction(-1, sender, receiver, amount, type, Instant.now(), metadata);
    }

    public long getId() {
        return id;
    }

    public UUID getSender() {
        return sender;
    }

    public UUID getReceiver() {
        return receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMetadata() {
        return metadata;
    }

    /**
     * Returns a copy of this transaction with a database-assigned id.
     */
    public Transaction withId(long newId) {
        return new Transaction(newId, sender, receiver, amount, type, timestamp, metadata);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction)) return false;
        Transaction that = (Transaction) o;
        return id == that.id
                && Objects.equals(sender, that.sender)
                && Objects.equals(receiver, that.receiver)
                && amount.compareTo(that.amount) == 0
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sender, receiver, amount, type);
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", sender=" + sender +
                ", receiver=" + receiver +
                ", amount=" + amount +
                ", type=" + type +
                ", timestamp=" + timestamp +
                '}';
    }
}
