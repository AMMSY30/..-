package com.server.economy.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads and exposes {@code config.yml} and {@code database.yml}.
 *
 * <p>Values inside {@code database.yml} may reference environment variables using
 * {@code ${VAR_NAME}} syntax. These placeholders are resolved when the file is
 * loaded so real credentials never need to live inside the file itself.</p>
 */
public final class ConfigManager {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

    private final Plugin plugin;
    private FileConfiguration config;
    private FileConfiguration database;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) {@code config.yml} and {@code database.yml} from disk,
     * saving the bundled defaults first if they do not yet exist.
     */
    public void load() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();

        File databaseFile = new File(plugin.getDataFolder(), "database.yml");
        if (!databaseFile.exists()) {
            plugin.saveResource("database.yml", false);
        }
        this.database = loadWithEnvSubstitution(databaseFile, "database.yml");
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        File databaseFile = new File(plugin.getDataFolder(), "database.yml");
        this.database = loadWithEnvSubstitution(databaseFile, "database.yml");
    }

    private FileConfiguration loadWithEnvSubstitution(File file, String resourceName) {
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String resolved = resolveEnvVariables(raw);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(resolved);
            return yaml;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load " + resourceName + ": " + e.getMessage());
            YamlConfiguration fallback = new YamlConfiguration();
            try (InputStream stream = plugin.getResource(resourceName)) {
                if (stream != null) {
                    fallback.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                }
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ignored) {
                // Fall through and return whatever defaults were parsed, if any.
            }
            return fallback;
        }
    }

    private String resolveEnvVariables(String input) {
        Matcher matcher = ENV_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String value = System.getenv(variableName);
            if (value == null) {
                value = System.getProperty(variableName, "");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getDatabaseConfig() {
        return database;
    }

    public BigDecimal getStartingBalance() {
        return BigDecimal.valueOf(config.getDouble("economy.starting-balance", 100.0));
    }

    public BigDecimal getMaxTransactionAmount() {
        return BigDecimal.valueOf(config.getDouble("economy.max-transaction-amount", 1000000.0));
    }

    public BigDecimal getMaxBalance() {
        return BigDecimal.valueOf(config.getDouble("economy.max-balance", 1.0E9));
    }

    public int getDecimalPrecision() {
        return config.getInt("economy.decimal-precision", 2);
    }

    public String getCurrencyName() {
        return config.getString("economy.currency-name", "Dollar");
    }

    public String getCurrencyNamePlural() {
        return config.getString("economy.currency-name-plural", "Dollars");
    }

    public String getCurrencySymbol() {
        return config.getString("economy.currency-symbol", "$");
    }

    public boolean isPaymentsEnabled() {
        return config.getBoolean("economy.payments.enabled", true);
    }

    public BigDecimal getMinPaymentAmount() {
        return BigDecimal.valueOf(config.getDouble("economy.payments.min-amount", 0.01));
    }
}
