# EconomyCore Commands

## Player commands

| Command | Description |
|---|---|
| `/balance [player]` | View your balance, or another player's balance. Alias: `/bal`. |
| `/money [player]` | Alias of `/balance`. |
| `/pay <player> <amount>` | Send money to another player. |

## Admin commands

All admin commands are subcommands of `/economy` (alias `/eco`).

| Command | Description |
|---|---|
| `/economy give <player> <amount>` | Add money to a player's account. |
| `/economy take <player> <amount>` | Remove money from a player's account. |
| `/economy set <player> <amount>` | Set a player's balance to an exact value. |
| `/economy reset <player>` | Reset a player's balance to the configured starting balance. |
| `/economy history <player>` | View a player's most recent transactions. |
| `/economy reload` | Reload `config.yml` and `messages.yml`. |

Running `/economy` with no arguments, or with an unrecognized subcommand,
shows a help listing of every subcommand the sender has permission to use.

## Notes

- Amounts accept decimal values (e.g. `12.50`) and are validated against the
  configured minimum, maximum, and precision limits.
- Targets can be specified by name; tab completion suggests currently online
  players.
- Admin commands can be run from the console as well as in-game, with the
  exception of commands that require a player context (`/balance` and
  `/money` with no arguments, `/pay`).
