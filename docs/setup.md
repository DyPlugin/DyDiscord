# DyDiscord 1.0.2

The default JAR is the small distribution. Its declared libraries are resolved
and cached by Spigot/Paper before plugin enable; initial installation needs
internet access. Use `DyDiscord-1.0.2-standalone.jar` for an installation
that must work without downloading libraries. Install exactly one distribution.
See [distribution packaging](packaging.md) for cache and version-selection limits.

**Claww · DyPlugin** — a Discord bot inside your Minecraft server, with private
account linking and an optional console channel. English and Turkish are
included. No separate Node.js service or external database is required.

This release is named 1.0.2. Human account-linking and visual client acceptance
remain incomplete. See [test evidence](compatibility-test.md). DyDiscord has
no dependency on DyClaim. See the project’s custom [LICENSE](../LICENSE) and [dependency notices](../LICENSE-NOTICE.md) for distribution terms.

## Build and technical target

Run `gradlew.bat clean build` on Windows, or `./gradlew clean build` elsewhere.
Gradle Wrapper 9.1.0, dependency locks and SHA-256 verification metadata are included.
The build was checked with JDK 21.0.22.1 and produces Java 17 bytecode against
Spigot API 1.20.4. Use a JDK supported by Gradle 9.1; JDK 17–25 is the build target.
Install **`build/libs/DyDiscord-1.0.2.jar`**, not the `plain` JAR.

The API baseline is not proof of runtime compatibility. Spigot 1.20.4 / Java 17
is the initial candidate; newer Spigot/Paper builds need their own live evidence.
Folia, proxies, Bedrock/Floodgate, server `/reload` and
third-party hot loaders are not supported claims for this release.

## First setup

1. Put the bundled JAR in your server's `plugins` directory and start the server.
   DyDiscord creates configuration, messages and local SQLite data. Bot/panel setup is enabled; credentials and a separate channel are still required. Chat directions and join/quit/death notices default on. Console relay defaults off; its command subflag defaults on, with Discord role/Administrator and current access checks preserved.
