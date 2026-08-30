package com.server.economy.hooks;

import com.server.economy.economy.EconomyManager;
import com.server.economy.model.EconomyResult;
import com.server.economy.util.FormatUtil;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges EconomyCore to the Vault {@link Economy} API so any Vault-compatible
 * plugin (shops, jobs, auction houses, etc.) can use EconomyCore transparently.
 *
 * <h2>A note on blocking</h2>
 * <p>Vault's {@code Economy} interface is fundamentally synchronous - every
 * method returns a plain {@code double} or {@code EconomyResponse}, not a
 * future. That is true of every Vault economy provider in existence, not
 * something specific to this bridge: Vault gives no way to hand back a
 * "pending" result and resolve it later. EconomyCore's own backend is
 * asynchronous end to end, so this class has to reconcile the two as safely
 * as the interface allows:</p>
 * <ul>
 *   <li><b>Reads never block for a cached (online/recently-online) player.</b>
 *       {@link #getBalance(OfflinePlayer)}, {@link #hasAccount(OfflinePlayer)},
 *       and {@link #has(OfflinePlayer, double)} check the in-memory account
 *       cache first and return immediately with zero I/O - this covers the
 *       overwhelming majority of real Vault calls, since shops, jobs plugins,
 *       and similar almost always act on the player currently using them.</li>
 *   <li><b>Only a genuine cache miss (an offline player nobody has looked up
 *       this session) triggers a bounded blocking database read</b>, hard-capped
 *       at {@value #READ_TIMEOUT_SECONDS} seconds so a slow or unreachable
 *       database can never hang the caller indefinitely.</li>
 *   <li><b>Writes ({@code deposit}/{@code withdraw}) always block</b>, capped at
 *       {@value #WRITE_TIMEOUT_SECONDS} seconds, since Vault expects a
 *       definitive {@link EconomyResponse} before returning - there is no way
 *       to make a Vault-triggered balance mutation non-blocking while still
 *       honoring the interface contract.</li>
 *   <li><b>The database call itself is never performed on the calling thread</b>
 *       in either case - it always runs on EconomyCore's dedicated async
 *       executor via {@link EconomyManager}, so the calling thread (main or
 *       otherwise) is only ever parked waiting on a bounded future, never
 *       doing blocking socket I/O itself.</li>
 *   <li><b>If a call happens to arrive on the main server thread</b> (the
 *       scenario that risks a visible tick stall or, in the worst case, a
 *       watchdog-triggered "server not responding" freeze), a rate-limited
 *       warning is logged identifying the situation, so admins can find and
 *       report the offending plugin. Plugins that can be updated should call
 *       EconomyCore's native, fully asynchronous {@code EconomyAPI} directly
 *       instead of going through Vault, which never blocks any thread.</li>
 * </ul>
 */
public final class VaultHook implements Economy {

    private static final long WRITE_TIMEOUT_SECONDS = 3;
    private static final long READ_TIMEOUT_SECONDS = 2;
    private static final long MAIN_THREAD_WARNING_INTERVAL_MS = 10_000L;

    private final Plugin plugin;
    private final EconomyManager economyManager;
    private boolean registered = false;
    private final AtomicLong lastMainThreadWarningMillis = new AtomicLong(0L);

    public VaultHook(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    /**
     * Registers this hook with Vault's services manager if Vault is installed.
     * Safe to call even when Vault is absent; simply does nothing in that case.
     */
    public void register() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - Vault integration disabled.");
            return;
        }
        Bukkit.getServicesManager().register(Economy.class, this, plugin, ServicePriority.High);
        registered = true;
        plugin.getLogger().info("Vault integration enabled.");
    }

    public void unregister() {
        if (registered) {
            Bukkit.getServicesManager().unregister(Economy.class, this);
            registered = false;
        }
    }

    @Override
    public boolean isEnabled() {
        return registered;
    }

    @Override
    public String getName() {
        return "EconomyCore";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return economyManager.getConfigManager().getDecimalPrecision();
    }

    @Override
    public String format(double amount) {
        return FormatUtil.formatCurrency(BigDecimal.valueOf(amount), economyManager.getConfigManager());
    }

    @Override
    public String currencyNamePlural() {
        return economyManager.getConfigManager().getCurrencyNamePlural();
    }

    @Override
    public String currencyNameSingular() {
        return economyManager.getConfigManager().getCurrencyName();
    }

    @Override
    public boolean hasAccount(String playerName) {
        UUID uuid = getOfflinePlayerUuid(playerName);
        return uuid != null && hasAccountByUuid(uuid);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return hasAccountByUuid(player.getUniqueId());
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    private boolean hasAccountByUuid(UUID playerId) {
        if (economyManager.hasAccount(playerId)) {
            return true;
        }
        // Cache miss: fall back to a bounded blocking existence check.
        return blockingWait("hasAccount",
                economyManager.getAccount(playerId).thenApply(java.util.Optional::isPresent),
                false, READ_TIMEOUT_SECONDS);
    }

    @Override
    public double getBalance(String playerName) {
        UUID uuid = getOfflinePlayerUuid(playerName);
        return uuid == null ? 0.0 : getBalance(uuid);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    /**
     * Returns the cached balance instantly if available; only blocks (briefly,
     * with a hard timeout) when the player isn't cached at all.
     */
    private double getBalance(UUID playerId) {
        var cached = economyManager.getCachedAccount(playerId);
        if (cached.isPresent()) {
            return cached.get().getBalance().doubleValue();
        }
        return blockingWait("getBalance", economyManager.getBalanceAsync(playerId), BigDecimal.ZERO, READ_TIMEOUT_SECONDS)
                .doubleValue();
    }

    @Override
    public boolean has(String playerName, double amount) {
        UUID uuid = getOfflinePlayerUuid(playerName);
        return uuid != null && getBalance(uuid) >= amount;
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player.getUniqueId()) >= amount;
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        UUID uuid = getOfflinePlayerUuid(playerName);
        if (uuid == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unknown player.");
        }
        return toVaultResponse(awaitWrite("withdraw", economyManager.withdraw(uuid, BigDecimal.valueOf(amount))));
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return toVaultResponse(awaitWrite("withdraw",
                economyManager.withdraw(player.getUniqueId(), BigDecimal.valueOf(amount))));
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        UUID uuid = getOfflinePlayerUuid(playerName);
        if (uuid == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Unknown player.");
        }
        return toVaultResponse(awaitWrite("deposit", economyManager.deposit(uuid, BigDecimal.valueOf(amount))));
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return toVaultResponse(awaitWrite("deposit",
                economyManager.deposit(player.getUniqueId(), BigDecimal.valueOf(amount))));
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = getOfflinePlayerUuid(playerName);
        if (uuid == null) {
            return false;
        }
        blockingWait("createPlayerAccount", economyManager.loadOrCreateAccount(uuid, playerName), null, WRITE_TIMEOUT_SECONDS);
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        blockingWait("createPlayerAccount",
                economyManager.loadOrCreateAccount(player.getUniqueId(), player.getName()), null, WRITE_TIMEOUT_SECONDS);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return createPlayerAccount(player);
    }

    // ----- Bank operations are intentionally unsupported -----

    @Override
    public EconomyResponse createBank(String name, String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banks are not supported.");
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    private EconomyResponse toVaultResponse(EconomyResult result) {
        double balance = result.isSuccess() && result.getNewBalance() != null
                ? result.getNewBalance().doubleValue() : 0.0;
        if (result.isSuccess()) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.SUCCESS, result.getMessage());
        }
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, result.getMessage());
    }

    private EconomyResult awaitWrite(String operation, CompletableFuture<EconomyResult> future) {
        return blockingWait(operation, future,
                EconomyResult.failure(EconomyResult.Status.DATABASE_ERROR, "The operation could not be completed."),
                WRITE_TIMEOUT_SECONDS);
    }

    /**
     * Blocks the calling thread on {@code future}, bounded by {@code timeoutSeconds},
     * returning {@code fallback} if the future fails or the timeout elapses. This is
     * the single choke point every Vault call funnels through, so the safety
     * behavior (hard timeout, main-thread detection, logging) is applied uniformly.
     *
     * <p>If called from the server's main thread, a rate-limited warning is logged
     * (at most once every {@value #MAIN_THREAD_WARNING_INTERVAL_MS}ms) rather than
     * once ever, since a Vault plugin blocking the main thread repeatedly under load
     * is exactly the situation admins need visibility into - but the interval keeps
     * a busy shop plugin from flooding the console.</p>
     */
    private <T> T blockingWait(String operation, CompletableFuture<T> future, T fallback, long timeoutSeconds) {
        boolean onMainThread = Bukkit.isPrimaryThread();
        if (onMainThread) {
            warnIfDueMainThreadBlock(operation, timeoutSeconds);
        }
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException e) {
            plugin.getLogger().severe("Vault economy operation '" + operation + "' failed: " + e.getMessage());
            return fallback;
        } catch (TimeoutException e) {
            plugin.getLogger().severe("Vault economy operation '" + operation + "' timed out after "
                    + timeoutSeconds + "s. The underlying database call may still complete in the background; "
                    + "the account's true balance will reflect it once it does.");
            return fallback;
        }
    }

    private void warnIfDueMainThreadBlock(String operation, long timeoutSeconds) {
        long now = System.currentTimeMillis();
        long last = lastMainThreadWarningMillis.get();
        if (now - last < MAIN_THREAD_WARNING_INTERVAL_MS) {
            return;
        }
        if (lastMainThreadWarningMillis.compareAndSet(last, now)) {
            plugin.getLogger().warning("A Vault plugin called '" + operation + "' on the main server thread and "
                    + "had to wait on a database round-trip (bounded to " + timeoutSeconds + "s). This is a "
                    + "limitation of Vault's synchronous API, not EconomyCore - under a healthy, responsive "
                    + "MySQL connection this resolves in milliseconds, but if you see this warning frequently "
                    + "alongside tick lag, check your database's latency/load first. Plugins that support it "
                    + "should call EconomyCore's native asynchronous EconomyAPI instead, which never blocks "
                    + "any thread.");
        }
    }

    private UUID getOfflinePlayerUuid(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return player.getUniqueId();
    }
}


