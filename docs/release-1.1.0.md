# DyDiscord 1.1.0

This release adds a live server status card, logs from DyClaim and DyRTP, PlaceholderAPI placeholders, three more languages, MiniMessage formatting and a developer API. Account links, settings and custom translations are kept.

## Added

- **Server status card.** One Discord message shows Online or Offline, players, the Minecraft version and TPS (on Paper and its forks). DyDiscord edits the same message every `interval-seconds` (30–3600, default 60), posts a new one only when the old one was deleted, and marks it Offline on a normal shutdown. Settings: `status-card.enabled`, `channel-id` and `interval-seconds` in bot.yml.
- **Logs from DyClaim and DyRTP.** Claims created and removed, trust changes, ownership transfers and random teleports are posted as small embeds to `plugin-logs.channel-id`. Each kind has its own switch: `dyclaim.claims`, `dyclaim.trust`, `dyclaim.transfers` and `dyrtp.teleports`. A blank channel turns all of them off. Neither plugin is required.
- **PlaceholderAPI.** `%dydiscord_linked%`, `%dydiscord_discord_name%` and `%dydiscord_bot_connected%`, answered from memory without touching storage. Switch: `integrations.placeholderapi`.
- **German, Spanish and Russian** for Minecraft and Discord messages. `lang: auto` follows the Minecraft client language; `/dyd language de|es|ru` and the bot language accept them too. Command words stay English; Turkish keeps its own.
- **MiniMessage.** On Paper and Purpur, Minecraft messages and the Discord-to-Minecraft chat template may use tags such as `<green>` or `<gradient:#55ff55:#55ffff>` next to `&` codes. Player names and chat are never read as tags. Spigot keeps `&` codes; Adventure is not bundled.
- **Developer API.** `dev.dydiscord.api.DyDiscordAPI` looks up linked accounts by Minecraft UUID or Discord ID. `AccountLinkEvent` and `AccountUnlinkEvent` report changes on the main thread.
- **bStats statistics** (plugin ID 34918), off with `metrics.enabled: false`.

## Changed

- **Modern cards everywhere.** Every bot message except relayed chat now uses Discord's container layout with a coloured accent: private replies, help, `/server`, `/players`, `/ip`, `/links`, `/status`, `dy!` replies, the console relay, the shutdown notice, the status card and the plugin logs. Chat lines stay plain so conversations read naturally.
- After choosing a language, the confirmation names that language in the new language.
- Discord slash commands are unchanged: `/server`, `/players`, `/ip`, `/links`, `/link`, `/account`, `/unlink`, `/help` and `/status` keep working as before.

## Upgrading

Stop the server, back up the existing JAR and the complete `plugins/DyDiscord` folder, then replace the JAR with **one** 1.1.0 package (`DyDiscord-1.1.0.jar`, or `DyDiscord-1.1.0-standalone.jar`). Keep the data folder.

- Account links, settings, custom messages and translations are kept. New settings are added with their defaults: the status card and plugin logs stay off until you choose a channel.
- German, Spanish and Russian preferences are stored beside the existing table, so returning to 1.0.2 shows those players the automatic language instead of failing.
- Existing Discord slash commands are reused; no new bot permissions are needed. The status card and logs need View Channel, Send Messages and Embed Links in their channel.

Full changelog: https://dynastyplugins.com/dydiscord/versions/1.1.0