2. In the [Discord Developer Portal](https://discord.com/developers/applications),
   create your own application and bot. Invite it with `bot` and
   `applications.commands` scopes. Do not grant the bot Administrator.
3. Locally edit `plugins/DyDiscord/bot.yml`: fill `token` and a quoted `guild-id`.
   Optional `DYDISCORD_BOT_TOKEN` overrides the file token. Never paste a token as a normal command argument, public message or support report. In-game token entry was removed. One token must be
   used by only one bot process.
4. Set the invitation with `/dydiscord admin invite https://discord.gg/YourCode`.
   Set `server-name` in `config.yml`. Run `/dydiscord admin reload` after file edits,
   which starts a newly configured bot. If you explicitly disabled it, run
   `/dydiscord admin bot enable`. Running sessions still report required restarts.
5. Run `/dydiscord admin panel channel <id>`; the first panel is sent automatically
   when connected. `panel send` / `panel refresh` remain available for repair.
   One Components V2 Container holds the Discord server icon and a compact explanation of
   each account action. A small divider separates the text from the three
   account buttons; the card has no brand label or footer. Sends, refreshes and restarts update the recorded
   message instead of adding copies, including migration of an older classic panel.
6. Run `/dydiscord admin doctor` to diagnose missing settings and permissions.

Initial `bot.yml` edits can be loaded with `/dydiscord admin reload` while the bot
is disabled. Reload stages the local settings; Discord channel/role checks happen
before use after connection. A new enabled setup connects after reload; explicitly disabled installations use `/dydiscord admin bot enable`. Missing token
or guild gives a setup-specific response. Existing active channel policies still
require live validation when changed.

Minecraft's default prefix is blue: `&7[&9DyDiscord&7]`. The former exact default
`&7[&dDyDiscord&7]` is upgraded on successful load/reload; custom prefixes survive.

The account panel needs View Channel, Send Messages, Embed Links and Read Message
History. Plain console output needs View Channel and Send Messages; the optional
shutdown embed additionally needs Embed Links. Console channels may be public, exposing output to everyone with channel access. Remote execution still requires explicit configured authority and fresh membership/channel checks. No Manage Messages
or presence intent is required. Role/boost member-event features request Server
Members Intent when enabled. Enable **Message Content Intent** in the Portal for
`dy!`, console input or Discord-to-Minecraft chat. Rejection (4014) logs the affected
features and reconnects for slash/panel only; fix Portal settings and restart the bot.
The bot name, avatar and application description are Portal settings; a panel
title is not a bot profile edit. DyDiscord does not set a profile activity/status
line. A legacy `bot.activity-text` value is ignored; diagnostic status commands
remain available.

The panel is one Discord card: Link Account opens the private code form,
My Account shows your own link, and Unlink starts a private confirmation.
The heading, explanation and privacy note share one text block, with a blank
line between the action descriptions. A small divider separates the text from
the three buttons below, ordered Link Account → Unlink → My Account.
The panel has no brand label or footer. Commands and account replies do not repeat
a branding footer. The Container replaces classic
embeds in the panel message; its recovery marker is not visible to users.

## Player commands

| Command | Turkish / ASCII aliases | Leaf permission |
| --- | --- | --- |
| `/discord` | same | `dydiscord.invite` |
| `/discord help` | `yardım`, `yardim` | `dydiscord.help` |
| `/discord link` | `eşle`, `esle` | `dydiscord.link` |
| `/discord status` | `durum` | `dydiscord.status` |
| `/discord unlink` | `kaldır`, `kaldir`; legacy `ayır`, `ayir` | `dydiscord.unlink` |
| `/discord confirm <token>` | `onayla` | pending operation's permission |
| `/discord cancel` | `iptal` | pending operation's owner |
| `/discord language <en\|tr\|auto>` | `lang`, `dil` | `dydiscord.language` |

`/dydiscord` uses the same router. `/link`, `/esle`, `/eşle`, `/hesap-esle` and
`/hesap-eşle` are registered shortcuts; Unicode roots are **not live verified**.
If another plugin owns `/discord` or a shortcut, use `/dydiscord link` or
`/dydiscord:dydiscord link`. DyDiscord does not forcibly replace another command.
The invitation works even with the bot disabled and is sent only to the caller.
Help lists all permitted player commands, including invite and confirm/cancel;
console help marks player-only operations instead of hiding them. Execution of
those operations still requires a player. Global language can be changed in-game
or from the console with `/dydiscord admin language tr`; personal preference uses
`/discord language tr` in-game and follows the documented global override.

Generate your code in your current Minecraft session. The in-game reply contains
the copyable code with a hover hint, then a separate clickable invitation. Open the
shared panel, press Link Account and enter the code in the modal. New setups use
6 unambiguous random characters by default; the previous shipped length of 8 migrates to 6, while other custom lengths are kept.
Codes expire after 5 minutes and have a 30-second
generation cooldown. Leave the game and your code is revoked. Codes do not
survive restart. The owner's current online session and permission are checked
again at redemption. Linking grants no Minecraft or Discord permissions.

Accounts are one UUID to one Discord user ID. Success on both sides shows the
Minecraft name. Private Discord account details include a readable link date and optional assigned managed-role names, never raw IDs; older records without a stored Discord username use a
localized account label. Unlink responses are private and require a single-use,
actor/record-bound confirmation (30 seconds
by default). Local status and unlink can work while the bot is offline. New codes
require a usable bot. Code generation and linking also work with `online-mode=false`.
On offline servers, the server's login system must authenticate the player and
block every DyDiscord command root/alias before login. DyDiscord checks the online
session and leaf permission; it does not query a login plugin's authentication API.
Keep UUID assignment consistent: changing authentication mode can change UUIDs
and does not automatically migrate existing links.

## Administration

All paths below start with `/dydiscord admin`. They also work under `/discord`.
Local server console and RCON can administer the plugin; player-only account
operations require a player. Only the leaf permission is checked.

| Path | Leaf permission after `dydiscord.admin.` |
| --- | --- |
| `help` | at least one accessible administration leaf |
| `invite <url>` | `invite` |
| `language <en\|tr\|auto>` | `language` |
| `reload` | `reload` |
| `status` | `status` |
| `doctor` | `doctor` |
| `bot enable\|disable\|restart` | `bot` |
| `panel channel <id>` | `panel.channel` |
| `panel send` / `panel refresh` | `panel.send` / `panel.refresh` |
| `console channel <id>` | `console.channel` |
| `console enable\|disable` | `console.toggle` |
| `console commands enable\|disable` | `console.commands` |
| `console role add\|remove <id>` / `console role list` | `console.roles` |
| `console administrator on\|off` | `console.administrator` |
| `unlink <uuid> <reason>` | `unlink` |

`/discord setinvite <url>` and `/discord ayarla <url>` are aliases for `admin invite`.
Turkish grammar: `davet`, `dil`, `yenile`, `durum`, `kontrol`, `kanal`, `gönder/gonder`,
`konsol`, `komutlar`, `rol`, `ekle`, `çıkar/cikar`, `liste`, `aç/ac`, `kapat`,
`yenidenbaşlat/yenidenbaslat`, `yönetici/yonetici`, `ayır/ayir`.
Only grammar is translated; URL, UUID, ID, name and reason values remain intact.

`dydiscord.player` defaults to true and inherits player leaves.
`dydiscord.admin` defaults to op and inherits all leaves and the player group.
Every leaf itself defaults to false. A denied leaf stays denied even for an OP;
one granted administration leaf does not require the administration group.
Help and TAB show only permitted paths in the effective language.

## Language and reload

Minecraft `lang: en/tr` overrides personal preferences. Under `lang: auto`, a
saved `en/tr` preference wins, followed by the bot language. Preferences remain
stored while a global override is active. `/dydiscord admin lang tr` changes the
global game language. If a connected bot follows that setting through auto, the
command restarts it to apply the changed slash-command language.

Discord `bot.language: auto/en/tr` selects the bot language. Auto follows an
explicit global game language; both auto resolves to English. Changing the bot
language also changes the fallback for players without an explicit game choice.
`/dydiscord admin bot language tr` changes it with a controlled restart. File
edits to token, guild, bot language or Message Content require reload and restart.
Requested and applied states are distinct in status. Only recorded DyDiscord
guild slash command IDs are replaced/deleted; unrelated commands are preserved.
Name collisions with unowned commands fail rather than overwrite them.

Discord functions are `/help`, `/link`, `/account`, `/unlink`, `/status`, or in
Turkish `/yardım`, `/eşle`, `/hesap`, `/kaldır`, `/durum`. Prefix parsing accepts
English, Turkish and ASCII grammar. `dy!link/account/unlink` directs you to the
panel; normal prefix messages are public and never show account records or codes.
Slash Link opens the modal directly. Account and unlink replies are ephemeral.

Malformed configuration/message files keep the active snapshot. Missing message
keys fall back to bundled English with one warning. Both files and all messages
are validated before activation. Command writes preserve YAML comments and use
atomic replacement. Initial disconnected file setup may be staged by reload. Connected policy changes
and every actual panel/relay/dispatch operation require fresh permission verification. IDs must be quoted strings.

## Optional private console

Use a separate private text channel. Deny View Channel for `@everyone`; only
trusted administration roles and the bot should have access. Individual member
allow overrides, except the bot's, are conservatively rejected. Discord
Administrators can bypass channel denies. Configure allowed roles, select the
channel, then enable relay and, separately, remote commands. A role change that
would leave an untrusted role able to view the channel is rejected.

Every authorized plain new message in that channel is a Minecraft **console**
command. These users have full server console power, including `op`, permission
management and other plugin commands. This is not a restricted moderator sandbox.
Bots, webhooks, DMs, threads, edited/system messages, attachments, multiline input,
replayed message IDs and `dy!` commands are not dispatched. Membership and channel
policy are checked again before dispatch on the server thread.

Limits: one command/user/2 seconds, 20/channel/minute, 50 pending; relay flush
every 2 seconds with 2,000 lines/1 MiB maximum. Queue overflow drops old relay
lines with a summary. Rate limits and access failure stop outbound relay safely.
Durable audit/receipts precede command dispatch; crashes are not replayed.
The channel shows only the server's actual console output. Admission, rejection,
queue and execution failures add no bot response; local audit/error logs retain
the result. Commands with no output remain silent. The internal `dispatched`
receipt means dispatch was accepted, not that a command achieved its goal;
`not-found` distinguishes an unknown command from a policy rejection.
Use the Minecraft command `list` to see online players; `online` requires a
separately installed command provider.

## Data, recovery and limits

SQLite stores UUID, Discord ID, latest Minecraft name and Discord username, UTC link time, record
version, UUID language preferences, one panel record, owned slash IDs and bounded
console receipts. Linking codes and confirmations stay in memory; only salted
code digests are retained. Unlink removes the active link. Audit and backups may
still contain older identity records. Audit retention is 30 days/20 MiB; the
plugin does not manage Discord channel history retention.
The Discord username is display data: identity and uniqueness still use the ID.
The database upgrade preserves existing account records.

No rewards or role sync, music, external dashboard, telemetry, automatic installer
or login blocking is included. Known secrets are redacted from relay; arbitrary
third-party secrets cannot be guaranteed detectable. Logs before enable, after
disable and lost during crashes/outages are outside relay coverage.

For failed startup, keep existing data and fix the named setting or database.
For an ambiguous panel send, preserve the pending marker and investigate the
bot-authored panel; do not blindly resend or edit internal state. See
[security](security.md), [backup/rollback](upgrade-and-rollback.md),
[compatibility coverage](compatibility-test.md) and [Türkçe](README.tr.md).

## Panel image, configuration upgrades and update notices

The panel thumbnail defaults to your Discord server icon. Set `panel.image-url`
in `bot.yml` to an HTTPS image URL to override it; leave it empty for the server
icon. A server without an icon shows the text and buttons without a thumbnail.
Run `/dydiscord admin reload`, then `/dydiscord admin panel refresh` after edits.

Both `/discord help` and `/dydiscord help` open the same menu. Every displayed
command starts with `/dydiscord`. Both languages keep the `admin` branch. Turkish
help uses `kaldır`; old `ayır`/`ayir`, `yönetim`/`yonetim` and English aliases remain accepted.

Configuration schema 1 is safely upgraded to 2. Missing settings and short English
comments are added without resetting custom values or owner comments. Every changed
file receives a sibling `.backup-...` copy before migration; unsupported future
schemas are rejected. These file copies supplement a full stopped-server backup.

`updates.enabled` checks public Modrinth metadata for `dydiscord` after startup
and every six hours. Only listed, newer versions compatible with your Minecraft
version and server loader are announced. Beta installations can move to beta or
stable; stable installations do not follow prereleases. No JAR is downloaded.
The console and players with `dydiscord.admin.update` receive notices. The project
is currently unpublished (API 404); checks retry quietly instead of claiming the
installation is up to date. Failed requests retry after at least 15 minutes.

Bidirectional text chat, optional join/quit/death embeds and a bounded shutdown
notice are now included. See [chat bridge setup and compatibility](chat-bridge.md).

## Automatic invitation and private account details

A blank `discord.invite-url` asks the connected bot to create one permanent, unlimited-use invite and persist it. It needs Create Invite permission in a suitable text channel; the private console channel is excluded. Missing permission produces console guidance, with attempts limited to once per minute. A manually supplied invite is preserved, including an owner edit made while the network request was pending.

`bot.yml` supports `account.show-integrated-roles: true` and `account.time-zone: UTC` (an IANA time zone, e.g. `Europe/Istanbul`). Roles are displayed only if assigned and explicitly managed by role synchronization. This display does not mean pending role operations succeeded. Private unlink confirmation uses a red Components V2 Container with green Unlink and red Cancel buttons. In-game confirmation tokens are short and retain actor, permission, record and expiry checks.

Admin help starts with topic navigation. Example: `/dydiscord admin help console`; numbered pages remain available. All leaf permission checks and aliases are retained.
