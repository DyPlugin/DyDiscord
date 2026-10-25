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

- **New license.** DyDiscord 1.1.0 and later use the DyDiscord Source-Available License 1.0, the same terms as DyClaim: free to use and modify on any server, no paid distribution, and distributed versions must share their source. Earlier versions keep the earlier GPL v3 notice with additional terms.
- **Modern cards everywhere.** Every bot message except relayed chat now uses Discord's container layout with a coloured accent: private replies, help, `/server`, `/players`, `/ip`, `/links`, `/status`, `dy!` replies, the console relay, the shutdown notice, the status card and the plugin logs. Chat lines stay plain so conversations read naturally.
- After choosing a language, the confirmation names that language in the new language.
- Discord slash commands are unchanged: `/server`, `/players`, `/ip`, `/links`, `/link`, `/account`, `/unlink`, `/help` and `/status` keep working as before.

## Upgrading

Stop the server, back up the existing JAR and the complete `plugins/DyDiscord` folder, then replace the JAR with **one** 1.1.0 package (`DyDiscord-1.1.0.jar`, or `DyDiscord-1.1.0-standalone.jar`). Keep the data folder.

- Account links, settings, custom messages and translations are kept. New settings are added with their defaults: the status card and plugin logs stay off until you choose a channel.
- German, Spanish and Russian preferences are stored beside the existing table, so returning to 1.0.2 shows those players the automatic language instead of failing.
- Existing Discord slash commands are reused; no new bot permissions are needed. The status card and logs need View Channel, Send Messages and Embed Links in their channel.

Full changelog: https://dynastyplugins.com/dydiscord/versions/1.1.0

---

# DyDiscord 1.0.2

- Added `/dyd`; hide `/dyda` suggestions unless an administration leaf is accessible.
- Removed in-game token entry and its permission. Configure tokens through bot.yml or the environment.
- Enable fresh chat directions/events; keep console relay off and its authorized-command subsetting on. Preserve explicit owner settings and treat empty quoted list examples as no-ops.
- Use V2 giveaway cards and private replies, with expiring actor-bound confirmation before leaving; repair active cards in place.
- Localize safe console diagnostics, retain YAML error location without its source line, validate giveaway button labels before applying configuration, and restore the compact » chat separator.

- Allow bounded Discord rate-limit waits during initial slash registration without treating the bot as permanently unready after the short ordinary-request timeout.
- Wake the chat sender on demand instead of waiting for the next idle polling tick. Preserve minimum send spacing, channel slowmode, queue limits and five-second expiry, including long slowmode periods.
- Preserve literal punctuation in Discord account/role names and reply authors when relaying to Minecraft; flatten message Markdown separately.
- Explain blocked chat forwarding in administrator diagnostics and throttled EN/TR warnings without weakening private-chat protection.
- Keep processing subsequent Paper chat events after transient reflected event-method failures; incompatible APIs remain blocked.
- Locally verified with 699 automated tests and isolated relay measurements; no publication/production deployment. See [release evidence](docs/release-1.0.2-verification.md) and [measured comparison](docs/performance/2026-10-18/README.md). No overall speed-superiority claim.

---

# DyDiscord 1.0.1

## Improved

- Six-character copyable linking codes with hover hints and a separate clickable invitation; automatic reusable permanent invitations when none is configured.
- Natural English/Turkish account messages, readable private link dates, optional managed-role names, and a contained unlink confirmation.
- Bot/game language inheritance, numbered administrator help, and short actor-bound in-game confirmation codes.
- Consecutive chat bursts retain order and duplicate protection. Console output has a dedicated bounded worker and overlapping fresh permission reads.

- Automatic first panel delivery after channel setup, connection and reload; the existing panel is reused.
- New setups wait for credentials and a configured console channel with the bot and log relay enabled; remote console command execution remains an explicit opt-in. Existing disabled settings are preserved.
- Paginated administrator help, permission-filtered navigation and English/Turkish command paths.
- Organized Discord help embeds shared by slash and message commands; other normal message-command replies also use embeds.
- Exact primary Discord role RGB in Minecraft, including dark colors; gradient/holographic roles use their first color.

## Simplified command surface

- Added `/dyda` with the same administration leaf checks, prefix editing, guild setup and a no-argument, player-only token entry session. Local token-file editing remains preferred because client history and third-party logging cannot be guaranteed.
- Player `chat on|off` now controls incoming Discord visibility only. Administrators choose explicit Discord → Minecraft, Minecraft → Discord or both directions and a chat channel.
- Removed announcement/startup/scheduled notice features and advanced editing/report/setup/validation command families. Role mappings, console authorization and reward settings remain editable in YAML; useful role operations and giveaways remain.
- Console channels may be public; output visibility follows channel access while remote commands retain explicit authorization and fresh checks. The normal short yellow shutdown embed remains.
- Numbered help replaces topic navigation; player help omits itself, header prefix appears once and page footer stays unprefixed. YAML descriptions follow their setting, and owner comments remain.
- Readable localized account dates and both account names remain private; configuration warnings reach authorized online administrators through `dydiscord.admin.alerts`.

## Fixed

- Unrelated inactive channels blocking configuration changes and channel setup being rejected before bot connection.
- Plain console output incorrectly requiring panel-specific history/embed permissions.
- Generic setup failures hiding channel/privacy/access repair instructions, including wrapped configuration validation errors.
- Failed initialization continuing to claim it was initializing; YAML failures now identify the affected file without exposing its values.
- Missing restart guidance for failed sessions and stale callbacks surviving a denied-intent fallback reconnect.
- Empty console channel generating repeated permission checks. Intent rejection now explains the affected features and Developer Portal settings.

