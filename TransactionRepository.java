package com.server.economy.database;

import com.server.economy.model.Transaction;
import com.server.economy.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Data access layer for {@code economy_transactions}.
 */
public final class TransactionRepository {

    private final ConnectionPool connectionPool;

    public TransactionRepository(ConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    /**
     * Persists a transaction record using its own pooled connection.
     */
    public Transaction record(Transaction transaction) throws SQLException {
        try (Connection connection = connectionPool.getConnection()) {
            return record(connection, transaction);
        }
    }

    /**
     * Persists a transaction record using the caller's connection, so it
     * participates in the caller's transaction boundary.
     */
    public Transaction record(Connection connection, Transaction transaction) throws SQLException {
        String sql = "INSERT INTO economy_transactions (sender_id, receiver_id, amount, type, metadata) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (transaction.getSender() != null) {
                statement.setBytes(1, uuidToBytes(transaction.getSender()));
            } else {
                statement.setNull(1, java.sql.Types.BINARY);
            }
            if (transaction.getReceiver() != null) {
                statement.setBytes(2, uuidToBytes(transaction.getReceiver()));
            } else {
                statement.setNull(2, java.sql.Types.BINARY);
            }
            statement.setBigDecimal(3, transaction.getAmount());
            statement.setString(4, transaction.getType().name());
            statement.setString(5, transaction.getMetadata());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return transaction.withId(keys.getLong(1));
                }
            }
            return transaction;
        }
    }

    public List<Transaction> findHistory(UUID playerId, int limit) throws SQLException {
        String sql = "SELECT id, sender_id, receiver_id, amount, type, metadata, created_at " +
                "FROM economy_transactions " +
                "WHERE sender_id = ? OR receiver_id = ? " +
                "ORDER BY created_at DESC LIMIT ?";
        try (Connection connection = connectionPool.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            byte[] idBytes = uuidToBytes(playerId);
            statement.setBytes(1, idBytes);
            statement.setBytes(2, idBytes);
            statement.setInt(3, Math.max(1, Math.min(limit, 100)));

            List<Transaction> results = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
            return results;
        }
    }

    private Transaction mapRow(ResultSet resultSet) throws SQLException {
        long id = resultSet.getLong("id");
        byte[] senderBytes = resultSet.getBytes("sender_id");
        byte[] receiverBytes = resultSet.getBytes("receiver_id");
        UUID sender = senderBytes != null ? bytesToUuid(senderBytes) : null;
        UUID receiver = receiverBytes != null ? bytesToUuid(receiverBytes) : null;
        java.math.BigDecimal amount = resultSet.getBigDecimal("amount");
        TransactionType type = TransactionType.valueOf(resultSet.getString("type"));
        String metadata = resultSet.getString("metadata");
        Timestamp createdAt = resultSet.getTimestamp("created_at");

        return new Transaction(id, sender, receiver, amount, type,
                createdAt != null ? createdAt.toInstant() : java.time.Instant.now(), metadata);
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
