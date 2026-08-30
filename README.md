# EconomyCore

MySQL-backed economy core plugin for the WhaleMC network, built for Paper
1.21.11. EconomyCore provides player balances, payments, and a full
administration toolkit, and exposes a public API so other WhaleMC plugins
(shops, jobs, auction houses, and future economy modules) can share a single
source of truth for player money.

## Features

- Player accounts backed entirely by MySQL - no flat-file or SQLite storage.
- Atomic, race-condition-safe balance operations using row-level locking and
  transactional commits.
- `/balance`, `/money`, and `/pay` for players.
- Full `/economy` admin toolkit: give, take, set, reset, history, reload.
- Public API for other plugins (`EconomyAPI`, `AccountAPI`, `TransactionAPI`).
- Optional Vault and PlaceholderAPI integration - both degrade gracefully if
  the dependency isn't installed.
- All database operations run asynchronously; nothing blocks the main server
  thread.
- Configurable currency name, symbol, decimal precision, and transaction
  limits.

## Requirements

- Java 21+
- Paper 1.21.11 (or a compatible fork)
- A MySQL or MariaDB-compatible database

## Installation

1. Download `EconomyCore-<version>.jar` and place it in your server's
   `plugins/` folder.
2. Start the server once to generate the default configuration files under
   `plugins/EconomyCore/`.
3. Set the required MySQL environment variables (see below) and configure
   `database.yml`.
4. Restart the server.

## MySQL Setup

EconomyCore requires a MySQL or MariaDB-compatible database. Create a
database and a dedicated user before starting the plugin:

```sql
CREATE DATABASE whalemc_economy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'economy_user'@'%' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON whalemc_economy.* TO 'economy_user'@'%';
FLUSH PRIVILEGES;
```

EconomyCore creates and manages its own tables on startup - no manual schema
setup is required beyond the database and user above.

Set the connection details as environment variables on the host running the
server (see `docs/CONFIGURATION.md` for the full list), then point
`database.yml` at them using `${VARIABLE_NAME}` placeholders. Real credentials
should never be committed to `database.yml` directly.

## Configuration

See [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) for the full reference
of `config.yml`, `database.yml`, and `messages.yml`.

## Commands

See [`docs/COMMANDS.md`](docs/COMMANDS.md) for the full command reference.

Quick reference:

```
/balance [player]
/money [player]
/pay <player> <amount>
/economy give <player> <amount>
/economy take <player> <amount>
/economy set <player> <amount>
/economy reset <player>
/economy history <player>
/economy reload
```

## Permissions

See [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) for the full permissions
reference.

## API

Other WhaleMC plugins can hook into EconomyCore's public API to read
balances, deposit/withdraw money, transfer between players, and read
transaction history. See [`docs/API.md`](docs/API.md) for usage examples.

## Vault Integration

EconomyCore automatically registers itself as the active Vault `Economy`
provider if Vault is installed, so any Vault-compatible plugin works out of
the box. No configuration is required.

## PlaceholderAPI Integration

If PlaceholderAPI is installed, EconomyCore registers the `economycore`
expansion, exposing placeholders such as `%economycore_balance%`. See
[`docs/API.md`](docs/API.md) for the full placeholder list.

## Building from Source

```bash
git clone <repository-url>
cd EconomyCore
mvn clean package
```

The built plugin JAR will be at `target/EconomyCore-<version>.jar`.

## Testing

```bash
mvn test
```

Tests cover account and transaction model invariants, payment validation
(self-payment prevention, insufficient funds, invalid/negative/zero amounts,
minimum and maximum transaction limits), and amount parsing.

## Future Expansion

EconomyCore's manager/API separation is designed so future WhaleMC systems -
Shop, Jobs, Bank, Auction House, Taxes, Rewards, Quests, player trading,
custom currencies, and economy statistics - can build on top of the existing
API without modifying the core economy logic.

## License

Released under the MIT License. See [`LICENSE`](LICENSE) for details.
