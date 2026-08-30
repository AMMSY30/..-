package com.server.economy.hooks;

import com.server.economy.economy.EconomyManager;
import com.server.economy.util.FormatUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Exposes EconomyCore balances to PlaceholderAPI.
 *
 * <p>Supported placeholders:</p>
 * <ul>
 *   <li>{@code %economycore_balance%} - formatted balance with currency symbol</li>
 *   <li>{@code %economycore_balance_raw%} - plain numeric balance</li>
 *   <li>{@code %economycore_balance_formatted%} - balance with currency name</li>
 *   <li>{@code %economycore_currency_symbol%}</li>
 *   <li>{@code %economycore_currency_name%}</li>
 *   <li>{@code %economycore_currency_name_plural%}</li>
 * </ul>
 */
public final class PlaceholderAPIHook extends PlaceholderExpansion {

    private final Plugin plugin;
    private final EconomyManager economyManager;

    public PlaceholderAPIHook(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    /**
     * Registers this expansion if PlaceholderAPI is installed. Safe to call
     * even when PlaceholderAPI is absent.
     */
    public void registerIfAvailable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("PlaceholderAPI not found - placeholder integration disabled.");
            return;
        }
        boolean success = register();
        if (success) {
            plugin.getLogger().info("PlaceholderAPI integration enabled.");
        } else {
            plugin.getLogger().warning("Failed to register PlaceholderAPI expansion.");
        }
    }

    @Override
    public @NotNull String getIdentifier() {
        return "economycore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "WhaleMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        BigDecimal balance = economyManager.getBalance(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "balance" -> FormatUtil.formatCurrency(balance, economyManager.getConfigManager());
            case "balance_raw" -> FormatUtil.formatPlain(balance, economyManager.getConfigManager().getDecimalPrecision());
            case "balance_formatted" -> FormatUtil.formatCurrencyName(balance, economyManager.getConfigManager());
            case "currency_symbol" -> economyManager.getConfigManager().getCurrencySymbol();
            case "currency_name" -> economyManager.getConfigManager().getCurrencyName();
            case "currency_name_plural" -> economyManager.getConfigManager().getCurrencyNamePlural();
            default -> null;
        };
    }
}
