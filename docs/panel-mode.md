# Panel or command-only account access

The account panel is optional. Existing installations keep it enabled by default.
To switch to commands, run `/dydiscord admin panel disable` in Minecraft or the
server console. `/dydiscord admin panel enable` restores panel access. Turkish
aliases are `/dydiscord admin panel kapat` and `/dydiscord admin panel aç` (`ac`).
Both require `dydiscord.admin.panel.toggle`; help/TAB respect explicit denial.

The equivalent `bot.yml` setting is:

```yaml
panel:
  enabled: false
```

Apply file edits with `/dydiscord admin reload`. This flag does not need a bot
restart. Keep your channel/image settings to reuse them later.

While disabled, recorded panel buttons are disabled when the bot is connected.
Old button interactions and forms opened from the panel are also rejected on the
server, even if Discord could not update the card. The message and link records
are retained. Re-enabling updates the same recorded message; it does not create
a second panel. If no panel existed, choosing its channel creates it automatically
when connected. Startup, reconnect and reload also reconcile the configured panel.
Bot startup/reconnect reconciles the saved mode. Missing permissions/timeouts
can leave a visual update pending; the configured interaction policy still applies.

Use Discord `/link`, `/account` and `/unlink`, or Turkish `/eşle`, `/hesap` and
`/kaldır`, in command-only mode. Linking opens a private code form; account and
unlink replies remain private. `dy!link`, `dy!account` and `dy!unlink` point to
the matching slash command instead of a panel URL. These prefix directions are
ordinary public messages with no account details. Prefix commands still need
`bot.prefix-commands-enabled` and Message Content intent; slash commands do not.
Minecraft code generation, existing links, role/boost reconciliation, console
policy and the enabled panel's layout/button order remain unchanged.

## Verified 2026-10-14

The updated v1.0.1-BETA Shadow JAR passed 491 automated tests (zero failures,
errors or skips), including disabled-button rejection, panel-form rejection,
successful command-only modal commit, EN/TR directions, leaf-filtered help/TAB
and persistent panel ID reuse. A clean build passed; an independent Shadow
rebuild matched the hash. Java target, native SQLite files, service resources
and dependency license notices were checked again.

Nine targeted isolated Paper 26.3 / Java 25 / test Discord checks passed:
actual admin disable, disabled buttons on the recorded message, retained state,
command-only guidance, refused manual refresh, bot restart retaining disabled
mode, and enabling the same message/buttons again. The private disposable channel
was deleted, slash metadata and isolated settings/panel state were restored;
the original server/accounts/JAR were not replaced. Human modal clicks and
visual clients were not retested; automated modal fixtures are not human evidence.

Artifact: `build/libs/DyDiscord-v1.0.1-BETA.jar`, 26,963,839 bytes.
SHA-256: `6af86328f2ed1552b7c48577c5c8819302d94bbc06f82b71263d4c0a34426cd6`.
Prior reports and live results describe their older artifact hashes.
