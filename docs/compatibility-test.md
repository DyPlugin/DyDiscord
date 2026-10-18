## Current 1.0.2 work

See [1.0.2 verification](release-1.0.2-verification.md) for this artifact. Earlier counts and live results below remain historical.

# Latest 1.0.1 simplification — 2026-10-17

Current source changes cover numbered permission-filtered help and `/dyda`, incoming-only personal chat visibility, explicit global chat direction controls, removed announcements and advanced editing commands, protected no-argument player token entry, account presentation and bounded administrator warnings. Console channel visibility may be public; current command authorization and access checks remain. Normal shutdown still attempts its short yellow embed.

See [the current simplification verification](simplify-1.0.1-verification.md) for exact checks and remaining limits. No test counts, hashes, live acceptance or expanded platform support are asserted here for the new artifact. All reports and counts below describe their earlier artifacts.

---

# Earlier 1.0.1 verification — 2026-10-16

The 2026-10-17 update remains version 1.0.1. See [its verification addendum](polish-1.0.1-verification.md); earlier counts and hashes below belong to the earlier artifact.

643 automated tests passed. The exact small package passed 21 actual Purpur 26.3 /
test Discord checks; standalone passed eight server checks and three intentional
startup-failure checks. Network failures and harness cleanup failures are reported
separately, not treated as passing runs. See [the current report](release-1.0.1-verification.md).

---

# Historical 1.0.0 verification — 2026-10-16

The final clean test/build passed 621 tests, zero failures/errors/skips.
The exact small JAR passed five packaging and six release probes on isolated
Paper 26.3-143 / Java 25.0.4.1 with bot disabled. New language keys migrated with
zero missing translations. Published-source rebuild hashes match both artifacts.
See [the 1.0.0 report](release-1.0.0-verification.md) for hashes, scope and unrun
human Discord/provider checks. All older sections below are historical evidence
for their original artifacts, including unpublished packaging experiments.

---
# Compatibility and verification — 2026-10-08

## Current small/standalone distributions — 2026-10-15

The final v1.0.2-BETA test/build passed 497 tests without failures, errors or
skips. The small default is 2,005,344 bytes; the offline standalone alternative
is 23,923,445 bytes. Both hashes reproduced in independent packaging reruns.
The exact small JAR passed fresh dependency resolution and cached restart on
isolated Paper 26.3-143 / Java 25.0.4.1, with five runtime probes on each start.
The cache restart used an unreachable Maven mirror and logged zero downloads.
The bot was disabled: this is not new live Discord acceptance evidence.
Effective SQLite was 3.49.1; server library resolution can override declared
versions and add Maven transitives. Current hashes and limitations are recorded
in [packaging details](packaging.md). The following full-only results are historical.

## v1.0.2-BETA packaging verification — 2026-10-15

The local packaging update passed a fresh clean build and 494 automatic tests
with zero failures, errors or skips. The bundled JAR is 23,923,279 bytes, down
from 26,963,839 bytes. All SQLite natives, service descriptors, dependency notices
and the complete Trove source archive are preserved. Only unused collection
classes were removed; application class payloads are unchanged.

The independent Shadow rebuild matched SHA-256
`7ec05d0100742f696a36503d0694e4f372f96acd7ce2897921afc86c7af3380d`.
New isolated artifact checks exercise JDA cache operations without development
dependencies, byte-for-byte native/source preservation and the size budget.
No new live Minecraft/Discord session was run. Previous sections describe their
own historical artifacts. See [packaging details](packaging.md).

---

Status: **local Beta artifact; live acceptance incomplete**. This report belongs
to DyDiscord. No DyClaim test counts, server builds or results are reused.

## Reproducible build

