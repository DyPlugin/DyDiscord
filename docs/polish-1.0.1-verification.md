# DyDiscord 1.0.1 — presentation and delivery verification

Date: 2026-10-17. This build keeps version **1.0.1** and replaces the earlier
1.0.1 build locally. Earlier hashes and live results identify earlier binaries.
No publishing, Git push or production installation was performed.

## Implemented behavior

- Six unambiguous random characters by default. The previous shipped length of
  eight, identified by its shipped comment, migrates to six with a deduplicated backup; later deliberate eight-character choices and other custom lengths remain.
  One-use redemption, current player session/permission checks, expiry, failed
  attempt limits and durable unique account insertion remain in place.
- `/discord link` and shortcuts send the copyable code with a localized hover
  hint, then a separate clickable Discord invitation. In-game unlink confirmation
  tokens are six characters and remain actor, record, permission and time bound.
- A missing invite is created once with no expiry or usage limit, persisted and
  reused. It requires Create Invite permission, never uses the console channel,
  and backs off attempts for one minute. Manual invites, including pending disk
  edits during network/validation, are protected by persisted-value and source
  checks. Discord may still reject invitation creation under its own policies.
- Private Discord success is one natural sentence, without a heading or an
  appended welcome. Account details show a localized readable date and only
  assigned configured managed-role names. The role row defaults on, can be
  disabled, and is absent when empty. `account.time-zone` is validated before
  reload publication; UTC is the default, Europe/Istanbul can be selected.
- Private unlink confirmation is one red Components V2 Container, with green
  Unlink/Kaldır and red Cancel/Vazgeç buttons. Completion edits that same private
  confirmation and removes its actions. Existing public panel structure/order
  remain unchanged. Account output uses no allowed mentions.
- Global game en/tr remains an override. Game auto uses a saved personal en/tr
  choice, then the bot language. Bot auto follows an explicit game language,
  otherwise English. `/dydiscord admin bot language <en|tr|auto>` shares the
  existing language leaf permission and performs a controlled restart.
- Administrator help opens an eight-topic overview. `admin help console` and
  translated equivalents show just that topic. Numeric full-reference pages,
  existing executable commands, aliases and leaf/explicit-deny checks remain.
- Chat accepts up to five messages per user in a two-second window, with the
  existing 120/minute global bound, message-ID deduplication, bounded queues,
  privacy/cancellation checks, five-second queue lifetime and no replay.
- Console forwarding runs on its existing dedicated timer worker rather than
  waiting behind other plugin I/O. Empty-buffer checks are network-free; nonempty
  checks run every 250 ms after completion. Three independent fresh REST reads
  overlap, retaining privacy and permission validation, rate-limit backoff,
  bounded buffers and actual-server-output-only Discord behavior.

## Automated verification

`gradlew clean test build polishProbeJar -I .local/polish-101/probe.init.gradle`
completed successfully: **664 tests, zero failures, errors or skips**.
The local probe task is outside the product build and is not included in either
artifact. Evidence: `.local/polish-101/verified-final-build.log` and fresh JUnit XML.

Regression coverage includes six-character redemption and denial/rate behavior,
actor-bound short confirmations, repeated chat and duplicate IDs in order,
language inheritance and explicit overrides, topic permissions, click/hover
behavior, V2 confirmation flags/buttons/colors, readable dates and optional role
rows, escaped names, custom templates, malformed zones, legacy defaults, comment
and backup idempotency, manual-invite races, and simultaneous fresh permission
reads. Existing role, reward, giveaway, console security, persistence and package
tests remain. No security checks were removed to obtain passing results.

Both deliverables were independently rebuilt with
`gradlew smallJar shadowJar --rerun-tasks`; both SHA-256 values matched.
`plugin.yml` is 1.0.1; product classes target Java 17 (main class major 61).
The standalone archive retains 20 SQLite native resources and four service files.
Both archives contain license notices and no runtime database/log/.local data.
The small archive uses the existing server-resolved dependency/cache mechanism;
it intentionally does not embed SQLite/JDA dependencies.

## Actual server and Discord verification

An isolated Purpur 26.3 build 2651 / Java 25.0.4.1 server bound only to
127.0.0.1, with **online-mode=true**, ran the exact small JAR against authorized
test guild 1557418524983566447. **27 application checks passed**, including:

- real command routing, numbered/topic help, effective bot-to-game language,
  six-character default, malformed reload preservation and stable backups;
- actual JDA connection, automatic first V2 panel, refresh/restart same panel ID,
  and public-console rejection/private-console setup;
