# Technical decisions â€” 2026-10-07

DyDiscord starts from an empty implementation directory containing AGENTS.md.
DyClaim build/language/alias sources were read for presentation context only;
its source tree, test results and license were not copied or modified.

| Area | Decision | Evidence / boundary |
| --- | --- | --- |
| Java output | Java 17 bytecode | `options.release = 17`; isolated artifact smoke checks on JDK 25 |
| Minecraft API | Spigot 1.20.4 API | compile target; Paper 26.3 live scope recorded separately |
| Candidate runtime | Spigot 1.20.4 / Java 17 | not a tested support claim |
| Current live test target | Minecraft 26.3 / Java 25 | Paper 26.3-143-main@ff3655a / Oracle 25.0.4.1 live scope verified |
| Build runtime | JDK 25.0.4.1, Gradle Wrapper 9.1.0 | local build succeeds; wrapper ZIP checksum pinned |
| Discord client | JDA 6.7.0 | official latest release checked 2026-10-07; minimum Java 8, no audio needed |
| Account panel, revised 2026-10-08 | one Discord Components V2 Container | text beside the bot-avatar Thumbnail; short paragraphs, a small divider and three account buttons; no brand label or footer |
| Console response, revised 2026-10-08 | only actual server output for every outcome | no generated admission/rejection/error response; local audit/receipts retain dispatch state and unknown-command classification |
| Linking presentation, revised 2026-10-08 | code-only in-game reply; 12-character default | SecureRandom, expiry and rate limits remain; explicit custom lengths are preserved |
| Account display, revised 2026-10-08 | Minecraft name + Discord username | IDs retain identity/uniqueness; backward-compatible username storage and localized legacy fallback; no raw IDs/dates in status |
| Bot profile activity, revised 2026-10-08 | no activity/status line | legacy `bot.activity-text` ignored; health/status commands retained |
| Persistence | SQLite JDBC 3.53.4.0, WAL, FULL synchronization | local unique/race/reopen/corruption/native smoke tests |
| Current data/config schemas | database 2; YAML config-version 2 | additive transactional schema 1â†’2 migration; future schemas refused, pre-write YAML backups supplement the owner's complete backup |
| YAML | SnakeYAML 2.5 SafeConstructor | duplicate/tag/alias/depth/size constraints; comment-preserving node edits |
| JSON | Jackson 2.22.3 | overrides vulnerable JDA transitive 2.22.2 |
| Log API | Log4j 2.26.1 compile/test only | actual server supplies Log4j; no server logging classes bundled |
| Packaging | Gradle Shadow 9.2.2; one bundled JAR | services/native drivers retained, dependencies relocated |
| License | DyDiscord Source-Available License 1.0 from 1.1.0 (2026-10-25), matching DyClaim; GPL v3 notice with additional project terms up to 1.0.2 (2026-10-10) | Claww / DyPlugin copyright, attribution, non-commercial distribution and public-source requirements; dependency licenses remain independent |

Sources:

- [JDA 6.7.0 release](https://github.com/discord-jda/JDA/releases/tag/v6.7.0)
  introduces upcoming channel obfuscation handling. The plugin uses interaction
  channel IDs and configured guild scope; live configured channels were validated.
- [JDA README](https://github.com/discord-jda/JDA/tree/v6.7.0) documents Java
  requirements, intent separation, audio exclusions and Apache-2.0 licensing.
- [JDA license](https://github.com/discord-jda/JDA/blob/v6.7.0/LICENSE).
- [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
  records the current stable 26.3 and its version metadata declares Java 25.
- [Gradle 9.1 release](https://docs.gradle.org/9.1.0/release-notes.html) documents
  Java 25 support. Project dependency locks and SHA-256 artifact verification
  preserve the resolved build inputs. Spigot's snapshot repository is not a
  stable release coordinate; verification protects the selected artifact bytes.
- [Jackson core advisory](https://github.com/advisories/GHSA-7hhh-6rmp-j9qf) and
  [Jackson databind advisory](https://github.com/advisories/GHSA-cxp5-3px4-pw24)
  identify fixes in 2.22.3. Additional same-version findings were handled by the
  same update; scan evidence is recorded in compatibility-test.md.
- [Discord interactions](https://docs.discord.com/developers/interactions/receiving-and-responding),
  [application commands](https://docs.discord.com/developers/interactions/application-commands),
  [Gateway](https://docs.discord.com/developers/events/gateway) and
  [components](https://docs.discord.com/developers/components/using-message-components)
  define modal first response, ephemeral deferral, intents and the separation
  of classic embeds from Components V2 messages.

On 2026-10-08 the user requested a real Container card and then provided a
screenshot showing excessive space beneath its avatar/header. The final
revision uses `IS_COMPONENTS_V2` and one Text Display containing the heading,
instructions, three bold action explanations and privacy note. One blank line
follows the introduction, separates each action description and precedes the
privacy note. That text sits in one Section beside the avatar Thumbnail, so a
separate header-only Section does not reserve empty space above the description.
One small Separator divides the text from the Action Row containing only the
three existing account buttons. The user's later instruction removes the
passive `DyDiscord` label entirely; there is no separate footer or brand button.
The avatar accessibility text is the neutral `Discord`. Other command/account
replies also have no branding footer.

The V2 message has no classic `content` or `embeds`. These schema limits and
the supported layout components are documented in the
[Discord component reference](https://docs.discord.com/developers/components/reference).

The existing recorded panel message is edited in place and keeps the
`dd:panel:link`, `dd:panel:account` and `dd:panel:unlink` custom IDs. Recovery
recognizes the old classic footer marker as well as the new invisible stable
component ID with exactly the three account IDs, optionally accompanied by the
previous passive brand ID for upgrade recovery. Newly rendered cards contain
only the three account IDs. An API access failure must not create
another panel. Earlier classic-panel REST evidence does not verify the new
Container rendering; structural/migration tests and live verification are
recorded separately in the acceptance and test documents.

The user's console screenshot also showed an unnecessary `dispatched`
acknowledgement before the actual server response. The user's final instruction
requires only server INFO/WARN/ERROR output for every outcome. Command admission,
rejection, unknown commands, malformed input, full queues and executor errors
add no bot response. A command that writes nothing produces no fabricated line.
Durable admission, deduplication and audit retain dispatch state; `not-found`
is distinct from a policy rejection. Exceptions remain masked in local logs.

The same revision removes invite/panel links from the in-game code reply and
changes the new-setup default to 12 unambiguous random characters. SecureRandom,
hashed in-memory codes, expiry, session/permission checks and attempt limits
continue to apply. Custom configured code lengths are retained. The previous
8-character live result is kept as historical evidence, not a current-default test.

Account success on both sides and private status use Minecraft and Discord
names. Discord usernames are persisted as display data through a schema upgrade
that preserves the UUID/Discord-ID identity and uniqueness constraints. Existing
records with no username use a localized account label. Status does not expose
raw IDs or dates; UTC link time remains in storage for integrity and audit.

Only exact recognized shipped legacy message values are upgraded in memory;
valid owner-written templates remain intact and incompatible placeholder sets
are rejected. No message-file rewrite is used to hide a failed validation.
Database/schema and template upgrades do not replace the required pre-upgrade
backup. The V2 flag cannot be removed from an existing Discord message, so an
older classic-only JAR/data rollback needs a separate owner-reviewed panel repair.
See [backup and rollback](upgrade-and-rollback.md) and
[Discord's component rules](https://docs.discord.com/developers/components/using-message-components).

No license choice or release date for DyClaim is attributed to DyDiscord.
No Folia, proxy, Bedrock, latest Minecraft or production support is asserted.
