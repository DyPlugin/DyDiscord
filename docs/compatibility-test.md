# Compatibility and verification — v1.0.0-BETA

Prepared on 2026-10-10. This is a Beta release with incomplete live acceptance.

## Release artifact

| Field | Result |
| --- | --- |
| Artifact | `DyDiscord-v1.0.0-BETA.jar` |
| Version | `1.0.0-BETA` |
| SHA-256 | `2e94b4645e051720910ab182cc07e20d59b06b086bc9fae781251cdb16bd6ccf` |
| Size | 26,865,858 bytes |
| Build | Gradle Wrapper 9.1.0, Eclipse Adoptium JDK 21.0.12.1 |
| API baseline | Spigot 1.20.4-R0.1-SNAPSHOT |
| Bytecode | Java 17; maximum base-class major version 61 |
| Automated validation | 322 tests, zero failures/errors/skips; fresh `clean test build` |
| License | Owner-selected GPL v3 notice with additional project terms; Claww / DyPlugin |

Packaging checks cover the bundled JDA/YAML/SQLite smoke tests, 20 SQLite native
files, SQL and relocated SLF4J service descriptors, project GPL text, dependency
notices and the original Trove4j source archive. No runtime database/log,
configured bot token or Bukkit/Log4j server implementation is included.

## Automated coverage

Commands, permission leaves, explicit deny, localized help/TAB and aliases;
custom translations, placeholders, supported migrations and reload failure;
code/confirmation expiry, concurrent linking and permanent database uniqueness;
bot sessions, delayed callbacks, panels and interactions; console authorization,
audit, deduplication, masking and bounded queues; chat privacy, message safety,
event/session context and queue lifetime; update parsing, version/platform
selection, refresh races and request failures.

Tests use local fixtures. They do not prove all real plugin combinations or
Discord service availability. The clean build emitted existing deprecation and
unchecked-operation warnings; these were not suppressed.

## Earlier live evidence

Earlier Beta artifacts were exercised on Paper 26.3-143 / Java 25, including
bot connection, panel updates, English/Turkish administration, actual console
output, offline-mode commands, LuckPerms permissions, two-way text chat and
selected player events. These results belong to those earlier artifacts.
The artifact above was **not installed or live-tested in this publication task**.
Intermediate Minecraft versions, Spigot/Purpur runtime combinations and Java 17
runtime execution have not all been individually verified.

## Remaining owner checks

- Complete linking and confirmed unlinking with real human Minecraft/Discord accounts.
- Inspect the panel and private replies on desktop and mobile clients.
- Validate the chosen real Vault/LuckPerms and moderation/chat providers.
- Verify offline login restrictions across every registered command alias.
- Exercise actual Discord permission changes, network failures and reconnects.
- Verify Modrinth notifications after publishing compatible versions there.

Folia, proxies, third-party hot loaders and unknown chat-provider semantics are
outside the support claim. No universal compatibility or error-free guarantee
is asserted. Use the Java runtime required by your server.
