# DyDiscord — Minecraft and Discord Made Simple

**Link your accounts. Connect your chat. Manage your server.**

DyDiscord is a Discord integration plugin for Minecraft servers. Connect your community, link player accounts and manage your server through your own Discord bot.

**Version 1.0.1** includes private account linking, two-way chat, role synchronization, configurable rewards, a personal chat visibility preference and giveaways, with English and Turkish support. The bot runs inside the plugin; no separate bot service or external database is required.

**Minecraft:** 1.20.x–26.x · **Platforms:** Paper, Purpur, Bukkit and Spigot. Server-version support and agent verification evidence are distinguished in the compatibility notes.

New setups connect automatically after the token and guild are configured and reloaded. Choosing a panel channel sends the first panel automatically; restarts and refreshes reuse it. Explicitly disabled settings stay disabled. Administrator help is paginated with `/dydiscord admin help <page>` (Turkish: `/dydiscord admin yardım <sayfa>`). Discord help uses the same organized embed for slash and `dy!` commands.

---

## Why DyDiscord?

**Your bot, your server.** Use your own Discord application and choose separate channels for account linking, chat and console access.

**Private account linking.** Players connect their accounts with an in-game code and a private Discord form. Account details stay private, and unlinking requires confirmation.

**Automatic language.** Minecraft messages and TAB suggestions follow an explicit global language, otherwise the saved player preference and bot language. **English** and **Turkish** are included. Discord has its own language setting.

**Optional features.** Enable the chat directions, player notifications, role synchronization, boost rewards and console features you want. Use the account panel or switch to command-only access.

---

## Link your accounts, your way

Get your code with `/dydiscord link`, then select **Link Account** on the Discord panel or use Discord `/link` to enter it in a private form. Codes are random, short-lived and single-use. Codes default to six unambiguous characters; the former shipped length of eight is migrated to six, while other custom lengths are preserved. The code can be copied with a click; hover for the hint. A separate clickable invitation is included. Each Minecraft account can be linked to one Discord account.

The panel keeps everything in one card: **Link Account**, **Unlink** and **My Account**. It uses your Discord server icon or a custom image and remains recorded across bot restarts.

Prefer commands? Use `/dydiscord admin panel disable` to turn off panel access while keeping private Discord `/link`, `/account` and `/unlink` available. Enable it again with `/dydiscord admin panel enable`; the same recorded panel is reused. In command-only mode, `dy!` account entry points direct players to the matching slash commands without exposing account details.

Account messages show Minecraft and Discord usernames. Players can view their own link privately or remove it after confirmation. Linking never grants Minecraft OP or permissions. Optional Discord roles are controlled by the server owner's role configuration.

## Keep your community connected

Share public conversations between Minecraft and a separate Discord chat channel. Enable Minecraft → Discord, Discord → Minecraft or both directions.

Minecraft messages can include ranks from LuckPerms, with Vault Chat as a fallback. Discord messages use the member's server display name and highest eligible role. Chat works without a rank provider.

Configure message formats and optional **join, leave and death notifications**. Events use short embeds with configurable text and colors; death notifications preserve the original death message. Normal chat stays plain text, and player messages are not translated.

Forwarded messages do not create Discord mention notifications. Bot/webhook messages, edits, history and attachments are not forwarded. Unknown Minecraft chat listeners pause outbound forwarding to protect private or moderated conversations; those systems need an explicitly supported integration.

## Connect Minecraft groups to Discord roles

Give linked players one or more configured Discord roles, even without LuckPerms. With **LuckPerms**, map real Minecraft group membership to Discord role IDs, including direct, inherited and temporary groups. One group can grant multiple roles.

Use all matching rules or select the highest explicitly configured priority. Fixed contexts keep role selection consistent for online and offline players. Temporary rank expiry is reconciled without requiring the player to join Minecraft again.

Changes are checked after linking, unlinking, group updates, reconnects and through gradual periodic reconciliation. Existing linked accounts are included. Preview expected changes, synchronize an account or all linked accounts, and separately confirm managed-role cleanup through `/dydiscord admin roles`.

Only configured roles are managed; unrelated member roles remain untouched. Privileged, managed and console-access roles are blocked, and the bot must have **Manage Roles** plus sufficient role hierarchy. Do not independently manage the same roles with another bot or manual assignments.

