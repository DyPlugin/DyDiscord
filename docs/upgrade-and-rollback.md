# Upgrade to 1.0.2

No database schema change or relinking is required. Explicit owner false values stay false. Fresh/missing chat directions and events default on, but no channel means no delivery. Console relay defaults off; its command subflag defaults on and existing authorization is required. Empty quoted list entries are ignored. Custom translations remain; only recognized bundled separator text upgrades to ». Active giveaway cards repair in place when enabled and connected. See [fresh verification](release-1.0.2-verification.md).

# Backup, upgrade and rollback

1.0.1 does not change database schema or require relinking. It preserves explicit
disabled values. Newly created configuration enables bot connection and log relay,
but waits for credentials/a configured accessible channel. Remote commands, roles and
rewards remain opt-in. Panel creation becomes automatic once configured; disabling
the panel still keeps private slash account access. New localized diagnostics/help
keys are merged without replacing custom text or duplicating unchanged backups.

The latest 1.0.1 simplification keeps numbered help only and adds `/dyda` as the administration shortcut with identical leaf checks. Player `chat on|off` controls incoming Discord visibility only; old outbound/notify preferences are ignored. Global chat direction and channel commands persist their settings. Announcement commands, startup/scheduled announcements and advanced in-game rule editing are removed. Edit role mappings, console authorization and reward details in `bot.yml`; reload validates files before applying them. Existing links and the panel record remain.

The console channel may be public. Its viewers can read relayed server output; command execution still requires configured trusted authorization, explicit enablement and current Discord access checks. Existing channel visibility is not changed automatically.

Tokens are read only from bot.yml or DYDISCORD_BOT_TOKEN. The former in-game token session and permission are removed in 1.0.2; no token belongs in a command, help example or support report.

New packaged YAML descriptions appear below their setting; owner comments and values remain. Current evidence is recorded in [the simplification report](simplify-1.0.1-verification.md), separately from previous artifact results.

For 1.0.1 choose either the small `DyDiscord-1.0.2.jar` or the bundled
`DyDiscord-1.0.2-standalone.jar`; install only one. Both use the existing
DyDiscord data folder and do not require account relinking. The small package
needs Maven access for initial dependency resolution. Preserve the server's
library cache for offline restarts, or use standalone for offline installation.
Changing package variants does not require deleting configuration or databases.
See [packaging details](packaging.md) for loader caveats and verified results.

Before changing the JAR, stop the Minecraft server and wait for shutdown to finish.
Back up the **entire** `plugins/DyDiscord` directory and the matching JAR. Store
the backup securely: configuration may contain a bot token, and the database,
receipts and audit contain account/user IDs. Do not publish backups.

For a live backup, use SQLite's supported online backup API/tool against a
consistent connection. Do not copy only `dydiscord.db` while a WAL database is
running: pending committed data can reside in `dydiscord.db-wal`. If you cannot
use a supported consistent backup, stop the server before copying all files.
Apply the same rule to the separate `data/boosts.db` reward database and its WAL;
both databases and the provider's reward records matter for a consistent restore.

Install `DyDiscord-1.0.2.jar` and start normally. Preserve custom config,
comments and language files. The current SQLite schema is **3**; `config-version`
in `config.yml` and `bot.yml` is **2**. Future/unsupported versions are refused.
Take the stopped-server backup before the first startup with the updated JAR;
the plugin creates sibling `.backup-...` copies before changing configuration/message
files, but these are not a complete database/JAR backup. Schema 1 adds new settings
and English comments while preserving owner values/comments. Future configuration
schemas are rejected, and a failed partial write restores the original files.

Migration backups are named by the SHA-256 of the original file bytes. The same
original reuses its identical backup rather than generating another timestamped
copy. A mismatching existing backup stops migration. Files needing no change
are not rewritten, preserving their exact bytes. This behavior belongs to the
updated JAR; an older binary cannot acquire it without being replaced.

Reload verifies that all six editable YAML files still match their validated
contents before activation, including files needing no migration. Command-based
edits also reject owner file changes made while Discord access is being checked.
If a file changes during validation, retry reload after finishing the edit;
the last active configuration remains in use.

Existing schema-1 and schema-2 SQLite databases are upgraded automatically in a
transaction. Before migration, `VACUUM INTO` creates a consistent sibling
`dydiscord.db.backup-schema<version>-<id>` database, including committed WAL data.
Backup failure stops startup before migration. This snapshot supplements the
complete stopped-server folder/JAR backup; it does not back up configuration.
The additive migration appends `links.discord_name TEXT NOT NULL DEFAULT ''`
when migrating schema 1, adds managed-role history and durable reconciliation
targets, and advances `schema_version` to 3. Existing UUIDs, Discord IDs, account links,
record versions, timestamps, preferences, panel record and slash ownership data
are preserved. A missing username on an older link is display data, not a missing
account: a localized label is used until the real Discord username is available.
Migration/schema failure rolls back the transaction and fails startup rather than
discarding data. Do not edit the version number to bypass schema validation.

The former shipped `account`, `linked` and Discord `panel-description` templates
are upgraded **in memory only when their text exactly matches a recognized old
English/Turkish default**. An old default panel also receives its language's new
privacy note in memory if that key is absent. Message values remain unchanged on disk; short English
comments can be added with a pre-change sibling backup. Other custom text is preserved when it passes the
current validation. Unknown placeholders are rejected. Private Discord `account` templates may keep
`{name}`, `{discord}` and an optional `{date}`; dates are rendered once in the
configured time zone. Previous owner-written linked-account templates keep their
supported placeholders. Failed reload retains the active snapshot. Missing keys
otherwise use the documented bundled-English fallback and warning.

