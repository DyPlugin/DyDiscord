# DyDiscord 1.4.0

This release adds support tickets, staff lookups, health alerts, Discord-to-Minecraft groups and image links in chat, and makes the plugin lighter on busy servers. Account links, settings and custom translations are kept, and every new option starts off.

## Added

- **Support tickets.** `/ticket <message>` opens a thread in the `tickets.channel-id` channel, and further messages from the player go to the same thread. Staff simply reply in the thread: the player sees the reply in game, or when they next join. `/ticket close` closes the ticket and archives the thread.
  - Turn it on with `tickets.enabled`. Threads are private when the bot can create them (`tickets.private-threads`); `tickets.ping-role-id` mentions a staff role and `tickets.cooldown-seconds` limits new tickets.
  - Needs the Message Content intent to read replies. Permissions: `dydiscord.ticket` (players) and `dydiscord.ticket.notify` (see new tickets in game, no cooldown).
- **/whois for staff.** With `whois.enabled`, the Discord command `/whois <player>` privately shows a player's linked Discord account, when they linked and when they were last seen. It accepts a Minecraft name, a Discord mention or a Discord ID. Only members with Manage Server (or the roles in `whois.allowed-role-ids`) can use it.
- **Health alerts.** With `alerts.channel-id` set, DyDiscord posts when TPS stays below `alerts.low-tps`, when memory use stays above `alerts.memory-percent`, and when the online player count reaches one of `alerts.player-milestones`. Each kind waits `alerts.cooldown-minutes` before it is posted again; `alerts.ping-role-id` mentions a role.
- **Discord roles to LuckPerms groups.** `discord-groups.rules` gives linked players a LuckPerms group while they hold a Discord role. Joins, links and unlinks are checked at once, online players every `discord-groups.recheck-seconds`.
  - Only groups DyDiscord added are ever removed; groups given by hand stay.
  - Staff-like groups (admin, mod, owner and similar) and groups with the `*` permission are never granted unless listed in `discord-groups.staff-groups`. Roles already managed by Minecraft-to-Discord role sync cannot be used.
- **Images in Minecraft chat.** With `chat.attachments`, Discord images, videos, files and stickers show in Minecraft as clickable `[image]`, `[video]`, `[file]` and `[sticker]` labels. Only Discord's own media links are opened; a message with just an image is relayed too.
- All new texts in English, Turkish, German, Spanish and Russian.

## Changed

- **Lighter Discord connection.** Guild message events are no longer requested when no enabled feature reads messages, so a busy Discord server no longer sends every message to the bot.
- **Faster chat relay.** Text cleanup and Discord formatting patterns are compiled once instead of for every message, rank lookups reuse their reflection, and the chat-plugin safety check is only rebuilt when another plugin registers or removes a chat listener.
- **Faster message rendering.** Every rendered message reused to compile its placeholder pattern; it is now compiled once.
- **Faster startup and reload.** The bundled configuration and message files are read from the JAR once instead of on every load, reload and setting change.
- **Quieter background work.** Role reconciliation checks every 30 seconds instead of every second while role sync is off and nothing is left to clean up. The status card no longer reads its message location from the database on every update, and TPS and plugin-log lookups reuse their reflection.
- Duplicate-message tracking for Discord chat no longer scans its whole list on every message.

## Upgrading

Stop the server, back up the JAR and the `plugins/DyDiscord` folder, then replace the JAR with **one** 1.4.0 package (`DyDiscord-1.4.0.jar` or `DyDiscord-1.4.0-standalone.jar`). New settings and messages are added; links, settings and translations are kept. Turning tickets or `/whois` on or off needs a bot restart (`/dyda bot restart`).

Full changelog: https://dynastyplugins.com/dydiscord/versions/1.4.0