Role failures do not delete account links. Pending work is re-evaluated when connectivity returns, while unlinking and removed rules retain cleanup information. Disabling synchronization stops new operations without automatically deleting all managed roles.

Synchronization is **Minecraft → Discord only**. Discord roles never grant Minecraft groups, permissions or OP. Vault Chat supports rank display, not group synchronization.

## Reward your Discord boosters

Optionally run configured Minecraft console commands for linked accounts boosting your Discord server. Use your installed provider's commands to award a temporary rank, in-game currency or site credit. DyDiscord does not bundle an economy provider or a direct website-credit API.

Reward commands support `{player}` and `{uuid}`. Claims are recorded per continuous boost period, with a configurable cooldown; additional individual boosts do not each create another claim. Optional removal commands can handle a recorded boost ending or the account being unlinked.

Configure rewards under `boost-rewards` in `bot.yml`, using `commands`, optional `remove-commands`, `recheck-seconds` and `cooldown-hours`. Rewards are disabled by default. Disabling them does not automatically reverse existing rewards, and uncertain command attempts are not automatically replayed. Command dispatch does not confirm that an external balance changed.

[Boost reward setup and recovery guide](https://github.com/DyPlugin/DyDiscord/blob/main/docs/boost-rewards.md)

## Manage your server from Discord

Follow actual server output in a separate console text channel. Optionally allow trusted Discord roles to run Minecraft console commands from that channel.

Log forwarding and command execution are controlled separately. The channel shows real server output without additional command acceptance or rejection replies. Normal shutdown attempts a short yellow **“The server is shutting down.”** embed before the bot disconnects. Bot restart and configuration reload do not produce this shutdown notification.

The console channel may be public. Everyone who can view it can read relayed server output; choose its visibility deliberately. Command execution still requires the configured trusted role or permitted Discord Administrator, current membership and fresh channel checks. Console access gives full Minecraft console power; reserve command authorization for trusted administrators. Local audit records, bounded queues and known-sensitive-value masking support the relay. Log and shutdown-notice delivery cannot be guaranteed during crashes or network failures.

## English and Turkish, automatically

With `lang: auto`, messages and TAB suggestions use the player's saved en/tr preference, otherwise the bot language. An explicit global game language takes priority.

**TAB shows only the effective language. Both languages still work when typed.** Turkish ASCII spellings are accepted too.

Players can choose `/dydiscord language auto|en|tr`. Administrators can force a language with `/dydiscord admin language auto|en|tr`, taking priority over personal preferences. The `admin` branch stays the same in both languages.

An unset game language follows the bot language after saved personal preferences. Explicit game language overrides are preserved. `bot.language: auto` follows the configured game language, with English when both are auto; `en` and `tr` select the bot language directly. `/dydiscord admin bot language <en|tr|auto>` applies a bot-language change with a controlled restart.

## Update notifications

DyDiscord checks Modrinth for newer public versions compatible with your Minecraft version and server platform. Notifications go to the console and administrators with `dydiscord.admin.update`.

Stable installations receive compatible stable recommendations. Set updates.include-beta to opt into future betas; superseded historical betas are excluded. Failed requests are retried. The checker uses the project identifier `dydiscord`, so GitHub-only releases do not trigger it.

The checker provides notifications; it does not download or replace your plugin JAR.

---

## Commands

### Player Commands

| Command | What it does |
| --- | --- |
| `/dydiscord` | Show the configured Discord invitation |
| `/dydiscord help` | Show commands available to you |
| `/dydiscord link` | Generate your private linking code |
| `/dydiscord status` | View your linked accounts |
| `/dydiscord unlink` | Start unlink confirmation |
| `/dydiscord confirm <token>` | Confirm your pending action |
| `/dydiscord cancel` | Cancel your pending confirmation |
| `/dydiscord language <en\|tr\|auto>` | Choose your Minecraft language |
| `/dydiscord chat <on\|off>` | Show or hide incoming Discord chat for yourself |

**`/discord`** uses the same command router. Linking shortcuts include `/dylink`, `/link` and `/esle`; use `/dydiscord link` if another plugin owns a shortcut.

### Admin Commands

All paths below start with **`/dydiscord admin`** or the equivalent **`/dyda`**. The same leaf permissions apply. Server console access is supported except for the player-only token entry flow.

| Subcommand | What it does |
| --- | --- |
| `help [page]` | Show numbered, permission-filtered administration pages |
| `prefix <text>` | Save the Minecraft message prefix, including spaces |
| `bot guild <id>` | Set the Discord server ID |
| `bot token` | Open a 60-second player-only token entry session; takes no value argument |
| `bot language <en\|tr\|auto>` | Set bot language with a controlled restart |
| `chat discord-minecraft <on\|off>` | Toggle Discord → Minecraft globally |
| `chat minecraft-discord <on\|off>` | Toggle Minecraft → Discord globally |
| `chat both <on\|off>` | Toggle both directions globally |
| `chat channel <id>` | Set the separate chat channel |
| `giveaway start <seconds> <winners> <prize>` | Start a configured giveaway |
| `giveaway end\|cancel <id>` / `giveaway list` | Finish, cancel or list giveaways |
| `invite <url>` | Set the Discord invitation |
| `language <en\|tr\|auto>` | Set the global Minecraft language |
| `reload` | Reload valid settings and messages |
| `status` | View connection and feature status |
| `doctor` | Check setup and access requirements |
| `bot enable\|disable\|restart` | Manage the bot connection |
| `panel channel <id>` | Select the account panel channel |
| `panel send` / `panel refresh` | Create or update the account panel |
| `panel enable\|disable` | Enable or disable panel access |
| `console channel <id>` | Select the console text channel |
| `console enable\|disable` | Toggle log forwarding |
| `console commands enable\|disable` | Toggle remote console commands |
| `unlink <uuid> <reason>` | Start a confirmed administrative unlink |

### Role administration

All paths below start with **`/dydiscord admin roles`**.

| Subcommand | Purpose |
| --- | --- |
| `status` | View synchronization status and setup problems |
| `preview <uuid>` | Preview an account's expected changes |
| `sync <uuid>` | Synchronize an account |
| `sync all` | Start gradual synchronization of linked accounts |
| `cleanup preview` | Preview managed-role cleanup |
| `cleanup confirm <token>` | Confirm cleanup while synchronization is disabled |

### Discord Commands

English: **`/help`**, **`/link`**, **`/account`**, **`/unlink`**, **`/status`**, **`/server`**.

Turkish: **`/yardım`**, **`/eşle`**, **`/hesap`**, **`/kaldır`**, **`/durum`**, **`/sunucu`**.

The selected bot language determines guild slash commands. **`dy!`** message commands provide the corresponding entry points; account operations direct users to private interactions rather than posting account details publicly. In command-only mode, account message commands direct users to the matching slash commands.

The server command shows the server name, visible player count/capacity and Minecraft version without listing player identities.

---

## Permissions

| Permission | Default | What it allows |
| --- | --- | --- |
| `dydiscord.player` | Everyone | Player command and chat permission group |
| `dydiscord.admin` | OP | Player group and all administration permissions |

Every individual permission below defaults to **false** and is granted through its corresponding group. You can grant or deny leaves separately; help, TAB and execution respect the effective permission. Confirmations recheck permissions, and explicit leaf denial is respected even for OPs.

| Player permission | What it allows |
| --- | --- |
| `dydiscord.invite` | View the Discord invitation |
| `dydiscord.help` | View player help |
| `dydiscord.link` | Generate linking codes |
| `dydiscord.status` | View your linked accounts |
| `dydiscord.unlink` | Remove your account link |
| `dydiscord.language` | Set your personal Minecraft language |
| `dydiscord.chat.send` | Forward your public Minecraft chat to Discord |
| `dydiscord.chat.receive` | Receive Discord chat in Minecraft |
| `dydiscord.chat.preferences` | Toggle your incoming Discord chat visibility |

| Administration permission | What it allows |
| --- | --- |
| `dydiscord.admin.invite` | Set the invitation |
| `dydiscord.admin.language` | Set the global Minecraft language |
| `dydiscord.admin.reload` | Reload configuration and messages |
| `dydiscord.admin.status` | View administration status |
| `dydiscord.admin.doctor` | Run setup diagnostics |
| `dydiscord.admin.bot` | Enable, disable or restart the bot |
| `dydiscord.admin.bot.guild` | Set the Discord server ID |
| `dydiscord.admin.bot.token` | Open protected player-only token entry |
| `dydiscord.admin.prefix` | Change the Minecraft prefix |
| `dydiscord.admin.chat` | Toggle global chat directions |
| `dydiscord.admin.chat.channel` | Set the chat channel |
| `dydiscord.admin.alerts` | Receive bounded setup and validation warnings |
| `dydiscord.admin.panel.channel` | Set the panel channel |
| `dydiscord.admin.panel.send` | Send or update the panel |
| `dydiscord.admin.panel.refresh` | Refresh the panel |
| `dydiscord.admin.panel.toggle` | Enable or disable panel access |
| `dydiscord.admin.console.channel` | Set the console channel |
| `dydiscord.admin.console.toggle` | Toggle console log forwarding |
| `dydiscord.admin.console.commands` | Toggle remote command execution |
| `dydiscord.admin.unlink` | Remove another player's link with confirmation |
| `dydiscord.admin.update` | Receive compatible Modrinth update notifications |

### Role synchronization permissions

| Permission | Purpose |
| --- | --- |
| `dydiscord.admin.roles.status` | View role synchronization status |
| `dydiscord.admin.roles.preview` | Preview account role changes |
| `dydiscord.admin.roles.sync` | Synchronize an account |
| `dydiscord.admin.roles.sync.all` | Synchronize all linked accounts gradually |
| `dydiscord.admin.roles.cleanup.preview` | Preview managed-role cleanup |
| `dydiscord.admin.roles.cleanup.confirm` | Confirm managed-role cleanup |

Role mappings, authorized console roles/Administrator acceptance, reward commands and other advanced options are edited in `bot.yml`, then applied with reload and any requested bot restart. Announcement commands and scheduled/startup announcements are removed.

Minecraft permissions and Discord console roles are separate. Account linking does not automatically grant either.

---

## Setup

Choose one distribution:

- **`DyDiscord-1.0.1.jar`** is the small download. Spigot/Paper resolves
  its declared libraries from Maven Central (or the server's configured mirror)
  and stores them in the server library cache. First installation needs internet
  access and may take longer. Missing required libraries prevent plugin loading.
- **`DyDiscord-1.0.1-standalone.jar`** bundles runtime dependencies for
  offline installation. Install only one of the two JARs at a time.

The server resolver can download additional transitive JDA libraries and prefer
server-provided versions. Its cache and version selection are server-owned;
this is not a guarantee that effective versions exactly match the build graph.
No voice feature is enabled. See [packaging](docs/packaging.md).

1. Stop your server, then place **`DyDiscord-1.0.1.jar`** in your server's `plugins/` folder and start the server.
2. Create your own bot in the [Discord Developer Portal](https://discord.com/developers/applications) and invite it with the `bot` and `applications.commands` scopes and the required channel permissions.
3. Prefer entering the token and quoted Discord server ID locally in **`plugins/DyDiscord/bot.yml`**. Alternatively, an authorized online player can use `/dyda bot guild <id>` and `/dyda bot token` (no argument). The latter opens a 60-second session: submit the token as the next slash-prefixed entry, or `/dyda cancel` to cancel. The plugin cancels and scrubs that entry before routing; client history and third-party loggers cannot be guaranteed. An active `DYDISCORD_BOT_TOKEN` prevents file-token entry. Logout, reload, restart or permission loss invalidates the session; successful saving restarts the bot.
4. Run **`/dydiscord admin reload`**, then **`/dydiscord admin bot enable`**.
5. Select the account channel with **`/dydiscord admin panel channel <id>`**, then run **`/dydiscord admin panel send`**. For command-only access, use **`/dydiscord admin panel disable`**.
6. Configure the separate chat and console channels if needed. Use **`/dydiscord admin doctor`** to check your setup.

New files enable bot connection and console log relay, but wait for credentials and a configured accessible channel. Remote commands, chat forwarding, player-event notifications, role synchronization and rewards remain opt-in. Plain-text Discord chat, `dy!` commands and console input require **Message Content intent**. Slash commands and account interactions can operate without it. Role and boost member events use **Server Members intent**; periodic fresh-member checks provide a fallback. The bot does not need Administrator permission.

Token, guild, bot language and intent changes require a **bot restart** after reloading. The panel enable flag can be applied through reload without a bot restart.

[Detailed setup guide](https://github.com/DyPlugin/DyDiscord/blob/main/docs/setup.md)

## Compatibility

| Area | Details |
| --- | --- |
| API baseline | Spigot API **1.20.4** |
| Compilation target | **Java 17 bytecode**; use the Java runtime required by your server |
| LuckPerms | Rank display and actual group membership for role synchronization |
| Vault + Chat provider | Fallback Minecraft rank display |
| Storage | Local SQLite; no external database required |
| Current scope | One Minecraft server, one bot and one Discord server |

Neither LuckPerms nor Vault is required for account linking, linked-account Discord roles or core chat. Group synchronization requires LuckPerms.

Testing includes automated regression tests and scoped checks on **Paper 26.3 / Java 25**, LuckPerms and a test Discord server. Coverage differs by artifact and scenario; this does not establish a complete version matrix.

Human account interactions, visual clients, real paid boost transitions, external economy/site balances and a Java 17 runtime remain outside the latest verification. Folia, proxies and third-party hot loaders are outside the current support claim.

Offline-mode linking is available, but your login system must authenticate players and block all DyDiscord command aliases before login. DyDiscord has no login-plugin authentication API integration.

[Compatibility and verification notes](https://github.com/DyPlugin/DyDiscord/blob/main/docs/compatibility-test.md)

## Data and upgrades

Account links, language preferences and panel state stay in the local database. Supported configuration migrations preserve custom settings, translations and comments, with backups before changed files are written. Back up the complete data folder and old JAR with the server stopped before upgrading.

Existing accounts do not need to be linked again. Identical migration backups are reused rather than duplicated; previously created backups are not automatically deleted.

Restoring local data does not reverse Discord role changes or reward commands already executed. Rolling back to an older database schema requires the matching older JAR and backup.

[Upgrade and rollback guide](https://github.com/DyPlugin/DyDiscord/blob/main/docs/upgrade-and-rollback.md)

## Source and Support

DyDiscord's source is public. Bug reports, feature requests and contributions are welcome on [GitHub](https://github.com/DyPlugin/DyDiscord).

The project license notice includes attribution, non-commercial distribution and public-source requirements. See the [full license](https://github.com/DyPlugin/DyDiscord/blob/main/LICENSE) for the terms; dependency licenses remain independent.

[Source code and documentation](https://github.com/DyPlugin/DyDiscord) · [Report an issue](https://github.com/DyPlugin/DyDiscord/issues) · [Discord support](https://discord.gg/dyhub) · [Published releases](https://github.com/DyPlugin/DyDiscord/releases) · [Full license](https://github.com/DyPlugin/DyDiscord/blob/main/LICENSE)

**By Claww / DyPlugin.**

## Community tools in 1.0.1

Discord offers configured server information, player lists, addresses and useful links. Players use `/dydiscord chat on|off` only to show or hide incoming Discord chat; it does not change Minecraft → Discord sharing or event notifications. The preference is persistent. Administrators use the explicit global direction commands above. First-link rewards and persistent button giveaways remain optional. Role mappings, console authorization and reward details are configured in YAML; the former advanced editing, report, setup and validation command families are removed. Use reload to validate and apply edited files.

### Account and console presentation in 1.0.1

Discord account details show both account names and a localized readable link date; raw account IDs are omitted. `account.time-zone` in `bot.yml` selects the time zone (default `UTC`); `account.show-integrated-roles` shows only assigned, configured managed roles and defaults to true. Empty role lists are hidden. Unlinking uses a private red Container with a red Unlink button and green Cancel button. Link success bolds the Minecraft name.

When `discord.invite-url` is blank, the connected bot creates and stores a reusable permanent invite in a suitable channel if it has Create Invite permission. Manual invites take priority. The bot never uses the configured console channel for this invitation.

`/dydiscord admin help [page]` and `/dyda help [page]` show numbered pages only; topic help is removed. Player help omits its own help entry. Headers show the prefix once and the pagination footer is unprefixed. Chat accepts short consecutive message bursts while retaining duplicate and bounded rate controls. Console log forwarding uses its own worker, checks for output every 250 ms, and still verifies current guild/channel permissions before each send. Network and Discord rate limits affect end-to-end latency. See [current simplification verification](docs/simplify-1.0.1-verification.md); older reports apply to their recorded artifacts.