- a real permanent unlimited-use invite, persistence and reuse;
- real vanilla `say` output arriving in the private Discord console;
- Discord accepting the new contained red unlink confirmation and help embed;
- command-only mode and re-enabling the original panel;


The marker-to-Discord Gateway delivery measurement was **752 ms**, one sample.
This is not a latency guarantee, percentile or controlled comparison with another
plugin. No claim that DyDiscord is faster or better than all alternatives is
supported. The scheduling, shared-queue and sequential-network waits identified
in this code were reduced without weakening the fresh security checks. Network,
Discord rate limits and load can still increase delivery time. TPS, sustained
production load and a like-for-like competitor benchmark were not measured.

A final cleanup request timed out after those 27 application checks. A separate controlled helper then removed both test channels, removed four newly test-owned slash commands and restored five original owned command definitions. That timed-out automated cleanup is not counted as passed. Evidence: `.local/polish-101/final-cleanup.log`.

Evidence: `.local/polish-101/server/polish-live-verified.log`. Temporary credentials were
scrubbed; original server files and account data were not changed. No human
account, auth bypass or existing role mutation was used.

The exact standalone JAR separately passed **10 real server checks**: ready state,
version, relocated JSON and EN/TR help, native SQLite, attached log relay,
second help page, localized account-date rendering and relocated V2 confirmation
serialization. Evidence: `polish-standalone-exact-final.log` in the same test folder.

## Failed attempts and limits

Early automated runs exposed expected changes to the previous message, default
length and shared-I/O test expectations; they were updated to the requested
behavior while preserving assertions for security and owner data. Review found
and fixed an automatic-invite overwrite race and a cross-surface placeholder
acceptance issue, each with regression coverage. A final red/green migration test also prevents a deliberately selected eight-character length from being reset on every reload; only the previous shipped eight-character comment/value is recognized as the old default.

A first local launch used incorrect relative copy paths and started an empty
test server. Only that identified localhost test process was stopped; no passing
plugin check is attributed to it. Standalone harness attempts first assumed an
English footer on a now-Turkish server and later selected an overloaded record
constructor by order. The harness was corrected without changing plugin code;
only the final ten-check run is counted as the completed standalone verification.

Human modal entry/link/unlink, actual human Discord-to-console commands, mobile
and Minecraft visual layout, and human role/gradient rendering were not newly
verified. The account confirmation was accepted by the real Discord API, not
claimed to have been clicked by a human. The full 1.20.x–26.x platform matrix was
not rerun. No arbitrary external authentication/login plugin compatibility or
blanket security/performance guarantee is claimed.

## Upgrade and artifacts

Stop the server, back up the complete DyDiscord directory, replace the JAR with
one of these alternatives and restart. Keep the data directory; no database
migration or relinking is required. An earlier same-version JAR must actually be
replaced; its behavior does not change because the version text matches.
Existing explicit disabled settings and owner translations remain. Recognized
previous defaults/obsolete English comments migrate; repeated reloads do not
multiply them. Set `account.time-zone: Europe/Istanbul` for Turkey-local dates.

- `DyDiscord-1.0.1.jar` — 2,151,506 bytes
  SHA-256: `67de1007d5ca68715a78246987279b7c6bfeb19fab058da0384ed2aace141401`
- `DyDiscord-1.0.1-standalone.jar` — 24,070,222 bytes
  SHA-256: `618ec5c4439bf26f93c118756a12d9c77466bd63d2dec7a9a8dac33c503967e0`

## Changed sources and reference checks

Implementation changes are in account/linking presentation, settings/migration,
bot/command routing, ingress admission and console delivery. Six public YAML
resources and their known previous message defaults were updated. New tests are
UserExperience101Test, AccountViewTest and PermissionLatencyTest; related existing
command, migration, chat, interaction and console tests were extended. The exact
38-source-file comparison against pre-edit copies is recorded locally in
`.local/polish-101/changed-sources.txt` and `source.diff`. Java/dependency versions,
dependency locks, database schema and license were not changed.

README, Turkish setup notes, setup/upgrade documents, CHANGELOG, AGENTS and local
publication-description text now describe the new behavior. No remote publication
page was edited.

API semantics were checked against the official
[JDA InviteAction documentation](https://docs.jda.wiki/net/dv8tion/jda/api/requests/restaction/InviteAction.html)
and [Components V2 message editing](https://docs.jda.wiki/net/dv8tion/jda/api/utils/messages/MessageEditBuilder.html).
[DiscordSRV's public configuration documentation](https://docs.discordsrv.com/config/)
was considered as a reference, not copied or treated as comparative benchmark evidence.
