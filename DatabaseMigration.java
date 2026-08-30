package com.server.economy.database;

import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and upgrades the MySQL schema used by EconomyCore.
 *
 * <p>Migrations are idempotent ({@code CREATE TABLE IF NOT EXISTS}) so they can be
 * safely run every time the plugin starts.</p>
 */
public final class DatabaseMigration {

    private static final String CREATE_ACCOUNTS_TABLE = """
            CREATE TABLE IF NOT EXISTS economy_accounts (
                player_id     BINARY(16)      NOT NULL,
                player_name   VARCHAR(16)     NOT NULL,
                balance       DECIMAL(20, 4)  NOT NULL DEFAULT 0.0000,
                created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (player_id),
                CONSTRAINT chk_balance_non_negative CHECK (balance >= 0),
                INDEX idx_player_name (player_name)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
            """;

    private static final String CREATE_TRANSACTIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS economy_transactions (
                id             BIGINT UNSIGNED AUTO_INCREMENT,
                sender_id      BINARY(16)      NULL,
                receiver_id    BINARY(16)      NULL,
                amount         DECIMAL(20, 4)  NOT NULL,
                type           VARCHAR(32)     NOT NULL,
                metadata       VARCHAR(255)    NULL,
                created_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (id),
                CONSTRAINT chk_amount_positive CHECK (amount > 0),
                INDEX idx_sender (sender_id),
                INDEX idx_receiver (receiver_id),
                INDEX idx_created_at (created_at)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
            """;

    private static final String CREATE_IDEMPOTENCY_TABLE = """
            CREATE TABLE IF NOT EXISTS economy_idempotency_keys (
                idempotency_key VARCHAR(64)  NOT NULL,
                created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (idempotency_key)
            ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
            """;

    private final ConnectionPool connectionPool;
    private final Plugin plugin;

    public DatabaseMigration(ConnectionPool connectionPool, Plugin plugin) {
        this.connectionPool = connectionPool;
        this.plugin = plugin;
    }

    /**
     * Runs all schema migrations. This method blocks and must never be called on
     * the main server thread; callers are expected to invoke it asynchronously
     * during plugin startup before accepting economy operations.
     */
    public void migrate() throws SQLException {
        try (Connection connection = connectionPool.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute(CREATE_ACCOUNTS_TABLE);
            statement.execute(CREATE_TRANSACTIONS_TABLE);
            statement.execute(CREATE_IDEMPOTENCY_TABLE);

            plugin.getLogger().info("Database schema verified/created successfully.");
        }
    }
}
