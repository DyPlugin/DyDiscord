DyDiscord by Claww / DyPlugin â€” third-party software

DyDiscord's project terms are in DYDISCORD-LICENSE.txt and LICENSE-DYDISCORD.txt.
Dependency licenses apply independently. Bundled license/notice files are
preserved under META-INF/licenses/<artifact> and META-INF/ upstream paths.

Pinned runtime dependencies (see gradle.lockfile for the complete resolution):

* JDA 6.7.0 â€” Apache-2.0; https://github.com/discord-jda/JDA
* SQLite JDBC 3.53.4.0 â€” Apache-2.0 / BSD-2-Clause; https://github.com/xerial/sqlite-jdbc
* SnakeYAML 2.5 â€” Apache-2.0; https://bitbucket.org/snakeyaml/snakeyaml
* Jackson â€” Apache-2.0; https://github.com/FasterXML/jackson
* SLF4J â€” MIT; https://www.slf4j.org/license.html
* OkHttp and Okio â€” Apache-2.0; https://github.com/square/okhttp ; https://github.com/square/okio
* Kotlin â€” Apache-2.0; https://github.com/JetBrains/kotlin
* nv-websocket-client â€” Apache-2.0; https://github.com/TakahikoKawasaki/nv-websocket-client
* Apache Commons Collections â€” Apache-2.0; https://commons.apache.org/proper/commons-collections/
* Trove4j core â€” LGPL-2.1-or-later; https://bitbucket.org/trove4j/trove
* JetBrains annotations â€” Apache-2.0; https://github.com/JetBrains/java-annotations

The standalone distribution excludes audio natives, voice encryption and DAVE.
The small distribution uses Spigot/Paper's Maven library resolver, whose
transitive resolution can additionally cache upstream JDA audio/crypto libraries.
DyDiscord does not enable voice features. Server-provided library versions may
take precedence in this mode; the standalone distribution retains relocated
dependencies for installations needing the bundled alternative.
Spigot API and Log4j are supplied by the server and are not bundled.
SQLite's driver package and platform native resources retain their upstream names.
Trove4j 3.1.0 sources are included under META-INF/licenses/trove4j/core-3.1.0-sources.jar.
The build relocates gnu.trove to dev.dydiscord.lib.trove without source edits
and removes unused compiled collection classes with Gradle Shadow minimization.
The complete original Trove source archive is retained.
Gradle Shadow applies this package transformation; original sources and the build
script provide the inputs for regenerating those bundled classes. The matching
source release includes the build inputs and dependency verification metadata.
