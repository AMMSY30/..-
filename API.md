# EconomyCore API

Other WhaleMC plugins can integrate with EconomyCore in one of two ways: the
native `EconomyAPI`, or the standard Vault `Economy` interface.

## Obtaining the API

```java
RegisteredServiceProvider<EconomyAPI> provider =
        Bukkit.getServicesManager().getRegistration(EconomyAPI.class);

if (provider == null) {
    // EconomyCore is not installed or has not finished starting.
    return;
}

EconomyAPI economy = provider.getProvider();
```

Add EconomyCore as a `softdepend` (or `depend`) in your plugin's `plugin.yml`
so it loads before your plugin.

## Reading balances

```java
BigDecimal balance = economy.getBalance(player.getUniqueId());
boolean canAfford = economy.canAfford(player.getUniqueId(), BigDecimal.valueOf(100));
```

`getBalance` reads from the in-memory cache only and returns
`BigDecimal.ZERO` for a player with no loaded account (e.g. offline and not
recently cached). This is intentional - it's a zero-cost, non-blocking read
suitable for use on the main thread, and is correct for the common case of
looking up an online player.

**If the player might be offline**, use the asynchronous variant instead so
you get their real balance rather than a false zero:

```java
economy.getBalanceAsync(player.getUniqueId())
        .thenAccept(balance -> {
            // balance reflects the database value even if the player
            // has never been cached this session
        });
```

`canAfford` currently checks the cached balance only, matching `getBalance`;
if you need to check affordability for an offline player, resolve their
balance with `getBalanceAsync` first and compare it yourself.

## Depositing and withdrawing

```java
economy.deposit(player.getUniqueId(), BigDecimal.valueOf(50))
        .thenAccept(result -> {
            if (result.isSuccess()) {
                // result.getNewBalance() is the balance after the deposit
            } else {
                // result.getStatus() / result.getMessage() explain why it failed
            }
        });
```

`withdraw` follows the same shape. Both methods are asynchronous and return a
`CompletableFuture<EconomyResult>`; the database work never blocks the
calling thread.

## Transfers between players

```java
economy.transfer(senderUuid, receiverUuid, BigDecimal.valueOf(25))
        .thenAccept(result -> { /* ... */ });
```

Transfers are atomic: either both balances update and a transaction record is
written, or nothing changes at all.

## Accounts

```java
AccountAPI accounts = economy.accounts();

accounts.hasAccount(player.getUniqueId());
accounts.getAccount(player.getUniqueId()).thenAccept(optionalAccount -> { /* ... */ });
accounts.loadOrCreateAccount(player.getUniqueId(), player.getName());
```

## Transaction history

```java
TransactionAPI transactions = economy.transactions();

transactions.getHistory(player.getUniqueId(), 10)
        .thenAccept(history -> { /* most recent first, up to 10 entries */ });
```

## EconomyResult

Every mutating call returns an `EconomyResult` rather than a plain boolean:

| Method | Description |
|---|---|
| `isSuccess()` | Whether the operation completed. |
| `getStatus()` | A `Status` enum value explaining the outcome. |
| `getMessage()` | A human-readable description. |
| `getNewBalance()` | The resulting balance, only populated on success. |

`Status` values: `SUCCESS`, `ACCOUNT_NOT_FOUND`, `INSUFFICIENT_FUNDS`,
`INVALID_AMOUNT`, `AMOUNT_TOO_LARGE`, `SELF_TARGET_NOT_ALLOWED`,
`PERMISSION_DENIED`, `DATABASE_ERROR`, `UNKNOWN_ERROR`.

## Vault integration

If Vault is installed, EconomyCore registers itself as the active `Economy`
provider automatically. Plugins that already use Vault's economy API will
work against EconomyCore without any code changes.

**Safety notes:** Vault's `Economy` interface is synchronous by design - it
has no async/future variant, so any provider (not just this one) must block
the calling thread to return a result. EconomyCore minimizes this as much as
the interface allows:

- Balance/account reads for the common case (an online or recently-online
  player) are served straight from the in-memory cache with **zero blocking**.
- A read for an offline player who isn't cached falls back to a database call
  bounded to a **2 second hard timeout**.
- Writes (`deposit`/`withdraw`) are bounded to a **3 second hard timeout**.
- The actual database work never runs on the calling thread - it's always
  dispatched to EconomyCore's background executor; the calling thread only
  waits on a bounded future.
- If a call happens to block the main server thread, a rate-limited warning
  is logged so admins can identify the responsible plugin.

New integrations should prefer the native `EconomyAPI`, which is fully
asynchronous end to end and never blocks any thread.

## PlaceholderAPI

If PlaceholderAPI is installed, the following placeholders are available:

- `%economycore_balance%`
- `%economycore_balance_raw%`
- `%economycore_balance_formatted%`
- `%economycore_currency_symbol%`
- `%economycore_currency_name%`
- `%economycore_currency_name_plural%`