## Upgrade

Replace the JAR with the server stopped; keep the entire DyDiscord data directory. No database schema change or account relinking is required. Choose either `DyDiscord-1.0.1.jar` or `DyDiscord-1.0.1-standalone.jar`, never both. See [the latest simplification report](docs/simplify-1.0.1-verification.md) for current evidence and limits; prior release/polish reports apply to their own artifacts.

---

# DyDiscord 1.0.0

## Added

- Discord player lists, configured Java/Bedrock addresses, useful links and custom server information.
- Persistent personal chat preferences, opt-in name notifications and safe reply context.
- Private linking welcomes and first-link reward commands with durable anti-replay receipts.
- Shared notice templates, preview, maintenance/restart notices, paced scheduled announcements and startup notifications.
- Guided setup, read-only configuration/translation checks, safe support reports and feature health details.
- Persistent button giveaways with optional linked-account/role conditions, automatic draws and result recovery.
- Channel restrictions, separate administration permissions and update notification preferences.

## Improved

- Smaller default download with server-managed dependency caching; standalone remains available for offline installation.
- Readable message truncation, slowmode pacing and independent temporary chat pauses.
- Existing account links, settings, translations and panel state remain compatible.

## Upgrade

Use `DyDiscord-1.0.0.jar` or `DyDiscord-1.0.0-standalone.jar`, never both.
Back up the entire stopped-server DyDiscord folder before replacing the JAR.
Optional notices, rewards and giveaways are disabled until configured.
This release uses the 1.0.0 name without a leading v or BETA. Previous beta tags
remain historical; see the upgrade guide for the version-line transition.

---

# DyDiscord v1.0.1-BETA

**Role integration and community rewards**

By Claww / DyPlugin

This Beta adds Minecraft-to-Discord role synchronization, configurable boost
rewards and a choice between panel and command-only account access. Existing
account links are preserved when upgrading from v1.0.0-BETA.

## Added

- Optional Minecraft → Discord role synchronization for linked accounts.
- Linked-account roles that work without LuckPerms, plus LuckPerms group-to-role
  mappings for direct, inherited and temporary group membership.
- Multiple roles per group, fixed contexts and configurable all/highest selection.
- Offline reconciliation, reconnect recovery and persistent cleanup for unlinking
  or removed role rules. Unrelated Discord roles remain untouched.
- Permission-filtered role administration, change previews, gradual bulk
  synchronization and separately confirmed cleanup.
- Optional boost rewards through configured rank, economy or site-credit console
  commands, with one claim per continuous boost period and a cooldown.
- Optional commands to remove a recorded boost reward when boosting ends or the
  account is unlinked. Uncertain command attempts are not automatically replayed.
- Panel enable/disable controls with private slash-command account access.
  Disabling retains the panel and accounts; enabling reuses the recorded panel.
- Discord `/server` and `dy!server`, with Turkish `/sunucu` and `dy!sunucu`,
  showing the server name, visible player count/capacity and Minecraft version.

## Improved

- New-installation linking codes default to 8 characters. Explicit existing
  code lengths remain unchanged.
- Supported upgrades preserve account links, custom settings, translations and
  comments. Identical migration backups are reused rather than duplicated.
- Reconnect updates only missing or changed DyDiscord slash commands.
- English and Turkish help, settings documentation and messages cover the new
  role, reward and panel controls.

## Upgrade notes

- Role synchronization and boost rewards are disabled by default. Configure
  Discord role IDs and reward commands before enabling them.
- LuckPerms is required for group synchronization; Vault remains a chat display
  provider. Discord roles never grant Minecraft groups, permissions or OP.
- Privileged, managed, inaccessible and console-access roles cannot be assigned
  by role synchronization. Disabling synchronization does not remove all roles.
- Back up the old JAR and complete plugin data folder with the server stopped.
  Existing accounts do not need to be linked again. Restoring a database backup
  does not reverse Discord role changes or commands already run for rewards.
- Human account interactions, visual clients, paid boost transitions, external
  balances and a Java 17 runtime remain outside the latest verification.

---

# DyDiscord v1.0.0-BETA

**Initial Beta release**

By Claww / DyPlugin

## Added

## Accounts and panel

- Private Minecraft–Discord account linking with short-lived random codes.
- One persistent Discord account panel with Link Account, Unlink and My Account.
- Private account information, username-based messages and confirmed unlinking.
- Local SQLite persistence for accounts, language preferences and panel state.

## Chat and events

- Independently configurable Minecraft → Discord and Discord → Minecraft text chat.
- LuckPerms rank support, Vault Chat fallback and Discord role/display-name formatting.
- Optional join, leave and death embeds with configurable text and colors.
- A bounded yellow notification during normal server shutdown.
- Mention suppression and safe handling of user-provided formatting.

## Administration and language

- Optional private console log relay and trusted-role console command access.
- Local audit records, bounded queues and sensitive-value masking.
- English and Turkish messages, localized help/TAB and per-player language selection.
- Guild slash commands, `dy!` entry points and setup diagnostics.
- Configurable panel image, commented YAML defaults and compatible configuration migrations.
- Compatible-version notifications through Modrinth without automatic installation.

## Compatibility and license

- Java 17 bytecode and Spigot API 1.20.4 baseline; use the runtime required by your server.
- Beta acceptance is incomplete: human account interactions, visual clients and selected
  real integrations still need validation. See the repository's compatibility notes.
- Project terms are GPL v3 with additional attribution, non-commercial distribution
  and public-source requirements; dependency notices are included.
