# DyDiscord — Minecraft and Discord Made Simple

**Link your accounts. Share your chat. Connect your community.**

DyDiscord is a Minecraft–Discord integration plugin for survival, SMP and community servers. Connect player accounts, bring conversations together and follow your server through your own Discord bot.

DyDiscord combines private account linking, two-way chat, Minecraft-to-Discord roles, configurable rewards, giveaways and staff tools in one plugin. **Version 1.2.0** runs on Folia from the same JAR and adds cheat checks with a Discord voice call, player reports, required linking, nickname and ban sync and DyHomes logs, while preserving existing account links, custom settings and translations.

---

## New in 1.2.0

**Cheat checks.** `/cheatcheck <player>` (Turkish `/hile-kontrol`) puts a title on the player's screen, a warning in chat and a sound, repeated until the check ends. A linked player gets a Discord message, by DM or in a channel, asking them to join your voice channel. The player stays in place and can only use the commands you allow. Leaving during a check can run console commands such as a ban. Staff see every step in game and in a Discord log.

**Player reports.** `/report <player> <reason>` (`/rapor`) tells online staff and posts the report, with the reported player's location, to a Discord channel. An optional role is mentioned.

**Required linking.** With `linking.require-to-join`, players without a linked account cannot join; the kick screen shows their linking code and your invitation. Operators can be let through.

**Nickname and ban sync.** Linked members can carry their Minecraft name as their Discord nickname. Bans can follow from Minecraft to Discord, from Discord to Minecraft, or both.

**Folia.** The same JAR runs on Bukkit, Spigot, Paper, Purpur and Folia.

**DyHomes logs.** Warps created, moved and deleted, homes made public and, if you want them, every DyHomes teleport.

Version 1.1.0 added the status card, DyClaim and DyRTP logs, PlaceholderAPI, German, Spanish and Russian, MiniMessage and the developer API.

## Choose your server build

| Package | JAR | First startup |
| --- | --- | --- |
| Small | `DyDiscord-1.2.0.jar` | Internet access to resolve required libraries |
| Standalone | `DyDiscord-1.2.0-standalone.jar` | Libraries included; available on GitHub |

Install **one** JAR per server. Both packages provide the same features for **Bukkit, Spigot, Paper, Purpur and Folia**, with **Minecraft 1.20.x–26.x** as the current compatibility range. Stop the server and back up the full DyDiscord folder before replacing the JAR. Existing accounts do not need to be linked again.

