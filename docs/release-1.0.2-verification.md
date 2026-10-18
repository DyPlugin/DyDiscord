# DyDiscord 1.0.2 verification — 2026-10-18

Status: scoped source, automated and live relay verification completed. No publication or production installation.

## Changes and boundaries

Token commands/entry listeners/permission were removed. `/dyd` shares the normal router; `/dyda` suggestions use accessible admin leaf checks. Blank quoted role/command list examples are no-ops. Fresh/missing chat direction and event settings default on, console relay defaults off, command subflag on; explicit owner values remain. Top-level reward/role/giveaway opt-ins remain off.

Giveaway cards and private interactions use V2 Containers. Leaving requires an expiring single-use actor, guild, channel, panel, settings and session-bound confirmation. Active cards repair in place; unchanged cards avoid edits. Button lengths are validated before reload.

Console diagnostics use active EN/TR or safely inferred startup language. Known filenames, setting paths and YAML line/column are shown; parser source lines and raw exception values are withheld. Owner translations remain. Known bundled >> separator upgrades to »; custom formats remain.

Chat sender now wakes on demand with one scheduled task, retains pacing and five-second queue expiry even under long slowmode. Slash registration has one bounded 90-second budget including Discord rate-limit waiting and rechecks bot session before recording ownership.

## Verification

`gradlew --offline clean test build` succeeded: **699 tests, zero failures/errors/skips**. The subsequent clean packaging-only build (`clean build -x test`) succeeded and reproduced both hashes exactly; it does not claim a second test run. Full XML is preserved locally under `.local/release102-root/full-test-results`.

Targeted TDD caught the missing localized diagnostics, giveaway confirmation/repair boundaries, changed default/list parsing, shortcut/token surface and excessive/blank giveaway button labels before fixes. Static final review found the button-label validation omission; fixed and regressed. Six user YAML files pass comment placement/idempotence checks. Removed token keys are absent from active packaged messages; legacy resources remain historical. Existing explicit settings and custom text have regression coverage.

699 tests include the existing account/link races, role cleanup, permission/explicit-deny, panel, console, chat privacy/expiry, configuration migration/rollback and artifact checks. Passing unit tests do not establish every live integration.

## Artifacts

| Package | Bytes | SHA-256 |
| --- | ---: | --- |
| `DyDiscord-1.0.2.jar` | 2,193,177 | `6f34562c78d8b834c591fe88c646bd587825b4cc8d8243fffd69aece5f841cd9` |
| `DyDiscord-1.0.2-standalone.jar` | 24,112,176 | `986ac10ab11b7a45255096a370f6cc12512ed4b9732a4820432f88e81f721b41` |

Both plugin descriptors say 1.0.2. Plugin bytecode is major 61 (Java 17). Standalone retains all twenty upstream SQLite native resources, four service files and dependency license notices. Small package uses the server library descriptor and carries fourteen license/source notice files; external dependency natives/services are not expected inside it. No DB/log/runtime directory or configured token was found in either artifact. The plain JAR is not a distribution package.

## Live verification and limits

The exact small JAR above ran on an isolated Purpur 1.20.4 / Java 21 server with the authorized test bot/guild. Pilot 6/6, full DyDiscord relay runs 43/43 each with no duplicate/order error; these are actual chat/console receipts, not queue admission. Original server JAR/world were not replaced or started. Original guild commands and five ownership IDs were restored and checked, two temporary channels deleted, runtime token copies cleared, listener stopped; account rows were not changed.

See [the complete DiscordSRV measurements](performance/2026-10-18/README.md). Both plugins delivered all 58 measured chat messages. Low-rate medians: DyDiscord 329.9 ms, DiscordSRV 313.2 ms. Short-burst medians: 972.8 ms vs 399.0 ms. **No overall speed superiority claim or superiority marketing graphic is supported.** DiscordSRV console markers were not observed, so console performance is not comparable. Prior network failures and an observer-feedback attempt are excluded, and cleanup's HTTP 400 followed by successful recovery is retained in evidence.

No human modal/giveaway leave-button interaction, visual client rendering, Discord-to-Minecraft human latency, live role/reward delivery, extended network/load test or new cross-platform matrix is claimed. Existing historical reports must not be assigned to this artifact. Review these flows manually on designated test accounts before publishing.
