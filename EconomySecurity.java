package com.server.economy.security;

import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Additional economy-wide safeguards that sit alongside {@link TransactionValidator}:
 * per-player operation locking (to prevent race conditions from rapid concurrent
 * commands) and lightweight rate limiting/audit logging.
 */
public final class EconomySecurity {

    private final Plugin plugin;

    /** Tracks players who currently have an in-flight economy operation. */
    private final Map<UUID, Boolean> activeOperations = new ConcurrentHashMap<>();

    /** Tracks the timestamp of each player's last payment for basic spam protection. */
    private final Map<UUID, AtomicLong> lastPaymentMillis = new ConcurrentHashMap<>();

    private static final long MIN_MILLIS_BETWEEN_PAYMENTS = 250L;

    public EconomySecurity(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to acquire an operation lock for a player, preventing two economy
     * operations for the same account from running concurrently. Callers must
     * release the lock in a {@code finally} block via {@link #releaseLock(UUID)}.
     *
     * @return {@code true} if the lock was acquired
     */
    public boolean tryLock(UUID playerId) {
        return activeOperations.putIfAbsent(playerId, Boolean.TRUE) == null;
    }

    public void releaseLock(UUID playerId) {
        activeOperations.remove(playerId);
    }

    /**
     * Basic throttle to prevent command spam from being used to attempt race-condition
     * exploits against the database layer.
     */
    public boolean isRateLimited(UUID playerId) {
        long now = System.currentTimeMillis();
        AtomicLong last = lastPaymentMillis.computeIfAbsent(playerId, id -> new AtomicLong(0));
        long previous = last.get();
        if (now - previous < MIN_MILLIS_BETWEEN_PAYMENTS) {
            return true;
        }
        last.set(now);
        return false;
    }

    /**
     * Logs a security-relevant economy event (large transactions, admin overrides,
     * repeated failures) to the server console for auditing.
     */
    public void audit(String message) {
        plugin.getLogger().info("[Economy Audit] " + message);
    }

    public void clearPlayer(UUID playerId) {
        activeOperations.remove(playerId);
        lastPaymentMillis.remove(playerId);
    }
}
