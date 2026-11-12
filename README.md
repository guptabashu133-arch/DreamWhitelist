# DreamWhitelist

Players only see and use the commands you allow - like CommandWhitelist, for **Paper, Folia and Spigot**
(1.21.x and 26.x, Java 21).

Build: `mvn package` -> `target/DreamWhitelist.jar`

## What it does
- **Hides** every command a player isn't allowed from their tab list - they can't tell it exists.
- **Blocks** them when typed, with one message: `★ TARAASHMC » No such command.`
  (sprite icon, yellow bold, text shadow - all in `config.yml`).
- Commands that don't exist at all get the same message instead of Minecraft's red text (for ops too).
- **Groups**: `default` is for every player; any other group is for players with
  `dreamwhitelist.group.<name>`. A player gets all their groups added together.
- **Aliases** come along: allowing `home` also allows `/homes`.
- **Namespaced** forms (`/minecraft:tp`, `/plugin:cmd`) are hidden unless written out in full.
- **Wildcards**: `dreamlb_*` allows every command starting with `dreamlb_`.
- **Blocked subcommands**: allow `/pv` but block `/pv admin` - blocked when typed and removed from tab-complete,
  on every alias of the command.
- Works on Folia: every check runs on the player's own region thread; command lists are re-sent on the
  player's scheduler.

## Commands (`/dreamwhitelist`, `/dwl`, `/cw`) - `dreamwhitelist.admin`
| Command | |
|---|---|
| `/dwl reload` | Re-read config.yml and re-send everyone's command list |
| `/dwl add <group> <command>` | Allow a command (creates the group) |
| `/dwl remove <group> <command>` | Take it out |
| `/dwl block <group> <command sub>` | Block a subcommand, e.g. `/dwl block default pv admin` |
| `/dwl unblock <group> <command sub>` | Unblock it |
| `/dwl list [group]` | What a group allows |
| `/dwl groups` | Every group and its permission |
| `/dwl test <player> <command>` | Would that player be allowed? |
| `/dwl dump` | Every command on the server, by plugin -> `plugins/DreamWhitelist/commands.txt` |

## Permissions
| Node | Default | |
|---|---|---|
| `dreamwhitelist.admin` | op | `/dwl` |
| `dreamwhitelist.bypass` | op | Sees and uses everything |
| `dreamwhitelist.group.<name>` | nobody | That group's commands (e.g. `dreamwhitelist.group.staff`) |

Give staff their group with LuckPerms:
```
lp group helper permission set dreamwhitelist.group.staff
```

## Default lists
`default` holds every player command of the Dream plugins (hub, warps, tpa, msg, homes, vaults, rtp, shop,
auction, orders, coinshop, coinflip, bounty, kits, claims, teams, koth, duels, leaderboards, tab settings,
report...). `staff` holds punishments (ban, mute, kick, warn, punish...), vanish, invsee, endersee, rollback,
gamemode, fly, tp and claim bypass. Plugins that run commands themselves (menus, screens, NPCs) are not
affected - only what players type or click in chat is checked.
