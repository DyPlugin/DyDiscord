# DyDiscord — Minecraft and Discord Made Simple

**Link your accounts. Connect your chat. Manage your server.**

Connect your Minecraft server and Discord community through your own Discord bot. DyDiscord brings private account linking, two-way chat, Minecraft-to-Discord roles, optional rewards and giveaways together, with English and Turkish support.

**Minecraft:** 1.20.x–26.x  
**Platforms:** Paper, Purpur, Bukkit and Spigot  
**Current release:** 1.0.1 · By Claww / DyPlugin

## Your accounts, linked privately

Generate a six-character code with `/dydiscord link` and enter it through the Discord panel or `/link`. Codes are temporary and single-use, with a click-to-copy hint in Minecraft and a separate clickable Discord invitation.

The panel keeps **Link Account → Unlink → My Account** in one card. Account details show both usernames, a readable link date and any assigned managed roles. Replies stay private, and unlinking requires confirmation. Prefer commands? Disable the panel while keeping private slash account commands available.

## One conversation, two communities

Use a separate Discord chat channel and enable either direction independently. Public Minecraft chat can include a LuckPerms rank, with Vault Chat as a display fallback. Discord messages use the member's server display name and highest eligible role, including its primary color.

Players can use `/dydiscord chat on|off` to show or hide incoming Discord messages for themselves. Join, leave and death embeds are optional. Forwarded messages cannot create Discord mention notifications. Cancelled/private/hidden chats are protected; unknown chat systems require an explicitly supported integration.

## Minecraft groups, Discord roles

Give linked players configured Discord roles even without LuckPerms. With LuckPerms, map real direct, inherited and temporary group membership to Discord role IDs. Temporary ranks are checked while players are offline, and old managed roles are cleaned up when links or rules change.

Synchronization is **Minecraft → Discord only**. Discord roles do not grant Minecraft groups, permissions or OP. Only configured roles are managed; privileged, integration-managed and console-access roles are blocked. The bot needs Manage Roles and sufficient role hierarchy, not Administrator.

## Rewards and giveaways

Optionally reward linked Discord boosters or a first successful link through your installed plugin's console commands. Your own provider can handle temporary ranks, game currency or site credit; no economy or website-credit service is bundled.

Run persistent Discord button giveaways with optional account/role conditions. DyDiscord draws the winners and keeps the result; the server owner delivers the prize.

## Your server console in Discord

Relay actual server output to a separate console channel and optionally let explicitly authorized Discord roles run Minecraft console commands. Log forwarding and command execution have separate switches. Public console channels are allowed; anyone who can view the channel can read its output, while command execution still requires authorization.

The console shows real server output without extra technical acceptance/rejection replies. Normal shutdown attempts a short yellow notification before disconnecting. Bot restarts and reloads do not create that shutdown notice.

## Clear commands, English and Turkish

Use `/discord` for the invitation and `/dydiscord help` for player commands. `/dyda` is the short administration root, using the same permissions as `/dydiscord admin`.

| Command | Purpose |
| --- | --- |
| `/dydiscord link` | Get your private linking code |
| `/dydiscord status` | View your linked account |
| `/dydiscord unlink` | Start unlink confirmation |
| `/dydiscord chat on|off` | Show or hide incoming Discord chat |
| `/dyda help [page]` | Open permitted administration commands |
| `/dyda doctor` | Check setup and permissions |
| `/dyda reload` | Apply valid settings and translations |

Turkish commands and ASCII spellings are also accepted. Help and TAB show the effective language and permitted actions. Explicit game/player language choices are respected; otherwise Minecraft follows the bot language. Advanced role, console authorization and reward options stay in the commented YAML files.

## Get started

1. Install **DyDiscord-1.0.1.jar** and start the server. The small JAR uses Spigot/Paper's library resolver; first installation requires internet access. For an offline installation, use **DyDiscord-1.0.1-standalone.jar** from GitHub instead. Install only one.
2. Create and invite your own Discord bot. Configure `token` and `guild-id` in `plugins/DyDiscord/bot.yml`, then run `/dyda reload`. An explicitly disabled bot stays disabled until enabled.
3. Choose the panel channel with `/dyda panel channel <id>`. The first panel is sent automatically once the bot is connected. Configure separate chat and console channels as needed.
4. Use `/dyda doctor` to check permissions and intents. Message commands, console input and Discord-to-Minecraft chat need Message Content Intent. Member-event features need Server Members Intent.

Authorized players can also use the no-argument `/dyda bot token` entry flow. Local file/environment configuration is preferred because client history and third-party loggers cannot be guaranteed.

**Upgrading:** Stop the server, back up the complete DyDiscord folder and replace the JAR. Keep your account databases and settings; no account relinking is required. Version 1.0.1 removes the old announcement system and simplifies administration commands. Existing custom settings and translations are preserved where supported.

The plugin targets Java 17 bytecode; use the Java version required by your Minecraft server. Folia and proxy platforms are outside the current support claim. Compatibility and live verification limits are documented on GitHub.

[Full documentation](https://github.com/DyPlugin/DyDiscord#readme) · [Türkçe rehber](https://github.com/DyPlugin/DyDiscord/blob/main/docs/README.tr.md) · [Report an issue](https://github.com/DyPlugin/DyDiscord/issues) · [Discord support](https://discord.gg/dyhub) · [Downloads](https://github.com/DyPlugin/DyDiscord/releases)

**Claww / DyPlugin** · [License](https://github.com/DyPlugin/DyDiscord/blob/main/LICENSE)
