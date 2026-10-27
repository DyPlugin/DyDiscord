# DyDiscord 1.2.0

This release adds staff tools: cheat checks with a Discord voice call, player reports, required account linking, and nickname and ban sync. The same JAR now runs on Folia, and DyHomes events can be logged. Account links, settings and custom translations are kept.

## Added

- **Cheat checks.** `/cheatcheck <player>` (aliases `/hile-kontrol`, `/hilekontrol`) shows a title on the player's screen, a chat warning and a sound, repeated every `cheat-check.reminder-seconds` until the check ends. A linked player is asked on Discord to join the voice channel in `cheat-check.voice-channel-id`, by DM, by a mention in `cheat-check.channel-id`, or both (`cheat-check.notify`). A closed DM falls back to the channel. An unlinked player sees the invitation in game.
  - With `cheat-check.freeze` (on by default) the player stays in place, ender pearls and chorus fruit do nothing, and only `allowed-commands` work.
  - Leaving during a check runs `quit-commands` from the console, for example `ban {player} Left during a cheat check`. Server-control commands such as `op` and `stop` are refused.
  - `/cheatcheck <player> end` ends a check, and `/cheatcheck list` shows the checks in progress.
  - Staff with `dydiscord.cheatcheck.notify` see every step in game. A Discord log in `cheat-check.log-channel-id` (or the plugin-logs channel) records start, end and abandoned checks.
- **Player reports.** `/report <player> <reason>` (alias `/rapor`) tells online staff and posts the report to `reports.channel-id` with the reported player's position, mentioning `reports.ping-role-id` if set. Players wait `reports.cooldown-seconds` between reports; staff do not.
- **Required linking.** With `linking.require-to-join`, a player without a linked account is kept out, and the kick screen shows a linking code and your invitation. They enter the code with Discord `/link` or the panel button, then join. `linking.require-bypass-ops` lets operators in. While the bot is offline, unlinked players get a clear "try again shortly" message.
- **Nickname sync.** `moderation.nickname-sync` sets a linked member's Discord nickname from `moderation.nickname-format` (default `{name}`) on linking and on every join. It needs Manage Nicknames; the server owner and members above the bot are skipped.
- **Ban sync.** `moderation.ban-sync` is `off`, `minecraft-to-discord`, `discord-to-minecraft` or `both`.
  - A Minecraft ban of a linked player bans the Discord account; this needs Ban Members.
  - A Discord ban bans and kicks the Minecraft account, and a Discord unban lifts it. This direction uses the Guild Moderation gateway event, so turning it on or off reconnects the bot.
- **Folia.** One JAR runs on Bukkit, Spigot, Paper, Purpur and Folia. Folia servers see Folia builds in update notices.
- **DyHomes logs.** `plugin-logs.dyhomes.warps` (on), `public-homes` (on) and `teleports` (off, because it is frequent).

## Permissions

- `dydiscord.cheatcheck`, `dydiscord.cheatcheck.notify` and `dydiscord.report.notify` come with `dydiscord.admin`.
- `dydiscord.report` comes with `dydiscord.player`.
- `dydiscord.cheatcheck.exempt` is given to nobody by default.

## Upgrading

Stop the server, back up the existing JAR and the complete `plugins/DyDiscord` folder, then replace the JAR with **one** 1.2.0 package (`DyDiscord-1.2.0.jar`, or `DyDiscord-1.2.0-standalone.jar`). Keep the data folder.

- Links, settings, custom messages and translations are kept. New settings arrive with their defaults: required linking, nickname sync and ban sync are off, and cheat checks and reports work in game without any Discord channel.
- New messages are added in each language file without touching your own texts.
- The `/report` command name may already belong to another plugin; `/rapor`, `/dyreport` and `/dydiscord:report` always reach DyDiscord.

Full changelog: https://dynastyplugins.com/dydiscord/versions/1.2.0
