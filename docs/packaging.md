# Distribution packaging — 1.0.2

| Artifact | Bytes | Purpose |
| --- | ---: | --- |
| `DyDiscord-1.0.2.jar` | 2193177 | Default; server-managed library download/cache |
| `DyDiscord-1.0.2-standalone.jar` | 24112176 | Bundled alternative for offline dependency installation |

Install one package. Both have the same features and Java 17 target. The 699-test clean build and a second clean packaging build reproduced both hashes. Standalone retains all 20 SQLite native files, service metadata and dependency notices. See [artifact hashes and limits](release-1.0.2-verification.md).

## Earlier packaging history

# Distribution packaging — 1.0.1

| File | Bytes | Use |
| --- | ---: | --- |
| `DyDiscord-1.0.1.jar` | 2124829 | Default; server-managed dependency download/cache |
| `DyDiscord-1.0.1-standalone.jar` | 24043442 | Bundled alternative for offline dependency installation |

Do not install both or the plain intermediate JAR. Both have plugin.yml version
1.0.1 and Java 17 bytecode. The 643-test clean build, artifact inspection and
independent repeat build passed. Hashes, actual Purpur/Discord validation and
remaining limitations are in [the 1.0.1 report](release-1.0.1-verification.md).

---

## Historical 1.0.0 packaging evidence

Install only one distribution. The plain JAR is not a distribution.

| File | Size | Use |
| --- | --- | --- |
| `DyDiscord-1.0.0.jar` | 2115780 bytes | Small default; server downloads/caches libraries on initial installation |
| `DyDiscord-1.0.0-standalone.jar` | 24034401 bytes | Bundled alternative for offline initial installation |

The small JAR declares 15 locked runtime root coordinates through Spigot/Paper's
built-in `plugin.yml libraries` loader. First installation needs Maven Central
or the server mirror; cache is server-owned (`libraries/` in the tested server).
Preserve it for cached starts. Resolution happens before plugin enable and can
delay or prevent startup when required dependencies cannot be obtained.

Maven transitives are resolved by the server independently of Gradle exclusions.
Unused upstream JDA audio/crypto libraries can be cached; DyDiscord does not enable
voice. Server-provided versions can win over declared ones. The actual Paper test
used SQLite 3.49.1 and Log4jLoggerFactory. Standalone retains relocated bundled
libraries; SQLite keeps its original package/native lookup paths.

Shadow only minimizes Trove and Commons Collections; other runtime classes remain
analysis roots. Standalone preserves all 20 SQLite natives, four service descriptors,
dependency notices and the full original Trove source archive. Small and standalone
have the same application classes and editable YAML resources. No runtime data,
secrets, logs or verification helper is bundled.

## Current verification

`gradlew.bat clean test build` passed 621 automatic tests with zero
failures/errors/skips. A separate build from the publication source produced
byte-identical archives. The exact small artifact passed 11 isolated Paper checks
and cached resolution with an unreachable Maven mirror and zero downloads.
No fresh first-download, human Discord interaction or alternate OS runtime is
claimed for this artifact. See [full verification](release-1.0.0-verification.md).

- Small SHA-256: `5d9a7d77eb8b4b9fbd0e49fb49162bebc6c7a3d322df81cf912aa1d9e337eb01`
- Standalone SHA-256: `e0a11516699cc67547d1c663c7bd857331bf6fb8b5d927d84e36bf6c60e35aa6`

Version 1.0.0 replaces the unpublished 1.0.2-BETA packaging proposal. Existing
published beta tags remain historical. Packaging alternatives use the same plugin
and data folder; switching variants never requires deleting accounts/configuration.
