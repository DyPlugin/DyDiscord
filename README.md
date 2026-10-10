# DyDiscord — Minecraft and Discord, Connected

**Link your accounts. Share your chat. Manage your server.**

DyDiscord connects a Minecraft server to your own Discord bot. Link player
accounts through a private code form, share conversations in both directions,
and optionally give trusted administrators access to a private console channel.

**Version v1.0.0-BETA** includes English and Turkish, local SQLite storage and
one persistent account panel. The bot runs inside the plugin; no separate bot
service or external database is required. By **Claww / DyPlugin**.

[Source code](https://github.com/DyPlugin/DyDiscord) · [Downloads](https://github.com/DyPlugin/DyDiscord/releases) · [Issues](https://github.com/DyPlugin/DyDiscord/issues)

## Features

### Private account linking

- Generate a short-lived code in-game and enter it through the Discord panel.
- Link one Minecraft account to one Discord account.
- View your account privately and confirm before removing a link.
- Receive account messages using usernames rather than raw IDs or dates.
- Keep the same panel after restarts, with your server icon or a custom image.

### Chat and event notifications

- Enable Minecraft to Discord and Discord to Minecraft independently.
- Use a chat channel separate from account linking and console access.
- Show Minecraft ranks through LuckPerms, with Vault Chat as a fallback.
- Show Discord display names and the highest eligible role in Minecraft.
- Configure message formats and optional join, leave and death embeds.
- Send a short yellow shutdown embed during a normal server shutdown.
- Prevent forwarded text from creating Discord mention notifications.

Chat works without a rank provider. Unknown Minecraft chat listeners pause the
outbound bridge to avoid exposing private or moderated conversations; they need
an explicitly supported adapter. Attachments, bot/webhook messages, edits and
message history are not forwarded. Player text is not translated.

### Console access with clear controls

Relay actual server output to a private Discord channel and allow selected
roles to run Minecraft console commands. Log forwarding and command execution
are configured separately and start disabled. Commands receive no additional
acceptance or rejection reply; the channel shows the server's real output.
The normal-shutdown embed is the documented lifecycle exception.

Console roles have full Minecraft console power. Use only trusted server
administrators. Bounded queues, mention suppression, known-secret masking and
local audit records support the relay; network outages can still lose output.

### English and Turkish

With `lang: auto`, Minecraft uses the player's saved preference, then client
language, falling back to English. A global `en` or `tr` setting takes priority.
Discord has its own independent bot language. Help and TAB follow the effective
language while both languages and documented ASCII aliases remain accepted.

The administration branch stays `admin` in both languages. Both `/discord help`
and `/dydiscord help` open the same menu; displayed paths use `/dydiscord`.

## Commands

| Command | Purpose |
| --- | --- |
| `/dydiscord` | Show the configured Discord invitation |
| `/dydiscord help` | Show commands available to you |
| `/dydiscord link` | Generate your private linking code |
| `/dydiscord status` | View your linked accounts |
| `/dydiscord unlink` | Start unlink confirmation |
| `/dydiscord confirm <token>` | Confirm your pending action |
| `/dydiscord cancel` | Cancel your pending action |
| `/dydiscord language <auto\|en\|tr>` | Choose your Minecraft language |
| `/dydiscord admin help` | Show permitted administration commands |
| `/dydiscord admin reload` | Reload settings and messages |
| `/dydiscord admin bot enable\|disable\|restart` | Manage the bot connection |
| `/dydiscord admin panel send` / `panel refresh` | Create or update the account panel |
| `/dydiscord admin status` / `doctor` | Inspect connection and setup health |

`/discord` uses the same command router. Discord offers guild slash commands
and `dy!` entry points for help, linking, account information, unlinking and
status. Account details are shown through private interactions.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `dydiscord.player` | Everyone | Player command group |
| `dydiscord.admin` | OP | Player and administration groups |
| `dydiscord.admin.update` | Through admin group | Receive update notifications |

Individual command permissions can be granted or denied through your permission
manager. Help, TAB and command execution respect those permissions. Linking
does not grant OP, Minecraft permissions or Discord roles.

## Setup and upgrades

1. Stop the server and place `DyDiscord-v1.0.0-BETA.jar` in `plugins/`.
2. Start the server to generate `plugins/DyDiscord/`.
3. Create your own bot in the Discord Developer Portal and invite it with the
   `bot` and `applications.commands` scopes. Do not grant it Administrator.
4. Enter the token and quoted guild ID locally in `bot.yml`. Never share the token.
5. Reload with `/dydiscord admin reload`, then `/dydiscord admin bot enable`.
6. Select the account channel with `/dydiscord admin panel channel <id>`, then
   run `/dydiscord admin panel send`. Use `admin doctor` to check the setup.

The bot, chat bridge, event notifications and console features start disabled.
Enable only the features you want. Message Content intent is needed for plain
text chat, prefix commands and console input; slash and account interactions
can operate without it. Bot language and token changes require a bot restart.

Back up the complete data folder and old JAR with the server stopped before
upgrading. Supported configuration migrations preserve custom values,
translations and comments, adding missing defaults with file backups. Accounts,
language preferences and the panel record remain in the local database.

## Update notifications

DyDiscord checks Modrinth for newer compatible public versions and notifies the
server console and permitted administrators. It does not download or replace
your JAR. The checker uses the project identifier `dydiscord`; until the project
and compatible versions are published there, checks retry without claiming
that the installed version is current. GitHub-only releases do not trigger it.

## Compatibility

Compiled for **Java 17 bytecode** against **Spigot API 1.20.4**. Use the Java
runtime required by your server. Earlier Beta revisions were checked on
Paper 26.3 / Java 25; those checks do not verify this exact release or every
Minecraft version. Folia, proxies and third-party hot loaders are outside the
current support claim.

Offline-mode linking is available, but the server's login system must
authenticate players and restrict every command alias before login. No login
plugin API integration is included. Human linking/unlinking, visual clients and
selected real-provider integrations still need acceptance testing.

## License and source

DyDiscord uses **GPL v3 with additional attribution, non-commercial distribution
and public-source requirements**. Copyright (C) 2026 **Claww / DyPlugin**.
Free distribution is permitted; selling builds or requiring payment for access
is prohibited by the project terms. Forks and modifications must share their
source under the same terms. See [LICENSE](https://github.com/DyPlugin/DyDiscord/blob/main/LICENSE).
Dependencies retain their independent licenses and bundled notices.
Contributions, bug reports and feature requests are welcome.

[Detailed setup and permissions](docs/setup.md) · [Chat bridge](docs/chat-bridge.md)
· [Security](docs/security.md) · [Upgrading](docs/upgrade-and-rollback.md)
· [Compatibility](docs/compatibility-test.md) · [Türkçe](docs/README.tr.md)

## Build

Run `gradlew.bat clean build` on Windows or `./gradlew clean build` elsewhere.
Use a JDK supported by Gradle 9.1.0. Dependency locks and verification metadata
are included. The bundled distribution is
`build/libs/DyDiscord-v1.0.0-BETA.jar`; the `plain` JAR is not a distribution.
