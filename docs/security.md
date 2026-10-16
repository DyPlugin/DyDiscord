# Security and privacy — 1.0.0

The security boundary is a trusted Minecraft server, one Discord application and
one configured guild. Code generation and account linking allow both online-mode
and offline-mode servers. The server-provided UUID and current session identify
the Minecraft side; DyDiscord does not independently prove offline account ownership.
An offline server's login system must authenticate players and block every
DyDiscord command root/alias until login completes. DyDiscord does not query a
login plugin's authentication API. Proxy forwarding and Floodgate identities
are not verified. Changing authentication mode/UUID assignment does not migrate links.

## Secrets

Keep `bot.yml` readable only by the server owner/service account. Optional
`DYDISCORD_BOT_TOKEN` takes priority. Status reports only file/environment and
requested/applied state. There is no token command. Do not share a token with
another bot process. Rotate an exposed token in the Developer Portal and restart
the bot. Never include tokens, databases or audit logs in public support reports.

Relay removes known active tokens, Authorization values, webhook/interaction
token URL tails, code-shaped strings, ANSI/colors, code fences and active mentions.
Third-party secret formats are not universally detectable. Allowed mentions are
empty. Bot/JDA/relay logger paths are excluded to prevent feedback loops.

## Linking and unlinking

SecureRandom codes have 8 unambiguous characters by default. Memory stores a
salted SHA-256 digest, never a persisted plaintext code. Expiry is 300 seconds;
generation cooldown is 30 seconds; one code per UUID. Code memory is bounded to
4,096 entries. New codes revoke the old code, logout revokes pending codes, and
restart invalidates all codes and confirmations. Five failed attempts per user
per five minutes and 1,000 global failed attempts per five minutes limit guessing.
These counters are bounded. Codes do not reveal an account's UUID or Discord ID.

Discord identity comes from the actual guild interaction. The owner's current
Minecraft session and `dydiscord.link` are checked at redemption. SQLite unique
constraints on UUID and Discord ID, plus a shared domain lock, prevent double
consumption/transfer. Only a durable insert is acknowledged as success.
Display names are not identity. Guild departure does not delete the local link.
Subsequent Discord operations require fresh guild membership.

Account details are only shown to the querying account owner. Prefix messages
direct to the shared panel and are public; they do not expose account records.
The panel remains common to everyone and is not replaced by private details.

Unlink confirmation is actor/target-version/session-generation bound, expires
after 30 seconds and is consumed once. The leaf permission is checked again for
Minecraft operations. A stale record, logout, permission loss, restart or a
second click cannot reuse it. Administrator unlink requires a bounded reason
and a durable local audit entry. There is no generic other-account lookup.

## Console authority

Console relay and execution each default off. The channel must differ from the
linking channel and pass conservative private visibility checks. Unapproved
roles and non-bot member allow overrides are rejected. Discord Administrators
and the guild owner inherently bypass channel denies; do not treat channel
privacy as protection from the guild's owners/Administrators.

Remote authority is allowed Administrator (if enabled) OR an allowed **role ID**,
with current guild membership and channel visibility. Role names are never used
as identity. REST membership and current channel/role permission data are fetched
again before dispatch. Validation failure, timeout or rate limit fails closed.
Policies are validated before activation; removing a role that still sees the
channel requires correcting its Discord visibility first.

When the bot is disconnected, local reload may stage initial credentials, channel
IDs and console settings without requiring an already-running bot. This is not a
claim of validated Discord access: panel/relay sends and console dispatch still
perform live channel/member/role checks before use, and fail closed on failure.
Changes to policies of a usable connected bot retain pre-apply validation.

An authorized operator has full Minecraft console authority: `op`, permissions
and all installed plugin commands. This is not a limited moderator console.
DyDiscord calls Bukkit `dispatchCommand` with the actual console sender on the
main thread. It does not invoke shells, PowerShell, processes or an RCON bridge.

Only new human ordinary messages in the exact guild/text channel are accepted.
Threads, DMs, bots, webhooks, edited/system messages, attachments, control/format
characters, code blocks, multiline input, empty/oversized commands and prefix bot
commands are rejected. An optional leading slash is removed; args are not translated.
Receipts identify each message durably and block replay. Limits are one command
per user per two seconds, twenty per channel per minute, and fifty pending tasks.

Durable receipt and audit acceptance precede dispatch. Missing audit stops
mandatory operations. Access is rechecked after acceptance. Session/policy
changes invalidate pending tasks. A command accepted before a crash can be
`unknown`, and is never replayed automatically. A response loss is not proof of
failure; `dispatched` is not proof of the requested in-game result.

## Stored data and retention

SQLite stores UUID, Discord user ID, latest Minecraft name, UTC linking Instant,
record version, UUID language preference, single panel state, owned guild slash
IDs and console message receipts. Link codes/modal contexts/confirmations are
temporary memory. Preferences use UUID rather than player name.

Unlink deletes the active link row, not history in backups. Audit stores actor,
guild/channel/message, UTC time, safe command root, authority reason and
dispatch/rejection result. Console arguments are deliberately omitted. Admin
unlink reasons are bounded and redacted. Audit is rotated at 30 days and 20 MiB;
console receipts are limited to 20,000 and thirty days. A full receipt store
rejects new acceptance. Discord channel history retention is the owner's policy.

Database corruption/unknown schema does not trigger a destructive empty reset.
Detected persistence failure stops mutations. Restore a consistent compatible
backup; never delete the database merely to make startup appear successful.

## Operational limits

The I/O executor, code/modal/confirmation/attempt collections, console admission
and relay queues have explicit bounds. Pending I/O tasks expire before start;
REST actions have deadlines; main-thread dispatch checks expire instead of
running late. Relay buffers at most 2,000 lines/1 MiB and reports overflow.
JDA handles its REST 429 timing; permission verification honors Discord's
`retry_after` by failing closed during that interval. Relay transport failures
use bounded exponential backoff and jitter, with local warning throttling.

Only active-session callbacks can mutate domain state. Shutdown stops acceptance,
revokes temporary work, detaches the appender, stops the bot/executors and closes
SQLite. Logs from before enable/after disable and delivery during crashes/outages
are not guaranteed. Neither zero lag nor complete secret detection is claimed.

Automated checks cover negative cases, races and packaging. Real Discord
authorization changes, gateway errors, Bukkit runtime and LuckPerms are still
unverified; see [compatibility evidence](compatibility-test.md). No absolute
security or error-free guarantee is made.

## 2026-10-08 audit follow-up

Commit-time owner checks, chat reload admission/delivery context, interaction
limits and audit capacity/text handling were strengthened with regressions.
See the [dated audit](security-audit-2026-10-08.md) for reproduced findings,
dependency checks and remaining acceptance work. This build was not deployed;
earlier live results must not be presented as live verification of these fixes.