| Field | Evidence |
| --- | --- |
| Build machine | Windows, Eclipse Adoptium JDK 21.0.12.1+1-LTS (Gradle); live server uses Oracle 25.0.4.1 |
| Build tool | Gradle Wrapper 9.1.0, ZIP checksum pinned |
| API baseline | Spigot API 1.20.4-R0.1-SNAPSHOT; dependency bytes verified |
| Bytecode | Java 17 (`--release 17`); all base bundled classes checked, maximum major 61 |
| Artifact | `build/libs/DyDiscord-v1.0.0-BETA.jar` |
| SHA-256 | `aa056298fa05110a116ab1e4727f3b0fd6faf04a060c05c1923da1094b8b3614` |
| Current command | `gradlew.bat test build` — passed; the earlier 213-case revision also passed `clean build` |
| Determinism | second `shadowJar --rerun-tasks` produced the identical SHA-256 |
| Automatic tests | 251 cases; final XML/HTML reports under `build/test-results/test` and `build/reports/tests/test` |
| JAR size | 26,818,417 bytes |

The shaded artifact's isolated class loader loaded JDA and YAML and executed a
native SQLite in-memory create/insert/query using only the JAR plus JDK modules.
This proves local packaging on the build JDK; it is not a Java 17 runtime/server
test. Base class bytecode inspection is distinct from behavioral compatibility.
Multi-release dependency classes are left in their upstream versioned paths.

## Automatic scope

| Suite | Cases | Result |
| --- | ---: | --- |
| ArtifactTest | 3 | passed |
| BotInteractionTest | 4 | passed |
| BotTest | 9 | passed |
| CommandTest | 74 | passed |
| ConfigurationLifecycleTest | 10 | passed |
| ConfigurationTest | 36 | passed |
| ConsoleTest | 34 | passed |
| LinkingCommandTest | 4 | passed |
| LinkingPresentationTest | 6 | passed |
| LinkingTest | 24 | passed |
| MainThreadTest | 2 | passed |
| MessageUpgradeTest | 10 | passed |
| PanelTest | 7 | passed |
| PanelViewTest | 9 | passed |
| StoreMigrationTest | 4 | passed |
| UpdateCheckerTest | 5 | passed |
| UpgradeAndUpdateTest | 10 | passed |
| Total | 251 | zero failures/errors/skips |

Fixtures cover domain/API failure boundaries and real local SQLite operations;
they do not connect to Discord. Permission tests exercise Bukkit PermissibleBase
and registered group children, including OP plus explicit leaf false. LuckPerms
is not replaced by those tests. Code/confirmation concurrency, precommit rollback,
audit failure, malformed configuration and panel ambiguity have negative checks.
Scheduler fixtures also prove a timed-out task cannot execute later when the
scheduler recovers. A future database schema is rejected without deleting links.
See [acceptance mapping](acceptance.md) for exact limits.

Before the offline-mode change, all 155 existing cases were explicitly rerun and
passed. After the offline change, a clean build passed 159 cases; the 2026-10-07 regression suite had 183 cases; the 2026-10-08 suite passes 213 cases. New command fixtures run
the real router, execution, linking service and SQLite in both online-mode settings
through English/Turkish paths and shortcuts. They verify committed server-provided
UUIDs, consumed codes, rejection after permission loss, and revocation on logout.
The Bukkit session/bot availability are simulated; these do not validate a real
login plugin, Minecraft server or live Discord interaction.

## Dependency and package inspection

