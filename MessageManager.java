package com.server.economy.config;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads {@code messages.yml} and resolves message keys into formatted, colorized
 * text ready to send to a {@link CommandSender}.
 */
public final class MessageManager {

    private final Plugin plugin;
    private FileConfiguration messages;
    private String prefix;

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream resourceStream = plugin.getResource("messages.yml")) {
            if (resourceStream != null) {
                defaults.load(new InputStreamReader(resourceStream, StandardCharsets.UTF_8));
            } else {
                plugin.getLogger().warning("Bundled messages.yml resource could not be found inside the plugin jar.");
            }
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to load bundled default messages.yml: " + e.getMessage());
        }

        this.messages = YamlConfiguration.loadConfiguration(file);
        this.messages.setDefaults(defaults);
        this.prefix = colorize(messages.getString("prefix", "&8[&6EconomyCore&8] &r"));
    }

    public void reload() {
        load();
    }

    /**
     * Resolves a message key with placeholder substitution and sends it to the target,
     * automatically prepending the configured plugin prefix.
     *
     * @param sender       recipient of the message
     * @param key          dot-path key inside messages.yml
     * @param placeholders alternating placeholder/value pairs, e.g. {@code "player", "Steve"}
     */
    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(prefix + format(key, placeholders));
    }

    /**
     * Resolves and returns a formatted message without sending it.
     */
    public String format(String key, Object... placeholders) {
        String raw = messages.getString(key);
        if (raw == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            raw = "&cMissing message: " + key;
        }
        String result = raw;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            String placeholder = "%" + placeholders[i] + "%";
            String value = String.valueOf(placeholders[i + 1]);
            result = result.replace(placeholder, value);
        }
        return colorize(result);
    }

    public String getPrefix() {
        return prefix;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}