Java 17 bytecode; use the runtime required by your server. Proxy platforms are outside the current support claim. Optional integrations must support your selected server too. [Compatibility notes](https://github.com/DyPlugin/DyDiscord/blob/main/docs/compatibility-test.md) · [Upgrade guide](https://github.com/DyPlugin/DyDiscord/blob/main/docs/upgrade-and-rollback.md) · [Downloads](https://github.com/DyPlugin/DyDiscord/releases)

## Why DyDiscord?

**Your bot, your community.** Use your own Discord application and keep account linking, chat and console output in separate channels. The bot runs inside Minecraft; no separate bot process or external database is required.

**Private account management.** Players link through a private Discord form and view their account details privately. Unlinking requires confirmation. The account panel and private slash commands share the same account system.

**Choose what you use.** Enable either chat direction, event messages, role synchronization, rewards or console access as needed. You can turn off the panel and keep command-based account access.

**Five languages.** English, Turkish, German, Spanish and Russian messages are included. Commands use English words, and Turkish also accepts its own command words and ASCII spellings. Help and TAB follow the effective language and permissions. Explicit game/player language choices are respected; otherwise game messages follow the client or bot language.

## Link accounts, your way

Players run `/dydiscord link`, then enter their temporary, single-use code through Discord `/link` or the account panel. The panel keeps **Link Account → Unlink → My Account** in one card, using your server icon or a custom image.

Each Minecraft account links to one Discord account. Linking never grants Minecraft permissions or OP. Account records remain in local SQLite storage across restarts and supported upgrades.

## Share the conversation

Forward public Minecraft chat to Discord, Discord chat to Minecraft, or both. Message formats and optional **join, leave and death embeds** are configurable; player messages are not translated.

Minecraft ranks can come from LuckPerms, with Vault Chat as a display fallback. Discord messages use the member's server display name and highest eligible role, including its primary color. Players can use `/dydiscord chat on|off` to show or hide incoming Discord chat for themselves.

Forwarded messages do not create Discord mention notifications. Bot/webhook messages, edits and attachments are not relayed. Unknown Minecraft chat listeners pause outbound forwarding to protect private or moderated conversations; those systems need a supported integration.

## Keep Discord roles in step

Give linked members configured Discord roles without requiring LuckPerms. With LuckPerms, map real direct, inherited and temporary Minecraft group memberships to Discord role IDs. Temporary ranks are checked while players are offline, and unlinking or changed rules retain the information needed to clean up old managed roles.

Synchronization runs **Minecraft → Discord only**. Only configured roles are managed; unrelated roles stay untouched. The bot needs **Manage Roles** and sufficient role hierarchy. Privileged, integration-managed and console-access roles are blocked from automatic distribution.

## Reward participation

Optional **first-link and boost rewards** run your installed providers' console commands. Use your existing rank, economy or website system to provide temporary ranks, currency, items or site credit. DyDiscord does not bundle those providers, and command dispatch does not prove an external reward was delivered.

Create persistent **button giveaways** with optional linked-account or role requirements. DyDiscord records entries and draws winners; the owner delivers the prizes. Discord server information, player lists, configured addresses and useful links help members find what they need.

## Follow your server from Discord

Relay actual server output to a separate console channel. Optionally authorize trusted Discord roles to run Minecraft console commands. **Log forwarding and command execution have separate switches.** Public console channels are allowed, so anyone with channel access can read the output.

The relay shows real server output without extra technical acceptance/rejection replies. Normal shutdown attempts a short yellow notification before disconnecting; bot restarts and reloads do not send it. Delivery cannot be guaranteed during crashes or network failures.

A separate status card keeps one message up to date with the server state, and an optional log channel follows DyClaim, DyRTP and DyHomes activity.

## Familiar commands

| Command | Purpose |
| --- | --- |
| `/discord` | Show the Discord invitation |
| `/dydiscord link` | Get your private linking code |
| `/dydiscord status` | View your linked account |
| `/dydiscord unlink` | Start unlink confirmation |
| `/dydiscord chat on` or `off` | Show or hide incoming Discord chat |
| `/report <player> <reason>` | Report a player to the staff |
| `/cheatcheck <player> [end]` | Start or end a cheat check (staff) |
| `/dyda help [page]` | Browse permitted administrator commands |
| `/dyda doctor` | Check setup, intents and permissions |
| `/dyda reload` | Apply valid settings and translations |

Turkish examples include `/dydiscord eşle`, `/dydiscord kaldır`, `/dydiscord sohbet aç`, `/rapor`, `/hile-kontrol <oyuncu> [bitir]`, `/dyda yardım` and `/dyda kontrol`. The long `/dydiscord admin` path remains available.

## Get started

1. Install one of the packages and start your server to generate the files.
2. Create your own Discord bot, invite it and configure `token` and `guild-id` in `plugins/DyDiscord/bot.yml`.
3. Reload valid settings, choose the channels you need and run `/dyda doctor`.
4. Enable the required Discord intents for the features you use. Message commands, console input and incoming Discord chat need Message Content Intent; member-event features need Server Members Intent.

Settings and messages include short English comments. Anonymous [bStats](https://bstats.org/plugin/bukkit/DyDiscord/34918) statistics can be turned off with `metrics.enabled: false`. Back up the complete data folder before upgrades; restoring local data does not undo Discord role changes or reward commands already executed.

## Source and support

[Documentation](https://github.com/DyPlugin/DyDiscord#readme) · [Türkçe rehber](https://github.com/DyPlugin/DyDiscord/blob/main/docs/README.tr.md) · [Report an issue](https://github.com/DyPlugin/DyDiscord/issues) · [Discord support](https://discord.gg/dyhub) · [License](https://github.com/DyPlugin/DyDiscord/blob/main/LICENSE)

**By Claww / DyPlugin.**
