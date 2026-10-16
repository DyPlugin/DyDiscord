# Community features — DyDiscord 1.0.0

English is the default; Turkish commands and ASCII aliases remain accepted.
Optional rewards, scheduled announcements, startup notices and giveaways start
**disabled**. The account panel and console-output contract are unchanged.

## Public Discord commands

| Command | Behavior |
| --- | --- |
| `/server`, `/sunucu` | Visible online count, capacity, version and configured custom fields |
| `/players`, `/oyuncular` | Visible online names; at most 100 listed, total count retained |
| `/ip`, `/adres` | Owner-configured Java and optional Bedrock connection addresses |
| `/links`, `/bağlantılar` | Up to 10 named HTTPS links |

The matching `dy!` commands are available when Message Content is enabled.
`config.yml: community` supplies the public addresses, links and extra fields;
no machine/network address is detected. A Bedrock address does not install or
prove a Bedrock bridge. `bot.yml: community.information-channel-ids` optionally
restricts these four information commands to selected regular text channels.
Account interactions remain private and are not moved into a public channel.

## Player chat controls

Use `/dydiscord chat inbound on|off`, `outbound on|off`, or `notify on|off`.
Turkish examples: `/dydiscord sohbet gelen kapat`, `/dydiscord sohbet giden kapat`,
`/dydiscord sohbet bildirim aç`. Defaults after successful loading are inbound
and outbound enabled, notifications disabled. Changes are stored in
`chat-preferences.db`; storage failures withhold bridging for that player.

Name notifications are local sounds, require opt-in, match whole Minecraft
names and have a 15-second per-player cooldown. They do not ping Discord.
Replies include only the safely formatted cached reply-author name, not the
referenced body; uncached references have no context. Long text is shortened at
a readable boundary. Slowmode pacing never extends the 5-second queue lifetime.
Bots, webhooks, private/cancelled/muted/hidden chats and unknown chat providers
retain their existing protections. Player opt-out is checked again at delivery.

Admins can temporarily pause a direction with
`/dydiscord admin chat pause inbound|outbound|both` and resume with `resume`.
Pausing discards affected pending traffic; resuming admits only fresh messages.
Runtime pauses survive configuration/bot restarts but reset on plugin restart.

## Notices and scheduled announcements

Configure `bot.yml: community-tools.channel-id` separately from the account
panel and private console. `templates` maps names to single-line text, up to20
entries of 800 characters. Shipped maintenance/restart defaults follow bot language;
custom owner text is not automatically translated.

- `admin announce preview <template>` previews Minecraft and Discord escaping.
- `admin announce send <template>` sends to both surfaces.
- `admin announce set <template> <text...>` edits a template; this has a separate permission.
- `admin maintenance` and `admin restartnotice` use the corresponding templates.
  The restart notice does not restart the server.
- `admin announcements pause <minutes>` suspends schedules for 1–10080 minutes.
- `admin announcements resume` resumes schedules.

Example: `community-tools.announcements.schedules` accepts
`[{template: maintenance, target: both, every-seconds: 3600}]`.
Targets are `both`, `minecraft`, `discord`; intervals 60–86400 seconds, at most 20
schedules. Schedules are paced at most once per second, with a 30-second freshness
window; stale/disconnected deliveries are dropped, never replayed in a burst.
The optional startup embed is eligible only after a genuine server startup,
for at most 120 seconds. Bot restart and reload do not create a new startup notice.
Neither startup nor shutdown delivery is guaranteed after connection/permission
failure or process termination. The existing yellow shutdown embed remains the
explicit private-console lifecycle exception; technical command replies stay off.

## Private linking and reward previews

See [first-link rewards](link-rewards.md). Optional private welcome text is
appended only after permanent linking. Reward receipt or command dispatch does
not prove that an economy/website provider granted a benefit.
`admin rewards preview <uuid>` lists configured first-link commands without
executing them; `admin roles preview <uuid>` computes role additions/removals.
These are separate from actually synchronizing roles or granting a reward.

## Setup, health and validation

- `admin setup` shows existing doctor checks and a setup guide. Never paste a
  bot token into a command, public chat or a support request.
- `admin report` writes `reports/diagnostic.txt` using an explicit safe-field
  allowlist; raw config, tokens, user IDs, commands and account data are excluded.
- `admin config validate` validates settings and translations without applying
  changes, creating missing files or migrating them.
- `admin translations validate` performs the same safe validation and lists
  missing translation keys (up to20 paths) using English fallback.
- `admin status`/`doctor` distinguish connection, storage, chat, role and reward
  availability. Enabled configuration alone is not proof of successful delivery.

`updates.notify` selects `console`, `players` or `both`; player notices require
`dydiscord.admin.update`. `updates.include-beta` opts into future beta releases.
Historical 1.0.0–1.0.2 betas are superseded by the renamed 1.0.0 line and are never
recommended to stable installations; ordinary semantic precedence is preserved.

## Giveaways and permissions

See [giveaways](giveaways.md) for entry buttons, persistent deadlines, conditions,
limits, manual finish/cancel and same-message result recovery. Prizes are delivered
by the owner; giveaways do not execute reward commands or transfer money.

Each administration operation has its own `dydiscord.admin.*` leaf in
`plugin.yml`. Help/TAB are filtered by those leaves; explicit deny still applies
to OP. Chat controls use `dydiscord.chat.preferences`, inherited by the player group.
The administration branch stays `admin` in both languages.

All new options apply on validated reload unless the existing bot/intent rules
require restart. Existing custom translations/comments and account links survive.
New local receipt/preference/giveaway files must be included in stopped-server
backups; restoring local data does not undo Discord roles or external rewards.

## Accepted feature index

1 players: CommunityInfo; 2 first-link rewards: reward service + primary observations;
3 maintenance/restart notice: Notices; 4 safe report: Support;
5 shared announcements: Notices; 6 extended server info: ServerInfo;
7 private welcome: Bot/DyDiscord; 8 inbound preference: ChatPreferences;
9 name sounds: ChatNotifications; 10 guided setup: CommunityCommands;
11 configured IP: CommunityInfo; 12 HTTPS links: CommunityInfo;
13 startup embed: ServerLoadEvent/Notices; 14 schedules: ToolsSettings/Notices;
15 chat pause: ChatBridge direction epochs; 16 information channels: Bot;
17 dry run: first-link command preview + existing role preview;
18 translations: Support.validate; 19 outbound preference: ChatPreferences;
20 reply author: ChatText; 21 readable truncation: ChatText;
22 slowmode: Bot/ChatBridge; 23 timed schedule pause: Notices;
24 preview: shared notice template; 25 Bedrock address: CommunitySettings;
26 custom fields: ServerInfo; 27 feature health: Bot.diagnostics;
28 read-only config check: Support; 29 update audience/beta option: ToolsSettings/Modrinth;
30 separate announcement edit/send leaves: Definition/plugin.yml;
31 later requested giveaways: GiveawayService/Store/JdaGiveawayHost.

No second account panel, automatic economy provider, web service, telemetry,
Discord-to-Minecraft permission sync or production install is part of this work.

API references checked on2026-10-16: [JDA button events](https://docs.jda.wiki/net/dv8tion/jda/api/events/interaction/component/ButtonInteractionEvent.html)
and [Spigot server load lifecycle](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/server/ServerLoadEvent.html).
The implementation separately checks STARTUP versus reload. These references
explain API behavior and are not live interaction test evidence.
