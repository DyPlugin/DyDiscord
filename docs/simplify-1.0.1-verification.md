# DyDiscord 1.0.1 simplification verification

Date: 2026-10-17. This report applies only to the artifacts below. Earlier release and polish reports retain their own historical results.

## Implemented behavior

- `/dyda` routes to the same administration definitions and leaf permission checks as `/dydiscord admin`; long routes and EN/TR aliases remain. Administrator help uses numbered pages only, with one header prefix and an unprefixed footer. Player help omits itself.
- The player `chat on|off` / `sohbet aç|kapat` setting controls incoming Discord visibility only. Outgoing and sound-notification choices are retired. Existing preference database columns are retained for rollback, but no longer control outgoing chat or sounds. Global direction controls use explicit source/destination names and persist atomically. Unchanged chat/prefix/basic settings do not rewrite files.
- Announcement commands, startup/scheduled announcements, permissions, service and bundled settings are removed. Migration removes `community-tools` while retaining owner comments; updates, rewards, giveaways and the normal bounded yellow shutdown embed remain. Advanced role/console policy editing stays in YAML. Useful role status/preview/synchronization/cleanup remains available.
- Unlink is red and Cancel green inside the existing red Container. Link success bolds the escaped Minecraft name. Private account output shows Minecraft and Discord names, localized date and optional assigned managed-role names. An already-linked Minecraft account reports its own stored Discord username; guessing a consumed code does not disclose another actor's account.
- Root invitation output is only `Discord: <link>`, without prefix. Clickability and copy/hover hints remain. Prefix, guild and basic channels can be managed in-game.
- `/dyda bot token` takes no argument and opens a player-only 60-second intercepted entry session. Permission, expiry and exact current Player session are rechecked before persistence; logout/reload/cancel/close invalidate pending work. File/env configuration remains supported; an active environment token blocks file-token entry. Audit fields contain no token. Post-commit audit failure does not falsely claim rollback.
- Public console channels are accepted when the bot has required channel permissions. Viewing output is separate from execution authorization. Current member/channel permissions and configured trusted-role/Administrator policy still govern remote commands. No technical acceptance/rejection replies were added to Discord console output.
- Known connection/intent/channel warnings use localized repair instructions, console WARN severity and the denyable `dydiscord.admin.alerts` permission for online administrators. Each known warning is throttled for 60 seconds. No arbitrary exception text is broadcast.
- Incoming Minecraft message text is gray, `>>` dark gray; primary Discord role RGB behavior remains.
- Six public YAML files have short English descriptions below settings. Owner values/comments are preserved. Flow mappings are expanded when needed for comment placement; block scalar content is protected. Rewritten YAML is parsed and compared before use. Repeated loads do not multiply comments/backups.

## Automated verification

`gradlew.bat test build --console=plain` passed on the final source: **659 tests, 0 failures, 0 errors, 0 skipped**. A clean full test/build also passed; the final full rerun includes subsequent message/alias and unavailable-guild warning corrections. The count is not directly comparable to older reports: the command permission matrix is smaller and removed-feature tests were replaced by retirement assertions.

Relevant coverage includes CommandSurfaceSimplificationTest, CommandTest, TokenSetupTest, AdminWarningsTest, SimplifiedChatCommandsTest, ChatPreferencesTest, ChatBridgeTest, AccountViewTest, ConsoleTest, DiscordSetup101Test, NoticesTest, ConfigurationBackupTest, ReleaseTranslationUpgradeTest and YamlCommentsTest. Existing linking, roles, boost/reward, panel, chat privacy, queue, migration and packaging regressions remain in the full suite.

Observed failures were investigated and corrected: unsafe comment insertion into flow-style YAML, CRLF fixture normalization, obsolete announcement/topic/account expectations, a token test fixture missing the Bukkit server, a nested Mockito stub, post-commit token audit reporting, and a missing administrator repair warning when the configured guild is unavailable. No tests were changed to permit unauthorized commands or private-chat leakage. The comment regression now checks parse equality, literal content, owner comments and idempotence.

The scoped independent read-only code review reported no high-priority finding. This was not a complete security audit.

## Actual server checks

On an isolated **Purpur 26.3 build 2651 / Java 25.0.4.1**, bound to `127.0.0.1:25596`, **online-mode=true**, RCON disabled:

- Exact small JAR: **23 checks passed**.
- Exact standalone JAR: **23 checks passed**.

The probe exercised real plugin loading and command dispatch, numbered help, prefix count, rejected category help, player-help omission, player-only token entry rejection from console, prefix changes/no-op persistence, unprefixed invitation, atomic two-direction toggles, single-direction changes, denied permissions, incoming format, log appender attachment, JDA JSON, SQLite native query and six-file reload preservation. Both servers stopped normally after checks. No production server was changed, no account authentication was bypassed, and no Discord message/role/command was sent.

The first small-probe attempt stopped after two checks because the harness compared a colored header to an uncolored string; stripping color in the assertion fixed it. The first standalone launch had a PowerShell JVM-argument quoting error before Minecraft started; quoting the property fixed it. Only the final 23-check logs count. Windows OSHI performance-counter warnings appeared during server startup independently of DyDiscord and did not prevent the checks.

Evidence remains local in `.local/simplify-101/`: `final-tests` build log, `test-results.zip`, `source.diff`, `changed-sources.txt`, packaging JSON and isolated server logs. The source snapshot comparison contains 52 changed/new/deleted source/resource/test paths. The project root remains outside Git; no repository was created.

## Packaging and reproducibility

`gradlew.bat clean build -x test --console=plain` rebuilt both distributions after the full test run. Both SHA-256 values matched the previous tested packages exactly. `plugin.yml` is 1.0.1; plugin bytecode major is 61 (Java 17). Standalone retains 20 SQLite native resource files, JDBC service registration and dependency license files. Both packages retain 14 dependency notice/license files. The small package declares its server-resolved libraries instead of bundling native dependencies. No runtime databases, logs, backups or local harness files were packaged. Dependency locks and the database schema were not changed.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `DyDiscord-1.0.1.jar` | 2177730 | `dab8049c63d6f0bcca30931262751e5acaffdc1d33904c2d9f16220e889e122e` |
| `DyDiscord-1.0.1-standalone.jar` | 24096526 | `d01ff1b96a6586e6659fb5635d5911334e06bd68047e409ece3e029253c72847` |

Artifacts are under `C:/Users/alph/Documents/DyDiscord/build/libs/`. Install one alternative only. The plain build is not the distribution.

## Limits and remaining acceptance

No real human Minecraft session or Discord interaction was used in this round. Visually verify the private account card, bold name, red/green buttons, invite click and code hover/copy in the actual clients. Test token entry only on a controlled server; client command history and earlier third-party packet/listener loggers can still see original input, so local bot.yml/env editing is preferred. The plugin cannot guarantee third-party logger secrecy.

Public console acceptance, fresh command authorization and intent-denial behavior have automated fixtures here; they were not exercised against live Discord permission changes in this round. Check those with designated test accounts/roles, including explicit denial and missing intents. No live console-latency or competitor performance benchmark was run. No fastest/best/full-security or whole-platform-matrix claim is made.

Stop and back up the server/data folder before replacing an earlier same-version JAR. Keep account/panel databases and owner configuration; no relinking is required. One initial comment/retirement migration may create bounded pre-change backups, and subsequent unchanged reloads must not create more. Restoring a database backup does not reverse Discord role changes. No commit, push, release publication or production installation was performed.
