# Minecraft → Discord role synchronization

v1.0.1-BETA adds optional role synchronization inside the existing bot. It starts
disabled. A verified link can receive an independent linked-account role and
roles selected from actual LuckPerms group membership. Discord roles never give
Minecraft permissions, groups or OP. Vault prefixes, primary-group labels and
Discord role names are not membership evidence.

## Configure and preview

Use a dedicated ordinary Discord role below the bot's highest role. Give the bot
Manage Roles; Administrator is unnecessary. Never use an administrative,
moderation or console-access role for automatic synchronization. The bot checks
the configured guild, current role catalog, permissions, hierarchy and managed
status. Unsafe roles are blocked for both addition and removal. A role that later
becomes unsafe needs deliberate owner repair; the worker does not bypass that
restriction to finish cleanup.

Example `bot.yml` section, with illustrative role IDs replaced by your own:

```yaml
roles:
  enabled: false
  linked-role-ids: ["123456789012345678"]
  groups:
    - group: vip
      role-ids: ["223456789012345678"]
      priority: 10
  context:
    server: survival
  selection: all
  recheck-seconds: 300
  member-events: true
```

Install LuckPerms for group rules. The adapter queries actual effective group
membership, including inheritance and temporary group nodes, under the fixed
`roles.context` values. It does not borrow an online player's world/server context.
With `all`, matching rules combine their roles. With `highest`, only the highest
matching priority applies; ties combine their roles. Shared roles stay until no
matching rule requires them. If LuckPerms is missing or a query fails, group
membership is unknown: existing configured group roles are preserved rather than
treated as a negative result. The independent linked-account role still works.

Load the settings with `/dydiscord admin reload`, then preview a verified linked
account using `/dydiscord admin roles preview <uuid>`. Preview works while role
sync is disabled and does not change Discord roles. Once the preview and role
policy are suitable, explicitly set `roles.enabled: true`, reload and restart the
bot if requested. No role command toggles this setting silently.

Configuration accepts at most 100 current unique managed role IDs, 100 rules,
100 IDs per rule and 16 fixed context entries. Group names and context keys use
lowercase letters, numbers, `_`, `.` or `-`, up to 64 characters. Context values
are nonblank strings up to 128 characters; priorities range from -1,000,000 to
1,000,000. Rechecks range from 30 to 3,600 seconds. Guild IDs and role IDs must be
quoted Discord snowflakes. Console-role conflicts and `@everyone` are rejected
even while role synchronization is disabled.

## Commands and permissions

All paths below follow `/dydiscord admin roles`. `/discord` uses the same router.
Every leaf defaults to false and is included in `dydiscord.admin`; grant individual
leaves when appropriate. Help, TAB, execution and confirmation use those leaves.

| Path | Permission after `dydiscord.admin.roles.` |
| --- | --- |
| `status` | `status` |
| `linked list` | `linked.list` |
| `linked add <id>` | `linked.add` |
| `linked remove <id>` | `linked.remove` |
| `groups list` | `groups.list` |
| `groups add <group> <id> <priority>` | `groups.add` |
| `groups remove <group> <id>` | `groups.remove` |
| `preview <uuid>` | `preview` |
| `sync <uuid>` | `sync` |
| `sync all` | `sync.all` |
| `cleanup preview` | `cleanup.preview` |
| `cleanup confirm <token>` | `cleanup.confirm` |

Adding a role to an existing group combines that group's IDs and applies the
explicit priority to its rule. Removing one ID preserves its other IDs and
priority; an empty rule is removed. The dedicated worker validates candidate
settings and rechecks the sender's permission and player availability immediately
before committing the file. Rule edits do not reset unrelated configuration.

Turkish paths use `admin roller`, with `durum`, `eşlenen`, `gruplar`, `önizle`,
`eşitle`, `tümü`, `temizle`, `onayla` and the existing `liste/ekle/çıkar` words.
Their documented ASCII spellings are accepted, including `eslenen`, `onizle`,
`esitle`, `tumu` and `cikar`. The `admin` display remains unchanged.

## Events, pending work and limits

Account link/unlink changes, LuckPerms recalculation/events, known member events
and periodic rechecks request synchronization. The default period is 300 seconds.
Temporary memberships are also rechecked at their expiry. Settings, account
version, provider invalidation and bot session are checked again before writes;
work validated against an older state cannot apply as current work.

`roles.member-events: true` requests the privileged Server Members intent only
when role sync is enabled. Grant that intent in Developer Portal if you want
member events. If it is unavailable, status reports the limitation and periodic
REST reconciliation remains active. Changing the effective intent requires bot
restart. Role reconciliation does not require Message Content intent.

The worker has 64 queued tasks and at most 256 pending account/member signals.
Overflow requests a sweep instead of collecting unlimited signals. Accounts are
read in pages of 20; each one-second tick requests at most two role-write endpoints
for one member. Bulk work proceeds gradually. Database target rows and managed
role history and sweep progress survive restart; history is capped at 2,048 IDs
per guild. Unresolved accounts do not block later group-change sweeps. Network
errors remain pending, not confirmed departures. Confirmed absent members count
as successful no-ops and can be reconsidered on a later join/recheck. Jobs from
another guild remain pending until their original guild is restored and do not
consume the current guild's processing ticks. Changing the configured guild
does not authorize operations in the old one.

`roles status` distinguishes enabled/disabled, provider availability, scanning,
retrying or failures needing attention, and successful/pending/failed accounts.
Queued or unresolved work is not successful completion. Unsafe-role and connection
diagnostics give short corrective explanations rather than exposing raw tokens
or internal exception details.

## Disable and clean up

Disabling sync stops ordinary role changes; it does not automatically remove
roles already given. To deliberately remove the plugin's managed roles, first
set `roles.enabled: false` and reload. Run `roles cleanup preview` to review the
candidate account count and managed role IDs, then `roles cleanup confirm <token>`
within 30 seconds. The token belongs to the actor and the previewed settings and
engine generation. It is single-use. Changed settings, expiration or lost leaf
permission prevents confirmation.

Confirmed cleanup creates durable gradual jobs while the feature is disabled.
It removes only this plugin's current or remembered managed IDs, leaving other
roles alone. Re-enabling synchronization cancels or supersedes cleanup; cleanup
cannot keep removing active desired roles. Failures remain visible and can need
manual owner repair. Database rollback does not undo Discord role changes.

The initial role-update clean build passed 409 automated tests, including the role fixtures and
existing behavior regressions. An isolated Paper 26.3-143 / Java 25.0.4.1 /
LuckPerms 5.5.87 provider harness passed all 10 actual LuckPerms checks against
the exact final JAR and exited normally. An independent Shadow rebuild produced
the same SHA-256. See [final build verification](roles-verification-20261012.md)
and [provider evidence and its limits](role-live-evidence-20261012.md).

The subsequent [comprehensive quality review](quality-review-20261012.md)
supersedes that artifact and adds explicit blocking of voice mute/deafen/move
and managing other users' nicknames. It also resolves unfinished worker results
at shutdown. Existing role configuration and account data are preserved.

No live Discord role endpoints, human linking/cleanup, or production setup were
exercised. Use an owner-authorized isolated test setup for first enablement,
hierarchy/critical-role changes, expiry, outages, member-intent fallback,
unlink/relink and cleanup before relying on those behaviors in production.
