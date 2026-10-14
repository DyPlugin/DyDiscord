# Compatibility and verification

## v1.0.1-BETA — 2026-10-14

The distribution is `DyDiscord-v1.0.1-BETA.jar`.
SHA-256: `6af86328f2ed1552b7c48577c5c8819302d94bbc06f82b71263d4c0a34426cd6`.

- Fresh `clean test build` completed successfully: 491 tests, zero failures,
  errors or skipped tests.
- Java 17 bytecode, Spigot API 1.20.4 compile target and plugin version were checked.
- The Shadow distribution includes 20 SQLite native files, four service files
  and 14 dependency license/notice entries. Runtime databases and logs are excluded.
- The source and Gradle files prepared for publication match the tested source.

Compilation targets do not prove support for every Minecraft version or runtime.
Use the Java runtime required by your server. Folia, proxies, third-party hot
loaders and arbitrary chat/login plugins are outside the current support claim.

## Live verification boundaries

The artifact above previously passed nine targeted isolated checks on Paper
26.3-143 / Java 25 and a test Discord server. They covered panel disabling,
retained panel state, command-only directions, rejected refresh while disabled,
bot restart preserving the mode, and re-enabling the same panel message/buttons.
Human modal clicks and visual clients were not retested. This publication run
used automated tests and packaging checks, not another live server session.

Earlier v1.0.1 development artifacts had scoped real LuckPerms 5.5.87 and Discord
role checks. Those historical results do not automatically verify this exact JAR.

Remaining checks include human account linking/unlinking, visual clients, real
paid boost transitions, external economy/site balances, Java 17 runtime behavior,
and installation-specific permission, login and moderation integrations.

## Integration boundaries

LuckPerms is the supported real-group provider for role synchronization.
Vault Chat is a rank-display fallback, not a group synchronization provider.
Linked-account Discord roles work without LuckPerms. Group synchronization is
Minecraft to Discord only; it never grants Minecraft permissions or OP.

Unknown Minecraft chat listeners pause outbound forwarding to protect private
or moderated conversations. Offline-mode linking requires the server's login
system to authenticate players and restrict all command aliases before login.
No login-plugin API integration or bundled economy/site-credit provider is included.

Before upgrading, see [backup and rollback](upgrade-and-rollback.md). Restoring
local data does not reverse Discord role changes or executed reward commands.
