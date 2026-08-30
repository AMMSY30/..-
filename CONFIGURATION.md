# EconomyCore Configuration

EconomyCore ships three configuration files under `plugins/EconomyCore/`:
`config.yml`, `messages.yml`, and `database.yml`.

## config.yml

```yaml
economy:
  starting-balance: 100.0
  max-transaction-amount: 1000000.0
  max-balance: 1000000000.0
  decimal-precision: 2
  currency-name: "Dollar"
  currency-name-plural: "Dollars"
  currency-symbol: "$"

  payments:
    enabled: true
    min-amount: 0.01

logging:
  log-transactions: true
```

| Key | Description |
|---|---|
| `economy.starting-balance` | Balance granted to a new account. |
| `economy.max-transaction-amount` | Upper bound for a single payment, deposit, withdrawal, or admin give/take. |
| `economy.max-balance` | Upper bound any single account balance may reach. |
| `economy.decimal-precision` | Decimal places used for storage, rounding, and display. |
| `economy.currency-name` / `currency-name-plural` | Singular/plural currency name shown in messages. |
| `economy.currency-symbol` | Symbol prepended to formatted balances. |
| `economy.payments.enabled` | Master switch for `/pay` and the API `transfer()` method. |
| `economy.payments.min-amount` | Smallest amount a single payment may move. |
| `logging.log-transactions` | Whether successful transactions are written to the console audit log. |

Changes to `config.yml` take effect after `/economy reload` or a server restart.

## database.yml

```yaml
database:
  host: ${MYSQL_HOST}
  port: ${MYSQL_PORT}
  name: ${MYSQL_DATABASE}
  username: ${MYSQL_USERNAME}
  password: ${MYSQL_PASSWORD}

  ssl: false
  verify-server-certificate: false

  pool:
    maximum-pool-size: 10
    minimum-idle: 2
    connection-timeout-ms: 10000
    idle-timeout-ms: 600000
    max-lifetime-ms: 1800000
```

Values wrapped in `${...}` are resolved from environment variables at
startup, so real credentials never need to be written into the file. Set the
corresponding environment variables on the machine or container running the
server:

```bash
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=whalemc_economy
export MYSQL_USERNAME=economy_user
export MYSQL_PASSWORD=change-me
```

If a variable is not set, it resolves to an empty string - the plugin will
fail to connect and disable itself with a clear error in the console rather
than starting with a broken configuration.

`database.yml` is **not** hot-reloaded by `/economy reload`; changing it
requires a full server restart, since swapping the active connection pool at
runtime risks leaving in-flight transactions in an inconsistent state.

### Connection pool

| Key | Description |
|---|---|
| `maximum-pool-size` | Maximum concurrent MySQL connections. |
| `minimum-idle` | Minimum idle connections kept warm in the pool. |
| `connection-timeout-ms` | How long to wait for a connection before failing. |
| `idle-timeout-ms` | How long an idle connection may sit before being closed. |
| `max-lifetime-ms` | Maximum lifetime of a pooled connection before it is recycled. |

## messages.yml

All player-facing text lives in `messages.yml` and supports `&`-style color
codes plus `%placeholder%` substitution. Edit any value and run
`/economy reload` to apply changes without restarting the server.
