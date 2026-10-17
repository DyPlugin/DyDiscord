# DyDiscord 1.0.1 verification — 2026-10-16

The 2026-10-17 update remains version 1.0.1. See [its verification addendum](polish-1.0.1-verification.md); earlier counts and hashes below belong to the earlier artifact.

## Artifacts

| Distribution | Bytes | SHA-256 |
| --- | ---: | --- |
| `DyDiscord-1.0.1.jar` | 2124829 | `1fbaf3d14514e557a809161f099804dd98afaeaa608405cad225de5dc6aa71f5` |
| `DyDiscord-1.0.1-standalone.jar` | 24043442 | `28145784706347ae76773727fc3b2b6e45b3607c0b4177074d95006961920b42` |

Both packages have plugin.yml version 1.0.1 and Java 17 bytecode (major 61).
An independent task rerun reproduced both SHA-256 values. The standalone retains
20 SQLite native entries, four service files and 14 dependency license/source
notice entries. Archive checks found no runtime databases, local test directories,
logs or migration backups. Install only one distribution; never the plain JAR.

## Changes and regression evidence

The reported first panel was not automatically created because reconciliation
returned when there was no existing panel. It now creates the first panel once
configured and connected, including reload/reconnect and channel setup. Existing
panel identity, private interactions, Components V2 structure and button order
are preserved. Disabled settings stay disabled.

Console output no longer requires panel-specific history/embed permissions.
Fresh private-channel checks still run before relay/dispatch. Unrelated inactive
channels no longer block another channel's setup. Offline channel settings can
be staged; runtime operations remain fail-closed. Empty console-channel settings
do not schedule failing permission checks. Setup failures now carry localized
repair guidance through wrapped configuration-validation failures.

Administrator help has eight commands per page after permission filtering, with
localized page instructions and numeric completion. Invalid page numbers and
explicit leaf denials remain enforced. Discord slash/message help share a
three-section embed; other normal prefix replies use embeds without account data,
footers or allowed mentions. Modal responses remain private and first-response
modal behavior is unchanged.

Discord primary RGB is preserved in Minecraft, including dark colors. Gradient
and holographic roles use only the primary color; unset color falls back to gray.
Untrusted message text cannot introduce hex/legacy formatting. Intent denial
(4014) explains Message Content and Server Members requirements, enters a new
session generation and reconnects without privileged intents. Old callbacks are
expired. A failed existing connection still reports when restart is necessary.

First-run bot/log switches are enabled but require credentials/a verified private
channel before side effects. Remote command execution, rewards and role features
remain explicit opt-ins. Existing false values are not overwritten. No database
schema change, account relinking, dependency update or authentication relaxation.

Malformed YAML and file-access/migration failures identify a bundled filename;
YAML line/column is logged when available, without input values. Failed startup
no longer indefinitely claims it is still initializing. The original remote
server's first IOException cannot be assigned an exact file/cause from its
redacted log alone; its original YAML files were not provided. The controlled
malformed-YAML reproduction is evidence for the new diagnostic/failure behavior,
not proof that arbitrary disk/configuration errors can be automatically repaired.

## Automated verification

- `gradlew clean test build`: **643 tests, zero failures/errors/skips**.
- `gradlew smallJar shadowJar --rerun-tasks`: both hashes reproduced.
- New regressions were observed failing before the corresponding fixes: first
  panel creation, permission-filtered paging, dark RGB, filename diagnostics,
  prefix help embeds, wrapped setup guidance, restart notice during failed
  setup, offline channel staging and denied-intent session generation.
- Existing configuration/backup, account/modal, role/reward, console policy,
  chat privacy, queue/lifecycle and artifact tests were retained.
- Fixtures were updated for intentional first-run defaults and exact RGB; the
  console idle-relay test now supplies a valid channel and a separate regression
  asserts no network work is scheduled with a blank channel.
- Read-only independent code review found two setup regressions; both gained
  failing tests and were fixed. The follow-up found no further important issues.

