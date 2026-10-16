# DyDiscord 1.0.0 verification — 2026-10-16

## Build and artifact evidence

- Clean command: `gradlew.bat clean test build` — passed.
- Automatic tests: **621**; zero failures, errors or skips.
- Independent `shadowJar smallJar --rerun-tasks` build in the publication clone
  produced byte-identical small and standalone artifacts from the matching source.
- New release/tag/version is `1.0.0`; no leading v or BETA.
- Small: `DyDiscord-1.0.0.jar`, 2115780 bytes.
- Small SHA-256: `5d9a7d77eb8b4b9fbd0e49fb49162bebc6c7a3d322df81cf912aa1d9e337eb01`.
- Standalone: `DyDiscord-1.0.0-standalone.jar`, 24034401 bytes.
- Standalone SHA-256: `e0a11516699cc67547d1c663c7bd857331bf6fb8b5d927d84e36bf6c60e35aa6`.
- Application bytecode is Java 17 (major 61). The standalone retains 20 SQLite
  native files, four service descriptors and original dependency source/notices.
- No runtime databases, logs, tokens, test helper or server configuration is bundled.
- Git line endings follow the existing attributes; binary/native/source archives
  are unchanged. The plain JAR is not a distribution.

## Verification coverage

The suite retains prior linking, panel, permission, chat, console, roles, boosts,
language, migration and update checks. New regressions cover permanent first-link
observations and rollback/outages, queued dispatch/session checks, private welcome,
personal preferences and close races, pause epochs, slowmode/truncation/replies,
public visibility/channel boundaries, announcement pacing and guild changes,
safe diagnostics/read-only validation, translated new-key migration and idempotence,
future/unknown optional database schemas, and giveaway admission/draw/recovery.

Giveaway limits, duplicate entries, revoked membership, unknown REST responses,
restarts without reroll, old-channel and same-channel failed-result histories,
bounded retries and queued-start session changes have deterministic checks.
First-link receipts distinguish dispatch from actual economic/provider success.

## Actual isolated server

The exact small artifact above ran on Windows Paper 26.3-143 / Java 25.0.4.1,
localhost only, online-mode enabled, with the Discord bot disabled. The Maven
library mirror was unreachable; cached resolution logged no downloads.
Five packaging probes and six release probes passed: JSON/V2 serialization,
SQLite persistence (effective SQLite 3.49.1), active logging, JDA cache operations,
application storage/admin dispatch, version, visibility-safe main-thread snapshot,
genuine STARTUP observation, synthetic primary link/relink facts, asynchronous
preference storage and disabled-giveaway refusal. Language/reload, safe report,
notice preview and per-direction pause/resume commands were exercised.

New keys appeared physically in all four language files. Read-only validation
reported zero missing translations. The one-time observation backup remained
single after subsequent starts. The test server stopped normally.
A synthetic direct-domain link is not human authentication or modal evidence.
The host printed existing Paper/OSHI registry-counter warnings; these were not
DyDiscord initialization failures and the operating system was not modified.

## Limits and manual follow-up

No fresh live Discord bot connection, human modal/account interaction, real
participation/winner announcement, role write, paid boost, website credit or
reward-provider payment was performed for this artifact. Those paths have
fixtures, not new live acceptance. Verify with designated test accounts/roles
before enabling optional features for a community. No production server changed.

No actual Java 17 runtime, alternate OS/native architecture, Spigot 1.20.4 runtime,
visual desktop/mobile client or measured load/network-failure campaign was run.
The default library loader can resolve upstream Maven transitives and use
server-provided versions; declaration versions are not full runtime isolation.
Previously removed links cannot be reconstructed from deleted historical data;
the new anti-repeat ledger covers the migrated baseline and later observations.
Prior audit/live reports remain evidence only for their original artifacts.
