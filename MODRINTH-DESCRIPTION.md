# DyDiscord

**Link your accounts. Connect your chat. Sync your roles.**

DyDiscord is a Discord integration plugin for Minecraft servers. Connect your community, link player accounts and manage your server through your own Discord bot.

**Version v1.0.1-BETA** includes private account linking, two-way chat, Minecraft-to-Discord role synchronization, configurable boost rewards and optional console access with English and Turkish support. The bot runs inside the plugin; no separate bot service or external database is required.

---

## Features

- **Private account linking:** Connect Minecraft and Discord accounts through an in-game code and private form, using a panel or slash commands.
- **Two-way chat:** Share public conversations between Minecraft and Discord, with rank display and configurable event notifications.
- **Role synchronization:** Give linked players Discord roles and connect LuckPerms groups, including temporary ranks, to configured role IDs.
- **Boost rewards:** Run your installed provider's commands to award ranks, in-game currency or site credit to linked boosters.
- **Console access:** Relay actual server output and optionally allow trusted Discord roles to run Minecraft console commands.
- **English and Turkish:** Localized messages, help and TAB suggestions, with independent Minecraft and Discord language settings.

Use your own Discord bot and choose separate channels for linking, chat and console access. Enable only the optional systems you want.

---

## Link your accounts, your way

Get your code with `/dydiscord link`, then select **Link Account** on the Discord panel or use Discord `/link` to enter it in a private form. Codes are random, short-lived and single-use. New installations use 8-character codes; existing configured lengths are preserved. Each Minecraft account can be linked to one Discord account.

The panel keeps everything in one card: **Link Account**, **Unlink** and **My Account**. It uses your Discord server icon or a custom image and remains recorded across bot restarts.

Prefer commands? Use `/dydiscord admin panel disable` to turn off panel access while keeping private Discord `/link`, `/account` and `/unlink` available. Enable it again with `/dydiscord admin panel enable`; the same recorded panel is reused. In command-only mode, `dy!` account entry points direct players to the matching slash commands without exposing account details.

Account messages show Minecraft and Discord usernames. Players can view their own link privately or remove it after confirmation. Linking never grants Minecraft OP or permissions. Optional Discord roles are controlled by the server owner's role configuration.

## Connect Minecraft ranks to Discord roles

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

## Keep your community connected

Share public conversations between Minecraft and a separate Discord chat channel. Enable Minecraft → Discord, Discord → Minecraft or both directions.

Minecraft messages can include ranks from LuckPerms, with Vault Chat as a fallback. Discord messages use the member's server display name and highest eligible role. Chat works without a rank provider.

Configure message formats and optional **join, leave and death notifications**. Events use short embeds with configurable text and colors; death notifications preserve the original death message. Normal chat stays plain text, and player messages are not translated.

Forwarded messages do not create Discord mention notifications. Bot/webhook messages, edits, history and attachments are not forwarded. Unknown Minecraft chat listeners pause outbound forwarding to protect private or moderated conversations; those systems need an explicitly supported integration.

## Manage your server from Discord

Follow actual server output in a separate private console channel. Optionally allow trusted Discord roles to run Minecraft console commands from that channel.

Log forwarding and command execution are controlled separately. The channel shows real server output without additional command acceptance or rejection replies. Normal shutdown attempts a short yellow **“The server is shutting down.”** embed before the bot disconnects.

Console access gives full Minecraft console power; reserve it for trusted administrators. Local audit records, bounded queues and known-sensitive-value masking support the relay. Log and shutdown-notice delivery cannot be guaranteed during crashes or network failures.

## English and Turkish, automatically

With `lang: auto`, messages and TAB suggestions use the player's saved preference or Minecraft language. Turkish clients receive Turkish; other languages fall back to English.

**TAB shows only the effective language. Both languages still work when typed.** Turkish ASCII spellings are accepted too.

Players can choose `/dydiscord language auto|en|tr`. Administrators can force a language with `/dydiscord admin language auto|en|tr`, taking priority over personal preferences. The `admin` branch stays the same in both languages.

Discord's `bot.language` controls its panel, forms and messages independently; changing it requires a bot restart.

## Update notifications

DyDiscord checks Modrinth for newer public versions compatible with your Minecraft version and server platform. Notifications go to the console and administrators with `dydiscord.admin.update`.

Beta installations can receive compatible Beta or stable recommendations; stable installations do not follow prereleases. Failed requests are retried. The checker uses the project identifier `dydiscord`, so GitHub-only releases do not trigger it.

The checker provides notifications; it does not download or replace your plugin JAR.

## Commands