## Actual Purpur / Discord checks

Isolated **Purpur 26.3 build 2651 / Java 25.0.4.1**, bound to loopback with
online-mode=true. The owner's running server and original data were not changed.
Updates were disabled in the test configuration; no Modrinth download was used.
Only the previously authorized test Discord guild was used.

The exact small artifact above passed **21 checks**: fresh startup/defaults,
real command routing for two bounded help pages, malformed reload retaining the
last valid settings and original invalid bytes, safe file diagnostics, unchanged
reload backup deduplication, exact dark RGB and both language help renderers;
then actual JDA connection, automatic first Discord panel after channel setup,
same-message enable/refresh, one V2 panel in channel history, public-console
rejection, private-console setup, real vanilla `say` output arriving in Discord,
Discord accepting the structured help embed, command-only mode, panel re-enable
and bot restart preserving the panel ID.

The exact standalone artifact passed **8 checks** on Purpur: correct version,
ready state, relocated JDA JSON, EN/TR help renderers, SQLite native query,
attached log appender and real second administrator help page. A separate
intentional malformed-startup run passed **3 checks**, including the actual
failed-startup reply instead of “initializing”.

Human Discord commands were not fabricated: the console delivery test used a
real Minecraft console command and fetched its real Discord output. The help
embed was sent through the renderer/API, not a human slash interaction. Controlled
administration routing used a permission-bearing test CommandSender on the actual
server thread; this does not replace a real player's visual/input acceptance test.

The first test driver used an unsupported sender dispatch and was corrected.
Two cleanup runs retained TextChannel objects from before a bot restart and hit
a stopped JDA executor. Those harness failures are not counted as passing checks.
Cleanup was completed using verified test channel IDs/names/guild, and newly
test-owned slash commands were removed; the pre-existing owned slash definitions
were restored. The driver was corrected to resolve fresh channel objects.

An additional clean rerun completed its ten server-only checks, then failed
Discord DNS resolution (UnknownHostException) before login. An earlier network
attempt also timed out. Neither is counted as a successful live rerun. Public
cross-client checks demonstrated intermittent endpoint reachability; no speculative
transport replacement or DNS/authentication workaround was added to the plugin.

Local evidence: `.local/release-101/final-build.log`, `artifact-before-rebuild.json`,
`reproducible-build.log`, `purpur-final/probe-final-live.log`,
`purpur-standalone/standalone-final.log`,
`purpur-standalone/invalid-startup-final.log`, cleanup logs and the final clean
retry log. Local evidence/runtime files are excluded from distribution.

## Remaining acceptance

- Human modal linking/unlinking, both Minecraft/Discord identities and permissions.
- A human Discord message executing an authorized console command end to end;
  real output delivery was verified, authorization/races use automated tests.
- A real human Discord chat message, role RGB/gradient appearance in a Minecraft
  client, mobile/desktop Discord appearance and visual screenshots.
- Actual Developer Portal intent toggling; denied-intent fallback was simulated
  deterministically in tests instead of changing the owner's application settings.
- Real temporary-role expiry/provider rewards/giveaway participation for this hash;
  automated existing regressions passed, older live evidence is not reused.
- Running Java 17 and the complete Bukkit/Spigot/Paper/Purpur version matrix;
  this run used Purpur 26.3 and checked Java 17 compilation/bytecode.

No production installation, Git initialization, commit, push or release upload
was performed in this task. Existing license and token-entry policy are preserved.

## API references

- [Discord privileged intents and 4014](https://docs.discord.com/developers/events/gateway#privileged-intents).
- [Discord role colors](https://docs.discord.com/developers/topics/permissions#role-colors-object).
- [JDA Role.getColors](https://docs.jda.wiki/net/dv8tion/jda/api/entities/Role.html#getColors()).
- [JDA primary/gradient/holographic colors](https://docs.jda.wiki/net/dv8tion/jda/api/entities/RoleColors.html).
