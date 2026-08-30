# EconomyCore Permissions

| Permission | Default | Description |
|---|---|---|
| `economy.balance` | `true` | Allows checking your own and other players' balances. |
| `economy.pay` | `true` | Allows paying other players. |
| `economy.history` | `op` | Allows viewing transaction history. |
| `economy.admin` | `op` | Grants all economy administration permissions below. |
| `economy.admin.give` | `op` | Allows giving money to players. |
| `economy.admin.take` | `op` | Allows taking money from players. |
| `economy.admin.set` | `op` | Allows setting a player's balance. |
| `economy.admin.reset` | `op` | Allows resetting a player's balance. |
| `economy.reload` | `op` | Allows reloading the plugin configuration. |

`economy.admin` is an umbrella permission: granting it also grants
`economy.admin.give`, `economy.admin.take`, `economy.admin.set`,
`economy.admin.reset`, and `economy.reload`. Server owners running a
permissions plugin (LuckPerms, etc.) can grant individual sub-permissions
instead of the umbrella node for finer-grained staff roles.
