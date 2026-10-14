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

### Accounts and panel

- Private Minecraft–Discord account linking with short-lived random codes.
- One persistent Discord account panel with Link Account, Unlink and My Account.
- Private account information, username-based messages and confirmed unlinking.
- Local SQLite persistence for accounts, language preferences and panel state.

### Chat and events

- Independently configurable Minecraft → Discord and Discord → Minecraft text chat.
- LuckPerms rank support, Vault Chat fallback and Discord role/display-name formatting.
- Optional join, leave and death embeds with configurable text and colors.
- A bounded yellow notification during normal server shutdown.
- Mention suppression and safe handling of user-provided formatting.

### Administration and language

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
