package com.server.economy.database;

import com.server.economy.config.ConfigManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns the HikariCP connection pool used for all MySQL access.
 *
 * <p>This class is the only place in the plugin that constructs a JDBC data source.
 * All other database classes obtain connections through {@link #getConnection()}.</p>
 */
public final class ConnectionPool implements AutoCloseable {

    private final Plugin plugin;
    private HikariDataSource dataSource;

    public ConnectionPool(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the connection pool from {@code database.yml}.
     *
     * @throws IllegalStateException if the pool cannot establish an initial connection
     */
    public void initialize(ConfigManager configManager) {
        FileConfiguration db = configManager.getDatabaseConfig();

        String host = requireNonBlank(db.getString("database.host"), "database.host");
        int port = db.getInt("database.port", 3306);
        String name = requireNonBlank(db.getString("database.name"), "database.name");
        String username = requireNonBlank(db.getString("database.username"), "database.username");
        String password = db.getString("database.password", "");
        boolean useSsl = db.getBoolean("database.ssl", false);
        boolean verifyServerCertificate = db.getBoolean("database.verify-server-certificate", useSsl);

        int maxPoolSize = db.getInt("database.pool.maximum-pool-size", 10);
        int minIdle = db.getInt("database.pool.minimum-idle", 2);
        long connectionTimeoutMs = db.getLong("database.pool.connection-timeout-ms", 10000);
        long idleTimeoutMs = db.getLong("database.pool.idle-timeout-ms", 600000);
        long maxLifetimeMs = db.getLong("database.pool.max-lifetime-ms", 1800000);

        StringBuilder jdbcUrl = new StringBuilder("jdbc:mysql://")
                .append(host).append(':').append(port).append('/').append(name)
                .append("?useSSL=").append(useSsl)
                .append("&verifyServerCertificate=").append(verifyServerCertificate)
                .append("&useUnicode=true")
                .append("&characterEncoding=utf8")
                .append("&autoReconnect=true")
                .append("&cachePrepStmts=true")
                .append("&prepStmtCacheSize=250")
                .append("&prepStmtCacheSqlLimit=2048");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl.toString());
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setPoolName("EconomyCore-Pool");
        hikariConfig.setMaximumPoolSize(maxPoolSize);
        hikariConfig.setMinimumIdle(minIdle);
        hikariConfig.setConnectionTimeout(connectionTimeoutMs);
        hikariConfig.setIdleTimeout(idleTimeoutMs);
        hikariConfig.setMaxLifetime(maxLifetimeMs);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setInitializationFailTimeout(-1);

        this.dataSource = new HikariDataSource(hikariConfig);

        // Fail fast: verify we can actually reach the database before the plugin finishes enabling.
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                throw new SQLException("Connection validation failed.");
            }
            plugin.getLogger().info("Connected to MySQL database '" + name + "' at " + host + ":" + port);
        } catch (SQLException e) {
            dataSource.close();
            throw new IllegalStateException("Unable to connect to the MySQL database. "
                    + "Check database.yml and confirm the database server is reachable.", e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("The connection pool is not initialized or has been closed.");
        }
        return dataSource.getConnection();
    }

    public boolean isHealthy() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }

    private static String requireNonBlank(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database configuration value: " + key);
        }
        return value;
    }
}
