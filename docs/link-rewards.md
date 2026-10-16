# First-link rewards and private welcome

`bot.yml` contains optional `link-rewards.enabled: false` and `commands: []`.
A new permanent account link can run the owner's configured Minecraft console
commands once. Both the Minecraft UUID and Discord account are permanently
reserved: unlinking, relinking, changing either counterpart, restarting, or
turning the feature off and on cannot reset eligibility. Links made while the
feature is disabled are reserved without rewards. Accounts already linked when
this release first starts are reserved without retroactive rewards.

Commands accept only `{player}` and `{uuid}`, must contain at least one target,
and are limited to ten commands of 256 characters each. Control characters,
unknown placeholders, and direct dangerous primitives such as `op` and `execute`
are rejected. A Minecraft account name is checked against its conservative name
grammar before substitution. Commands belong to the trusted owner and execute
with the full server console authority; this validation is not a sandbox for
other plugins. No Discord roles, account IDs, or user-supplied command arguments
are interpolated. No economy, website credit provider, or credentials are bundled.

The new ledger is `data/rewards.db`, SQLite schema 1, independent of the account
and boost databases. The ledger commits both permanent identity tombstones and
`CLAIMED` before scheduling any command. Dispatch runs on the Minecraft main
thread after an asynchronous account snapshot. Immediately before each command,
it checks the immutable reward configuration, bot session, current link
generation, and a five-second deadline without database access on main.
At most 64 dispatch jobs wait in the queue. Overflow consumes the durable claim
without executing it. No backlog is replayed at startup.

`DISPATCHED` means Bukkit accepted every configured command. It does not prove
that a provider awarded money, rank, or credit. A partial command list is never
rolled back. Failed, timed-out, stale, or interrupted work is `UNKNOWN`; a restart
changes unfinished `CLAIMED` receipts to `UNKNOWN`. These claims are never retried
automatically. This deliberately prefers a missed reward over duplicate external
side effects. Compare provider records manually before any correction.

`link-welcome.minecraft` and `link-welcome.discord` both default to `false`.
The optional welcome follows a successful permanent link and stays private to
the matching Minecraft player or the Discord modal's ephemeral reply. It does
not claim that a reward was granted. No general channel or automatic DM is used.
Reward preview renders the same templates and checks ledger eligibility without
claiming an identity or dispatching a command; its result is only a preview.

Back up the fully stopped server's complete data directory, including
`dydiscord.db`, `boosts.db`, `rewards.db` and their WAL/SHM files if present. Never
restore an older reward ledger alone to retry a reward: it can erase consumed
identities and duplicate real external side effects. Deleting or losing
`rewards.db` loses command-attempt receipts. The primary database preserves the
first-link observations, but cannot reconstruct missing command-attempt outcomes.
A local rollback cannot undo commands, provider credits, ranks, or Discord roles.
Disable rewards and compare provider records before recovery. Never edit status
rows or remove identity tombstones to repair a presumed failure.


The primary `data/dydiscord.db` schema 3 now also contains additive
`link_reward_seen` and `link_reward_origins` tables. Both account identities are
observed in the same transaction as every permanent link, whether optional
rewards are disabled, unavailable, or damaged. A new eligible origin is created
only when both identities were previously unseen. Unlink never removes these
facts. Failed or revoked link commits roll them back together with the link.
Existing linked accounts are seeded as observations without eligible origins
when the tables are first introduced. Incomplete or malformed observation tables
are rejected rather than reconstructed from incomplete history.

The production reward host checks this primary origin before claiming in the
optional ledger. Thus a link made and removed during a reward-ledger outage
cannot become a new first link after the ledger recovers or the plugin restarts.
Back up both databases consistently: the primary facts prevent counterpart
farming while the separate receipt ledger records uncertain external attempts.
Never delete either database or restore the primary observation tables from an
older copy to retry a reward. Older plugin binaries do not maintain these new
observations; keep rewards disabled after such a rollback until history has
been reconciled. A rollback cannot recreate observations of links made and
removed while older binaries were running.

Before adding the observation tables to an existing schema-3 account database,
startup writes one consistent `VACUUM INTO` backup named
`dydiscord.db.backup-reward-observations-<unique-id>`. It includes committed WAL
data and precedes all observation-table mutations. Backup failure aborts the
upgrade; existing data is preserved. Once both tables exist, later startups do
not create another observation-upgrade backup. Schema-1/2 upgrades retain their
existing consistent pre-migration backup behavior.

Historical limitation: links already removed before installing the first-link
observation feature cannot be reconstructed from the active-account database.
The anti-repeat guarantee covers the migrated active-account baseline and all
subsequent committed observations; it is not a reconstruction of deleted history.