The final runtime resolution was submitted to [OSV](https://osv.dev/) by public
Maven name/version, without project credentials or user data. Sixteen runtime
artifacts were checked; zero returned known advisories at this run. This is a
point-in-time advisory lookup, not a guarantee that all vulnerabilities are known.
Queries/results are saved locally under `build/inspection` after the final build.

An initial lookup found high-severity Jackson 2.22.2 advisories. JDA's transitive
Jackson was overridden to 2.22.3, which contains the published fixes. Compile/test
Log4j was moved to 2.26.1; the runtime server supplies its own logging library and
must maintain that version independently. No Log4j or Bukkit classes are bundled.

The JAR contains empty example tokens, both languages, service metadata, relocated
runtime dependencies, SQLite native resources, third-party notices and Trove
sources/license. No local username path, database, log, test class, world, real
Discord ID or token is intentionally packaged. Source test snowflakes are synthetic
and test sources do not enter the artifact. No `Runtime.exec`, ProcessBuilder,
System.out/err replacement, isOp bypass or administration-group OR shortcut is
present in implementation sources.

## Candidate and real environment matrix

Mojang's official version manifest was checked on 2026-10-07: current stable
Minecraft **26.3**, released 2026-09-15, declares Java **25**. This target was tested on the exact Paper build below.
The selected minimum candidate remains Spigot 1.20.4 / Java 17. The existing Paper build was supplied by the user; Spigot 1.20.4 remains untested.

| Environment/scenario | Exact build/client | Result |
| --- | --- | --- |
| Spigot 1.20.4 + Java 17 actual server | not provided/run | NOT TESTED |
| Paper 26.3 + Java 25 | 26.3-143-main@ff3655a / Oracle 25.0.4.1+1-LTS-5 | LIVE PASSED for recorded scope |
| Other Paper builds / Purpur | not provided/run | NOT TESTED |
| EN/TR locale change, TAB/help, clickable components | actual protocol 777 localhost client | LIVE PASSED; visual rendering untested |
| Real test application/guild/token and permissions | owner-authorized CTest test guild | LIVE PASSED: connection, channel validation, relay |
| Desktop/mobile, light/dark panel/modal/ephemeral layout | no live Discord clients | NOT TESTED |
| Two players/two Discord accounts, race/unlink/relink | local domain fixtures only | LIVE NOT TESTED |
| Bot language tr→en→tr slash/panel transition | actual guild REST + gateway | LIVE PASSED; same panel ID |
| Private console and policy commands | actual guild channels/roles | LIVE PASSED for staging/privacy/administration; user screenshots and matching audit show earlier human dispatch, current agent relay test uses local console |
| Gateway denied Message Content / restart | actual 4014 fallback, then enabled Message Content | LIVE PASSED; 429/5xx outage tests remain untested |
| LuckPerms, OP + explicit false / group and leaf grants | LuckPerms 5.5.87 on actual Paper player | LIVE PASSED for recorded matrix |
| Load, network outage, tick/memory/thread impact | no measured server load | NOT TESTED |
| Server restart/shutdown | actual graceful server stops and starts | LIVE PASSED; hot plugin reload unsupported |
| Offline-mode code generation and roots/shortcuts | actual localhost Paper player; Unicode/ASCII paths | LIVE PASSED; Discord redemption remains untested |
| Offline login-system authentication/command blocking | no login plugin API integration or real login server | NOT TESTED |
| Folia, proxy identity, Floodgate/Bedrock | out of current support scope | NOT CLAIMED |

The user authorized the existing Minecraft server and dedicated Discord test
application/guild. Live panel and relay messages were sent. No public release
or upload was performed. Exact command outcomes, environment changes and remaining
human-client flows are in [live verification](live-verification.md).
NOT TESTED entries remain limits; automated fixtures do not turn them into passes.
Project licensing remains undecided for public distribution.

The subsequent compact-panel revision passes 218 cases. Five added cases cover
recognized numbered-panel migration (EN/TR) and the actual console receive path
for successful, rejected and duplicate messages. Successful dispatch emits no
technical Discord acknowledgment; receipts and audit remain durable. At that revision,
real REST confirmed one combined text section and the disabled brand followed by
three active account buttons in the same row. See live-verification.md.


The preceding presentation revision passed 220 cases and reproduced its own
artifact hash. Paragraph spacing, absence of the brand label and confirmation
wording, the divider before three actions, and old-template upgrade are checked
in EN/TR. Live REST verifies the same panel message contains the requested layout.

The console-output revision passed 227 cases, retained in the button-order update.
Seven added receive-path
cases prove denied authority, changed policy, executor exception without replay,
bot-prefix/malformed input and full queue neither dispatch unauthorized work nor
send a generated Discord response. Unknown dispatch records `not-found`; policy
revocation remains `rejected`. These fixtures use a simulated Bukkit/JDA boundary
with real SQLite/audit, not fresh human Discord messages. Current live scope is
recorded in live-verification.md.

The latest panel order is Link Account → Unlink → My Account. Existing EN/TR
serialization checks verify the reordered buttons keep their own labels, styles
and custom IDs while the explanatory paragraphs retain their earlier order.

## 2026-10-08 — natural Turkish, configuration and version notices

The final revision passed **251 automatic cases** with zero failures/errors/skips.
A second `shadowJar --rerun-tasks` reproduced the same SHA-256. Schema 1→2,
custom values/comments, backups, future-schema refusal, stale preview and partial
write rollback are covered. Update fixtures cover BETA→stable, semantic ordering,
Minecraft/loader compatibility, listed releases, stable/prerelease policy, denied
notification permissions, disabled checks, 404 and stale-session shutdown.

The stopped Paper 26.3-143 / Java 25 server received the final JAR after a full
DyDiscord directory/JAR backup at
a local stopped-server backup.
Startup migrated the six editable YAML files with sibling pre-write backups,
kept current settings/data and connected CTest to `test`. The later restart
loaded the new localized state/language keys without missing-key warnings.

Both `/discord help` and `/dydiscord help` produced the same Turkish menu with
all command paths beginning `/dydiscord`. Administrative help, ASCII `yonetim`,
global language, status, reload and panel refresh ran in the real console.
The final status displayed `Bağlı` and `Türkçe`. Unicode command tokens were
verified in fixtures; this Windows terminal input session did not accept the
Unicode spelling, so that terminal spelling is not claimed as live verified.

Discord REST verified the unchanged panel ID the recorded panel message, V2 flag
32768, three buttons in link/unlink/account order and localized `/kaldır` slash
registration. The guild has no icon: the default panel correctly uses text,
divider and buttons without a thumbnail. A temporary HTTPS custom image appeared
on the same panel and was then removed; `image-url` was restored to blank.
The original global Minecraft language `auto` was restored. Actual `list` and
marked `me` server output reached the Discord console channel.

The real Modrinth request returned 404; startup logged one explanation and
scheduled a later retry. No existing release is claimed, and an actual future
published-version notification remains unverified outside fixtures. No download
or publication occurred. Human modal success/unlink, authenticated login-system
integration and desktop/mobile/theme rendering remain pending. The final server
was left running with the new JAR. Evidence: `build/inspection/live/config-update-*`
and `custom-image-20261008.json` (no token values).

## Latest chat/shutdown build — 2026-10-08

`gradlew.bat test build` passed 272 cases with zero failures/errors/skips, including
the existing 251-case suite. A second `shadowJar --rerun-tasks` reproduced:

```text
f1200662c26c4bffe0e9fe3774f295ae2f16d91e0e7d684d43287831f4eea4e9
```

Artifact: `DyDiscord-v1.0.0-BETA.jar`, 26,846,754 bytes. Maximum base class major
61 is unchanged; provider test stubs are absent from the distributed JAR.
The installed server copy matches this hash. Build baseline remains Spigot
1.20.4 / Java release 17; actual runtime verification remains Paper 26.3-143 /
Java 25. No new broad version compatibility claim is made.

Actual vanilla-protocol clients and Discord REST verified two-way chat, no-rank
and LuckPerms 5.5.87 prefix presentation, no bot loop, mention suppression,
join/quit/death reason, direction-off and completed reload behavior. Normal
server stopping was separately exercised for the lifecycle notice. The final
notice uses a short amber embed. See `live-verification.md` for exact delivery
results and remaining limits. Bukkit legacy chat, Vault, hidden/private/muted
plugin scenarios, role selection and failed connections are fixture coverage.

Temporary offline/profile settings and rank JAR were restored/removed. Chat
defaults are off with a blank channel, and the existing panel/account/console
configuration is preserved. New configuration is additive and custom message
templates remain intact; the data schema stays at 2.

## Autonomous audit build — 2026-10-08

The pre-audit 272-case suite was explicitly rerun. The audit adds 13 cases; the
final `gradlew.bat test build` passes 285 with zero failures/errors/skips.
Regressions demonstrate permission/session revocation at commit, both queued
reload directions, incoming preprocessing, interaction admission and audit
limits/text. Artifact evidence and SHA-256 are in
`build/inspection/audit-artifact-20261008.json` and the JAR checksum file.
No live server or Discord operation was run for this build, and it was not
deployed. Earlier Paper 26.3 evidence remains attached to the previous JAR.
See [audit findings and limits](security-audit-2026-10-08.md).

## Independent review build — 2026-10-09

Fresh `gradlew.bat clean test build` passed 322 cases, with zero failures/errors/
skips. Runtime: Gradle 9.1.0 / Eclipse Adoptium 21.0.12.1 / Windows 11 amd64.
Source/API target remains Java 17 / Spigot 1.20.4; class-major and shaded packaging
were checked separately. This is no claim of a live Java/platform matrix.
The Shadow JAR checksum, reproducibility and package evidence are recorded in
`build/inspection/round-artifact-20261009.json` and its `.jar.sha256` companion.
No live server/bot operation or deployment occurred. See
[review findings and remaining checks](bug-review-2026-10-09.md).

## Published Beta artifact — 2026-10-10

The owner-authorized public release includes the supplied DyClaim-style license
notice adapted to DyDiscord, Claww / DyPlugin; third-party terms stay independent.
Fresh clean test/build passed 322 tests with no failures/errors/skips. A second
Shadow build matched SHA-256 2e94b4645e051720910ab182cc07e20d59b06b086bc9fae781251cdb16bd6ccf.
All 15 tracked resource inputs match the JAR after plugin version expansion.
Published JAR size: 26865858 bytes. The GitHub download and
SHA256SUMS.txt were checked against the local artifact. Source commit: ae9ba4b142df661d7f42542467cedb9d5156d0c6.
No live server/bot/client acceptance was performed during publication.

## Role integration — 2026-10-12, v1.0.1-BETA

Fresh `gradlew.bat clean test build` passed 409 cases with zero failures, errors
or skips. Java source/API target remains release 17 / Spigot 1.20.4. Shaded
packaging retains 20 SQLite native libraries, service providers and license
notices; optional LuckPerms API is test-only and is not bundled.

An independent `shadowJar --rerun-tasks` reproduced SHA-256
`a773393129228b06318678287148ef3c4f4187c3192b5ba5b0234b3c4ed3155a`.
The distribution is `build/libs/DyDiscord-v1.0.1-BETA.jar` (26,927,610 bytes).

That exact JAR was loaded in an isolated localhost Paper 26.3-143 / Oracle
Java 25.0.4.1 server with LuckPerms 5.5.87. Ten real provider checks passed:
offline user loading, inherited/contextual groups, offline temporary expiry,
event invalidation and subscription disposal. Final-artifact startup and
normal shutdown succeeded; bot/role sync were disabled and no Discord endpoint
or Minecraft player login occurred. The isolated server exited normally and
its listener was closed. No owner-server files or running bot were changed.

These checks do not establish a Java/platform matrix or live Discord role
acceptance. Human linking/unlinking, Discord role writes/hierarchy/intent
denial/rejoin/429 and visual clients remain open. See
[role verification](roles-verification-20261012.md) and
[real LuckPerms evidence](role-live-evidence-20261012.md).

## Comprehensive quality follow-up — 2026-10-12

The role-update artifact above is superseded by the corrected v1.0.1-BETA
artifact, 26,928,017 bytes, SHA-256
`8030fa9cc46cea6494cf592a66e37671adafaacc274fc641a4744223388d76ca`.
Fresh `clean test build --warning-mode all` passed 418 tests with no failures,
errors or skips; an independent Shadow rebuild matched. Gradle 9.1.0 uses
Eclipse Adoptium 21.0.12.1 on Windows 11; bytecode/API targets remain release 17 /
Spigot 1.20.4. Packaging and remaining live limitations are in
[the comprehensive review](quality-review-20261012.md). This is no expansion of
the supported runtime/platform matrix.

## Final continuation artifact — 2026-10-13

The final v1.0.1-BETA Shadow build passed 479 tests. The same artifact passed the
84-assertion isolated Paper 26.3-143 / Java 25.0.4.1 / LuckPerms 5.5.87 run, with
actual JDA message/role transport in the authorized test guild. Java target
remains 17 and API target Spigot 1.20.4; this does not prove every intervening
server version or actual Java 17 execution. Hash, packaging, restored fixtures
and remaining acceptance are in [continuation verification](continuation-verification-20261012.md).
