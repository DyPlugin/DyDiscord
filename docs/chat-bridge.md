## Current 1.0.2 work

See [1.0.2 verification](release-1.0.2-verification.md) for this artifact. Earlier counts and live results below remain historical.

# Chat bridge and shutdown notice

Configure `chat` in `bot.yml`. All directions and event switches default to false.
Set `channel-id` to a guild text channel distinct from both panel and
console, enable only the directions/events you want, then run
`/dydiscord admin reload`. Enabling Discord → Minecraft needs Message Content and
`/dydiscord admin bot restart`; the existing prefix/console intent requirement is
unchanged. No member or presence intent is added.

Normal chat is text, not an embed. The default Discord format is
`**Rank** PlayerName **»** Message`; without a rank it is `PlayerName **»** Message`.
LuckPerms 5 active-context cached prefix, then primary group, takes priority. If
unavailable, registered Vault Chat prefix/group is used. `default` is omitted.
No permission provider or external database is required to forward unranked chat.
This reads metadata only; it never synchronizes roles, groups or permissions.

Minecraft receives `[Discord] HighestSuitableRole DisplayName » Message`, using
the guild nickname/effective name. @everyone and managed integration roles are
excluded. Missing roles add no extra space. Discord primary RGB is preserved,
including dark colors; gradient/holographic roles use their first color. An unset
role color falls back to gray. Minecraft uses actual hex-colored text components
instead of displaying literal `&#RRGGBB` codes, without bold or Markdown
format markers. Leaf permissions `dydiscord.chat.send` and `.receive` are children
of the existing player group and can be explicitly denied.

Edit `chat-to-discord` and `chat-to-minecraft` in `discord/messages_en/tr.yml` for
formats. `{rank}` includes safely escaped bold text plus a space when present;
`{role}` adds a space only in Minecraft. All other values remain plain text.
Join/quit/death templates and per-event hex colors are editable. Bot language is
used; player text and the existing death reason are never automatically translated.
Events have no branding footer. Existing links, names and custom translations are
preserved by additive configuration migration and backups.

## Commands and personal visibility

Players use `/dydiscord chat <on|off>` (Turkish: `sohbet aç|kapat`) to show or hide Discord messages for themselves. This persistent preference affects incoming Discord chat only. It does not stop their public Minecraft chat from being forwarded or change join/leave/death notifications. Old outbound/notification preferences are ignored; sound notifications are removed.

Administrators can use `/dyda` or `/dydiscord admin` with the same leaf checks:

- `chat discord-minecraft <on|off>`: Discord → Minecraft.
- `chat minecraft-discord <on|off>`: Minecraft → Discord.
- `chat both <on|off>`: both directions.
- `chat channel <id>`: choose the separate chat channel.

Direction toggles persist in `bot.yml`; they do not change personal visibility. Repeating the current setting reports that it is already set. Apply any requested bot restart when effective intents change. The old pause/resume and announcement command families are removed. Event formats, role rules and advanced options remain in configuration files.

## Privacy and compatibility

The current test server has DyClaim and DyDiscord; DyClaim does not handle chat.
The bridge listens to Paper AsyncChatEvent when available, otherwise Bukkit's
AsyncPlayerChatEvent. Cancelled chat and recipients/viewers restricted below the
public online-player set are rejected. `muted`, `dydiscord-muted`, `vanished`,
`hidden`, `dydiscord-hidden` metadata and Bukkit `canSee` hiding are honored.
Suppressed/blank join, quit and death messages and disabled death gamerules do
not produce event embeds. A hidden state is checked before scheduling and again
before sending, so quit cleanup cannot expose a previously hidden player.
Death forwarding also excludes a hidden killer, a hidden player's name in the
reason, and a scoreboard team with restricted death-message visibility.

Any other plugin registered on legacy/Paper async or sync chat handlers pauses
Minecraft → Discord and writes one setup warning. A public-channel adapter for
that specific moderation/chat system is required before support can be claimed.
This conservative rule can also pause harmless cosmetic chat plugins. Do not
claim all chat/mute/private-channel/vanish plugins work automatically; a plugin
which changes MONITOR events contrary to Bukkit's contract is unsupported.
Discord → Minecraft broadcasts to online players with the receive permission; it
does not impersonate a player, fire another Minecraft chat event, execute commands,
or participate in a third-party plugin's private channel or mute workflow.

All bridge text/embeds and the shutdown message disable allowed mentions and reply
pings. User names, ranks, roles and messages cannot create template Markdown or
Minecraft colors. User newlines, controls and direction/zero-width formatting
are flattened. Only new human Discord text within five seconds is admitted;
message IDs are deduplicated. Bots, webhooks, `dy!`, edits/history and attachments
are ignored. Content is displayed as plain text; nothing is downloaded/executed.

## Bounds and shutdown

`/dyda doctor` (`/dyda kontrol` in Turkish) reports paused Minecraft → Discord
delivery and up to eight distinct chat-plugin names even before a player speaks.
The first blocked chat event warns administrators through the existing throttled
English/Turkish warning path. This does not bypass the public-chat privacy check.
Missing/incompatible Paper chat APIs require a compatibility correction and
restart; a transient exception thrown by an event method drops that event without
permanently disabling subsequent events.

Discord names, roles and referenced-message authors keep literal punctuation such
as underscores. Message bodies use JDA's Markdown sanitizer; identity text is added
separately as literal Minecraft components. Color/control cleanup and no-bold
output still apply. Verification: [DiscordSRV comparison](discordsrv-comparison-2026-10-18.md).

The queues hold at most 128 outbound and 64 inbound messages, each for five
seconds. Short per-user bursts are accepted within bounded per-user and global rate controls. Discord sends are spaced at least half a second apart.
The outbound sender now schedules one coalesced wakeup when work arrives; an idle
queue has no periodic polling delay. Subsequent wakes respect minimum spacing and
channel slowmode, and wake earlier to discard expired messages. Network/API
latency remains part of real delivery time; this does not guarantee a delivery
deadline or superiority over another plugin.
A failed send is not retried; queues are discarded, with a 15-second backoff.
Disconnect, bot restart, settings reload and plugin shutdown invalidate queues.
Minecraft chat and join/quit/death events retain their original settings, policy,
bot session and five-second expiry while awaiting the main thread. A delayed
event cannot acquire a replacement channel/session or a fresh lifetime.
No backlog is replayed after reconnect. Network requests run on a dedicated
worker; Minecraft API and player delivery stay on the main thread.

When Paper reports actual server stopping, DyDiscord attempts a short
`The server is shutting down.` / `Sunucu kapanıyor.` amber embed to the configured
console channel before closing the bot, even if ordinary log relay is disabled.
It contains only the short description, with no title, timestamp or brand footer;
the console channel also needs Embed Links permission.
This is the requested exception to the console-output-only rule; no command
acceptance/rejection replies return. Only the shutdown attempt may wait up to two
seconds; it cannot delay shutdown indefinitely. A bot restart/config reload/manual
plugin disable does not generate this notice. Server families without a reliable
`isStopping()` API skip it; forced termination, crashes, denied permissions and
network failure cannot guarantee delivery. The text never claims the process has
already stopped. Existing bot/executor shutdown bounds still apply.

Current source changes and verification boundaries: [1.0.1 simplification](simplify-1.0.1-verification.md). Historical live checks do not validate this new artifact.
