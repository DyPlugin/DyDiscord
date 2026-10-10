# Backup, upgrade and rollback

Before changing the JAR, stop the Minecraft server and wait for shutdown to finish.
Back up the **entire** `plugins/DyDiscord` directory and the matching JAR. Store
the backup securely: configuration may contain a bot token, and the database,
receipts and audit contain account/user IDs. Do not publish backups.

For a live backup, use SQLite's supported online backup API/tool against a
consistent connection. Do not copy only `dydiscord.db` while a WAL database is
running: pending committed data can reside in `dydiscord.db-wal`. If you cannot
use a supported consistent backup, stop the server before copying all files.

Install `DyDiscord-v1.0.0-BETA.jar` and start normally. Preserve custom config,
comments and language files. The current SQLite schema is **2**; `config-version`
in `config.yml` and `bot.yml` is **2**. Future/unsupported versions are refused.
Take the stopped-server backup before the first startup with the updated JAR;
the plugin creates sibling `.backup-...` copies before changing configuration/message
files, but these are not a complete database/JAR backup. Schema 1 adds new settings
and English comments while preserving owner values/comments. Future configuration
schemas are rejected, and a failed partial write restores the original files.

Reload verifies that all six editable YAML files still match their validated
contents before activation, including files needing no migration. Command-based
edits also reject owner file changes made while Discord access is being checked.
If a file changes during validation, retry reload after finishing the edit;
the last active configuration remains in use.

An existing schema-1 SQLite database is upgraded automatically in a transaction.
The additive migration appends `links.discord_name TEXT NOT NULL DEFAULT ''`
and advances `schema_version` to 2. Existing UUIDs, Discord IDs, account links,
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
current validation. Incompatible placeholder sets, including a custom account
template that still requires `{date}`, are rejected; update that custom template
to the new placeholders instead of deleting all translations. `account` and
`linked` now use `{name}` and `{discord}`. Failed reload keeps the active snapshot.
Missing keys otherwise use the documented bundled-English fallback and warning.

New setups default to 12-character linking codes. Existing explicit
`linking.code-length` values, including 8, are retained. Change that setting
deliberately if an existing installation should use 12 characters.

Rollback with the earlier JAR **and its compatible data backup**, while the server
is stopped. Retain failed/newer files separately for investigation. Do not mix an
older plugin with a newer schema or delete account data to suppress a failure.
Backups can contain links that were later unlinked; document restoration's privacy
effect to your users and correct restored links through confirmed administration.

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
