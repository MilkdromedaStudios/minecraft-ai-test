# Commands

All commands are under `/ai`. Type `/ai help` in-game for the live list.

| Command | Description |
|---------|-------------|
| `/ai help` | Show the in-game command list |
| `/ai menu` · `/ai config` | Open the settings screen |
| `/ai summon [name]` | Spawn an assistant (default: **Ethan**) |
| `/ai dismiss` | Remove your assistant |
| `/ai come` | Call it to you |
| `/ai follow` | Have it follow you |
| `/ai stay` | Hold position and keep watch |
| `/ai stop` | Cancel the current task |
| `/ai resume` · `/ai enable` | Re-enable after the FPS kill-switch tripped |
| `/ai locate` · `/ai where` | Distance, direction and coords |
| `/ai name <name>` | Rename it |
| `/ai skin <name>` | Change skin (built-in or custom PNG) — see [Custom Skins](Custom-Skins) |
| `/ai token <token>` | Set API token |
| `/ai listen on\|off` | Toggle chat listening |
| `/ai active on\|off` | Toggle proactive AI analysis |
| `/ai commands on\|off` | Allow/block command execution — see [Running Commands](Running-Commands) |
| `/ai inventory` · `/ai inv` | Show carried items |
| `/ai settings` | List all current settings |
| `/ai settings <key> <value>` | Change any one setting (tab-complete the key) — see [Settings](Settings) |
| `/aiskins list\|reload` | *(client)* List or hot-reload the skins folder |
| `/ai admin …` | *(ops only)* Admin panel — see below |
| `/ai <task>` | Give a natural-language task |

> **Heads-up (3.2.0+):** changing **server-wide settings** is now operator-only.
> `/ai menu`, `/ai token`, `/ai settings <key> <value>` and
> `/ai listen\|active\|commands on\|off` require admin permission
> (`adminPermissionLevel`, default 2 = ops). Everyday commands above stay open to all
> players, and `/ai settings` (the read-only list) and `/ai help` do too.

## Admin commands (ops only)

The whole `/ai admin` tree is hidden from, and refused to, anyone below the admin
permission level. See **[Admin Menu](Admin-Menu)** for the full guide.

| Command | Description |
|---------|-------------|
| `/ai admin menu` | Open the visual admin panel |
| `/ai admin stats` | Bots, players, FPS, mod status (text) |
| `/ai admin list` | Every bot and where it is (text) |
| `/ai admin killall` | Remove **all** bots on the server |
| `/ai admin maxbots <0-50>` | Cap bots per server (0 = unlimited) |
| `/ai admin disable` · `/ai admin enable` | Turn all bots off / on for everyone |
| `/ai admin reload` | Reload `config.json` from disk |

## Quick intents (no API token)

These common phrases — whether as a command or just typed in chat — are handled
instantly with no LLM call: **come**, **follow**, **stop**, **stay**, **where are you**.

See **[Talking to Your Assistant](Talking-to-Your-Assistant)** for how chat addressing
and active analysis decide what counts as a command.
</content>