| Command | Purpose |
| --- | --- |
| `/dydiscord` | Show your Discord invitation |
| `/dydiscord help` | Show available commands |
| `/dydiscord link` | Get your private linking code |
| `/dydiscord status` | View your linked accounts |
| `/dydiscord unlink` | Start unlink confirmation |
| `/dydiscord confirm <token>` | Confirm a pending action |
| `/dydiscord cancel` | Cancel a pending action |
| `/dydiscord language <auto\|en\|tr>` | Choose your preferred language |
| `/dydiscord admin help` | Show administration commands |
| `/dydiscord admin reload` | Reload settings and messages |
| `/dydiscord admin bot enable\|disable\|restart` | Manage the bot connection |
| `/dydiscord admin panel send` / `panel refresh` | Create or update the panel |
| `/dydiscord admin panel enable\|disable` | Switch panel access on or off |
| `/dydiscord admin roles ...` | Configure roles, preview changes and synchronize accounts |
| `/dydiscord admin status` / `doctor` | Check connection and setup health |

`/discord` uses the same router. Both help paths open the same menu, with displayed commands using `/dydiscord`. Help and TAB are filtered by permissions and language.

Discord includes slash commands and `dy!` entry points for help, linking, account details, unlinking and status. `/server` and `dy!server` show the server name, visible player count/capacity and Minecraft version. Turkish equivalents include `/sunucu` and `dy!sunucu`. Account details are shown through private interactions.

## Permissions

| Permission | Default access | Purpose |
| --- | --- | --- |
| `dydiscord.player` | Everyone | Player permission group |
| `dydiscord.invite` | Through player group | View the invitation |
| `dydiscord.help` | Through player group | View help |
| `dydiscord.link` | Through player group | Generate linking codes |
| `dydiscord.status` | Through player group | View your linked accounts |
| `dydiscord.unlink` | Through player group | Remove your link with confirmation |
| `dydiscord.language` | Through player group | Choose your language |
| `dydiscord.chat.send` | Through player group | Forward public chat to Discord |
| `dydiscord.chat.receive` | Through player group | Receive Discord chat in Minecraft |
| `dydiscord.admin` | OP | Player and administration permission groups |
| `dydiscord.admin.update` | Through admin group | Receive update notifications |

Permissions can be configured through your permission manager. Administration commands have separate leaf permissions; see the [full setup guide](https://github.com/DyPlugin/DyDiscord/blob/main/docs/setup.md). Optional features must also be enabled in configuration.

## Compatibility and integrations

Compiled for **Java 17 bytecode** against **Spigot API 1.20.4**. Use the Java runtime required by your server.

Testing includes automated regression tests and scoped checks on **Paper 26.3 / Java 25**, LuckPerms and a test Discord server. Coverage differs by build and scenario; human account interactions, visual clients, paid boost transitions, external balances and a Java 17 runtime remain outside the latest verification. Historical results in the [compatibility notes](https://github.com/DyPlugin/DyDiscord/blob/main/docs/compatibility-test.md) apply to the builds identified there, not automatically to this release.

| Optional provider | Purpose |
| --- | --- |
| LuckPerms | Minecraft rank display and real group membership for role synchronization |
| Vault + Chat provider | Fallback Minecraft rank display |

Neither provider is required for account linking, linked-account roles or core chat. Group synchronization requires LuckPerms. Folia, proxies and third-party hot loaders are outside the current support claim.

Offline-mode linking is available. Your login system must authenticate players and block all DyDiscord command aliases before login; no login plugin API integration is included.

## Setup and upgrades

1. Stop your server and place the JAR in `plugins/`.
2. Start the server to generate `plugins/DyDiscord/`.
3. Create your own Discord bot and invite it with the `bot` and `applications.commands` scopes.
4. Enter the token and quoted guild ID locally in `bot.yml`.
5. Run `/dydiscord admin reload`, then `/dydiscord admin bot enable`.
6. For panel access, set the channel with `/dydiscord admin panel channel <id>` and run `/dydiscord admin panel send`. For command-only access, use `/dydiscord admin panel disable`.

**The bot, chat bridge, event notifications, role synchronization, boost rewards and console features start disabled.** Configure and enable the features you want. Message Content intent is required for Discord text input used by chat, `dy!` commands and console commands; private account slash commands do not need it. Server Members intent enables member and boost event updates; periodic fresh-member checks provide a fallback. Keep your token private and do not grant the bot Administrator.

Before upgrading, back up the old JAR and complete DyDiscord data folder with the server stopped. Supported migrations add missing options without replacing custom settings, translations or comments. Accounts, language preferences and the panel record remain in the local database; players do not need to link again. Restoring a database backup does not reverse Discord role changes or reward commands already executed.

## Support and license

DyDiscord by **Claww / DyPlugin** uses the **DyDiscord License**, containing attribution, non-commercial distribution and public-source requirements. Free distribution is permitted; selling builds or requiring payment for access is prohibited by the project terms. Dependencies retain their independent licenses.

Read the [setup guide](https://github.com/DyPlugin/DyDiscord/blob/main/docs/setup.md) for configuration and permissions. Report bugs or request features through [GitHub Issues](https://github.com/DyPlugin/DyDiscord/issues). Never include bot tokens or private account data in a report.

[Source code and issues](https://github.com/DyPlugin/DyDiscord) · [License](https://github.com/DyPlugin/DyDiscord/blob/main/LICENSE)
