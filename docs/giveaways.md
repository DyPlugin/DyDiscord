# Giveaways

Giveaways are optional and disabled by default. They post a Components V2 Container with Join and Leave buttons in a
configured Discord text channel and edit that same message with the result.
DyDiscord selects winners; the server owner supplies the prize manually. No
Minecraft console commands, economy grants, direct messages or role rewards are
executed by a giveaway.

In `bot.yml`, configure a channel separate from the account panel and private
console, then enable the feature and reload:

```yaml
giveaways:
  enabled: false
  channel-id: ""
  require-linked: false
  allowed-role-ids: []
```

The bot needs View Channel, Send Messages, Embed Links and Read Message History.
Giveaways use member REST lookups and do not require a member or message-content
intent. An empty role list allows all guild members; otherwise membership of at
least one configured role is required. At most 20 role IDs may be configured.
`require-linked` additionally requires an existing Minecraft account link.
Requirements are saved when a giveaway is created; changes apply to new ones.

Use `/dydiscord admin giveaway start <seconds> <winners> <prize...>` to open one.
For example, `/dydiscord admin giveaway start 3600 2 Weekend prize` runs for one
hour with up to two winners. The command returns an internal giveaway identifier.
Use `giveaway list`, `giveaway end <id>` or `giveaway cancel <id>` under the same
admin path. Each operation has its own leaf permission:
`dydiscord.admin.giveaway.start`, `.list`, `.end` and `.cancel`.

Duration is 60 seconds through 30 days, winner count 1 through 20 and prize text
1 through 200 characters. Control characters are rejected. At most 20 active or
unresolved initial publications and 10,000 distinct entries per giveaway are
accepted. A repeated button click keeps the existing entry and never adds a
second chance. Bots and nonmembers cannot enter. Entry replies are private.

Current guild membership, bot status, saved role requirements and account-link
requirements are checked at entry and again during the draw. A cryptographic
Fisher–Yates shuffle provides an unbiased order without replacement. Candidates
are checked in small batches; if fewer eligible members remain than requested,
all eligible members win. A transport error pauses the draw and retries the same
candidate with a delay up to five minutes; an uncertain REST response does not
silently disqualify somebody. An authoritative unknown-member result means that
member has left and is ineligible. A large ineligible entry list or Discord rate
limits can delay the result. There is no fixed completion-time guarantee.

The result contains only the prize and winning Discord display names. It does
not mention members or expose Minecraft account details, UUIDs or Discord IDs.
Text is escaped and all allowed mentions are disabled. A displayed prize does
not certify that the owner has delivered it.

State and entries live in the separate SQLite file `data/giveaways.db`. Back it
up with the other plugin data while the server is stopped (or use a consistent
SQLite backup including its WAL). Winner rows and the completed draw state are
committed atomically before publishing the result. Restart resumes overdue draws
and pending edits; it does not reroll a committed result. Retried edits target
the same message and are safe after an ambiguous Discord response. Completed
records remain available in the database; the admin list shows up to 100 records, with active giveaways and drafts first.

A failed or interrupted *initial* publication is different: Discord may have
created its message before the response was lost. Such a record stays `DRAFT`
and is never automatically sent again. Inspect the channel and admin list,
remove any orphaned public message manually, cancel the draft by its identifier,
then start a replacement if desired. This deliberately avoids duplicate entry
messages. Deleted result messages and missing channel permissions require owner
intervention; winners remain recorded and no replacement message is created.

Disabling the feature or changing its channel/guild pauses affected draws and
result publication. Restore the original configuration to resume them; use
cancel to abandon an active giveaway in the current guild. Cancellation while
disabled is saved, and its message is updated when that channel is enabled
again. Old-session callbacks cannot enter or complete a new-session giveaway.

Offline regression tests cover limits, duplicate entries, membership failures,
session changes, restart recovery and the atomic result ledger. New human
Discord entry flows and client rendering still require live acceptance; earlier
beta live results are not evidence for this feature.


A queued start request is bound to the admitted bot session and policy. It is
not moved into a replacement connection or channel. Queued work expires after
15 seconds; a start also carries its deadline into the Discord send check.
A timeout can still leave an ambiguous DRAFT if a network request was already
in flight. Check the draw list and channel before retrying; do not blindly replay.

Pending-result recovery is keyset-paged within the active guild/channel; old
missing messages cannot permanently hide newer results in that same channel.
Each pass reads at most60 records and retry metadata is capped at128 records.
Very large pending histories can delay a due draw while pages rotate; this is
not a guarantee that every Discord result is delivered immediately at its deadline.

## Leaving a giveaway

Leave opens a private red Container asking for confirmation. The red Leave and green Cancel buttons are bound to the initiating member and expire after 60 seconds; cancellation keeps the entry. The giveaway, original panel, session, current membership and active configuration are checked again before removal. Existing active cards are updated at the saved message ID; unchanged cards are not repeatedly edited.
