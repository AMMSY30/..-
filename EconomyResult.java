package com.server.economy.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents the outcome of an economy operation.
 *
 * <p>Instances are immutable. Consumers should inspect {@link #isSuccess()} before
 * relying on {@link #getNewBalance()}, and should always inspect {@link #getStatus()}
 * for a machine-readable reason when an operation fails.</p>
 */
public final class EconomyResult {

    /**
     * Machine readable status describing why an operation succeeded or failed.
     */
    public enum Status {
        SUCCESS,
        ACCOUNT_NOT_FOUND,
        INSUFFICIENT_FUNDS,
        INVALID_AMOUNT,
        AMOUNT_TOO_LARGE,
        SELF_TARGET_NOT_ALLOWED,
        PERMISSION_DENIED,
        DATABASE_ERROR,
        UNKNOWN_ERROR
    }

    private final boolean success;
    private final Status status;
    private final String message;
    private final BigDecimal newBalance;

    private EconomyResult(boolean success, Status status, String message, BigDecimal newBalance) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.newBalance = newBalance;
    }

    public static EconomyResult success(BigDecimal newBalance) {
        return new EconomyResult(true, Status.SUCCESS, "Operation completed successfully.", newBalance);
    }

    public static EconomyResult success(String message, BigDecimal newBalance) {
        return new EconomyResult(true, Status.SUCCESS, message, newBalance);
    }

    public static EconomyResult failure(Status status, String message) {
        if (status == Status.SUCCESS) {
            throw new IllegalArgumentException("Failure result cannot use the SUCCESS status.");
        }
        return new EconomyResult(false, status, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    /**
     * The account balance after the operation. Only present when {@link #isSuccess()} is true.
     */
    public BigDecimal getNewBalance() {
        return newBalance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EconomyResult)) return false;
        EconomyResult that = (EconomyResult) o;
        return success == that.success
                && status == that.status
                && Objects.equals(message, that.message)
                && Objects.equals(newBalance, that.newBalance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, status, message, newBalance);
    }

    @Override
    public String toString() {
        return "EconomyResult{" +
                "success=" + success +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", newBalance=" + newBalance +
                '}';
    }
}