The previous shipped code length of 8 migrates to 6 on reload/startup, with one
deduplicated pre-change backup. Other explicit custom lengths, including 12,
remain. The accepted range is 6–16. Existing linked accounts need no new link.
Unlink confirmation tokens become six characters while keeping actor, record,
permission and timeout checks. No database schema changes are introduced.

Game auto now uses a saved player preference, then bot language. Bot auto follows
a configured global game language and falls back to English when both are auto.
Existing explicit language selections and disabled feature settings are retained.
New account display options merge without resetting owner translations/comments.
Only recognized obsolete shipped explanatory comments are replaced. Repeated
reload does not multiply comments/backups.

v1.0.0-BETA refuses schema 3. Rollback with the earlier JAR **and its compatible
schema-1/schema-2 data backup**, while the server
is stopped. Retain failed/newer files separately for investigation. Do not mix an
older plugin with a newer schema or delete account data to suppress a failure.
Backups can contain links that were later unlinked; document restoration's privacy
effect to your users and correct restored links through confirmed administration.

Restoring the database, configuration or older JAR does **not** restore Discord
roles. Record the roles and accounts affected by synchronization before rollback.
If appropriate, finish explicitly confirmed cleanup with v1.0.1-BETA while sync
is disabled, or review and repair Discord roles manually. Cleanup is gradual and
can remain pending because of network, hierarchy or permission failures; do not
assume that starting it means all roles were removed. Retain the schema-3 data
and logs separately so unfinished work can be reviewed. Re-enabling sync cancels
or supersedes cleanup rather than allowing it to continue deleting desired roles.
See [role synchronization](role-sync.md).

Boost award/removal receipts have their own **schema 1** database,
`data/boosts.db`. Preserve it even if boost rewards are disabled. An award is
committed as `CLAIMED` before external commands; an interrupted/uncertain attempt
becomes `UNKNOWN` and is not automatically replayed. The same applies to
removal attempts. Do not delete these records to retry a failed provider action.

Restoring an old reward database may erase a receipt for money, site credit or
a rank already granted externally, allowing that period to be awarded again.
Before rollback or restore, disable boost rewards and compare receipts with the
provider's actual records. Keep the newer receipt database separately. Changing
the JAR/database does not undo issued credits, executed commands, Minecraft
ranks or Discord roles. For an old JAR that does not implement boost rewards,
retain the receipt database rather than treating it as unused data. See
[boost reward recovery](boost-rewards.md).

Discord panel state also needs review during rollback. The updated JAR edits the
existing classic panel message into one Components V2 Container. Discord makes
the `IS_COMPONENTS_V2` flag irreversible on that message: an older classic-only
JAR cannot turn it back into a classic embed, even after restoring its database
backup. See [Discord's component rules](https://docs.discord.com/developers/components/using-message-components).
Keep a V2-compatible JAR to continue updating that message. If a classic-only
rollback is essential, the owner must deliberately retire/remove only the bot's
migrated panel in Discord, restore the compatible JAR/data backup and use
`/dydiscord admin panel send` once to create the replacement after the old message
is confirmed missing. Do not erase other messages or repeatedly resend on errors.

Bot token/guild/language/intent changes require controlled bot restart.
The bot does not set a profile activity/status line; an old `bot.activity-text`
key is ignored and does not require restart when edited.
Server `/reload`, third-party hot loaders and restoring a running database file
are not supported procedures. Codes and confirmations are deliberately lost on
restart. Existing account links and preferences survive ordinary restarts.

An ambiguous panel send leaves a durable `pending` marker. Refresh searches a
bounded history for a matching bot-authored stable panel marker. One candidate
can be recovered; none or multiple candidates require investigation. Permissions
errors/timeouts do not mean "message missing". Do not repeatedly run send expecting
new panels, or edit internal database IDs without a consistent backup and review.
Recovery recognizes both the prior `dd:panel:v1` classic footer marker and the
Container's stable numeric ID plus its three account-button custom IDs. The
previous compact layout added a disabled `dd:panel:brand` label before those
three actions. Recovery accepts both that older four-button row and the
three-action V2 row; the current card removes the brand label entirely.
Account button IDs remain unchanged, and the panel does not display its
recovery marker. Refresh updates the existing message to the current layout
without adding a copy.

The bot only removes slash command IDs it recorded as its own. Preserve the
database so language transitions retain that ownership history. An unowned
matching command name stops registration and needs owner review.

## 1.0.0 local stores and version naming

The release is named 1.0.0 (no leading v or BETA); previous release tags remain
historical. Existing links, panel state and languages are preserved. Additive
first-link observation tables are backed up before creation in an existing
schema-3 database. Include `data/rewards.db`, `data/giveaways.db` and
`chat-preferences.db`, in addition to both previous databases and WAL companions,
in a consistent stopped-server backup. These ledgers prevent reward replay and
preserve completed lottery results. Do not restore an old receipt database merely
to repeat commands. Restoring local files never undoes money, site credits,
Minecraft groups, Discord roles or previously announced winners.
