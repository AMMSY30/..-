package com.server.economy.database;

import com.server.economy.config.ConfigManager;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Top-level facade over the database layer. Owns the {@link ConnectionPool},
 * runs migrations at startup, and exposes the repositories used by the
 * economy managers.
 */
public final class DatabaseManager {

    private final Plugin plugin;
    private final ConnectionPool connectionPool;
    private final PlayerRepository playerRepository;
    private final TransactionRepository transactionRepository;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        this.connectionPool = new ConnectionPool(plugin);
        this.playerRepository = new PlayerRepository(connectionPool);
        this.transactionRepository = new TransactionRepository(connectionPool);
    }

    /**
     * Connects to MySQL and applies schema migrations. This performs blocking
     * network I/O and must be called from an asynchronous context.
     *
     * @throws IllegalStateException if the connection or migration fails
     */
    public void initialize(ConfigManager configManager) {
        connectionPool.initialize(configManager);
        try {
            new DatabaseMigration(connectionPool, plugin).migrate();
        } catch (SQLException e) {
            connectionPool.close();
            throw new IllegalStateException("Database migration failed.", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }

    public boolean isHealthy() {
        return connectionPool.isHealthy();
    }

    public PlayerRepository getPlayerRepository() {
        return playerRepository;
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public void shutdown() {
        connectionPool.close();
    }
}
