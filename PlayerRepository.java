package com.server.economy.database;

import com.server.economy.model.Account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for {@code economy_accounts}.
 *
 * <p>Every method in this class performs blocking JDBC calls and must only be
 * invoked from an asynchronous context, never from the main server thread.</p>
 */
public final class PlayerRepository {

    private final ConnectionPool connectionPool;

    public PlayerRepository(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public Optional<Account> findByUuid(UUID playerId) throws SQLException {
        String sql = "SELECT player_id, player_name, balance, created_at, updated_at " +
                "FROM economy_accounts WHERE player_id = ?";
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, uuidToBytes(playerId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    public Optional<Account> findByName(String playerName) throws SQLException {
        String sql = "SELECT player_id, player_name, balance, created_at, updated_at " +
                "FROM economy_accounts WHERE player_name = ? LIMIT 1";
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Inserts a brand new account. Uses {@code INSERT IGNORE} semantics via a
     * primary-key conflict check so concurrent first-joins cannot create duplicates.
     *
     * @return {@code true} if a new row was inserted, {@code false} if the account already existed
     */
    public boolean createAccount(UUID playerId, String playerName, BigDecimal startingBalance) throws SQLException {
        String sql = "INSERT IGNORE INTO economy_accounts (player_id, player_name, balance) VALUES (?, ?, ?)";
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, uuidToBytes(playerId));
            statement.setString(2, playerName);
            statement.setBigDecimal(3, startingBalance);
            int rows = statement.executeUpdate();
            return rows > 0;
        }
    }

    public void updateBalance(UUID playerId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE economy_accounts SET balance = ? WHERE player_id = ?";
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, newBalance);
            statement.setBytes(2, uuidToBytes(playerId));
            statement.executeUpdate();
        }
    }

    /**
     * Updates the balance using the same {@link Connection} as an in-progress
     * transaction, so this call participates in the caller's commit/rollback.
     */
    public void updateBalance(Connection connection, UUID playerId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE economy_accounts SET balance = ? WHERE player_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, newBalance);
            statement.setBytes(2, uuidToBytes(playerId));
            statement.executeUpdate();
        }
    }

    /**
     * Locks and returns the account row within an existing transaction using
     * {@code SELECT ... FOR UPDATE}, preventing concurrent modification races.
     */
    public Optional<Account> findByUuidForUpdate(Connection connection, UUID playerId) throws SQLException {
        String sql = "SELECT player_id, player_name, balance, created_at, updated_at " +
                "FROM economy_accounts WHERE player_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, uuidToBytes(playerId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
                return Optional.empty();
            }
        }
    }

    public void updatePlayerName(UUID playerId, String newName) throws SQLException {
        String sql = "UPDATE economy_accounts SET player_name = ? WHERE player_id = ?";
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newName);
            statement.setBytes(2, uuidToBytes(playerId));
            statement.executeUpdate();
        }
    }

    public void resetBalance(UUID playerId, BigDecimal startingBalance) throws SQLException {
        updateBalance(playerId, startingBalance);
    }

    private Account mapRow(ResultSet resultSet) throws SQLException {
        UUID playerId = bytesToUuid(resultSet.getBytes("player_id"));
        String playerName = resultSet.getString("player_name");
        BigDecimal balance = resultSet.getBigDecimal("balance");
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        Timestamp updatedAt = resultSet.getTimestamp("updated_at");
        return new Account(
                playerId,
                playerName,
                balance,
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                updatedAt != null ? updatedAt.toInstant() : Instant.now()
        );
    }

    private static byte[] uuidToBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSignificant = buffer.getLong();
        long leastSignificant = buffer.getLong();
        return new UUID(mostSignificant, leastSignificant);
    }
}
