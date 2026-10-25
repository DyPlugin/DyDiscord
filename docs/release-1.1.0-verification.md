# DyDiscord 1.1.0 verification

Checked on 2026-10-24. Nothing was committed, pushed or published. The 1.0.2 sources were backed up to `.local/release-110-before` before any change.

## Automated checks

`gradlew clean test build` passed **718 tests, zero failures, errors or skips** (699 from 1.0.2, now 718). New and changed tests cover:

- the same keys and placeholders in all five languages on both surfaces, loading the new files, `lang: de` and the bot language following it, and client-locale selection (`de_DE`, `ru_RU`, `es-ES`, unknown `pt_BR`);
- German, Spanish and Russian preferences stored beside the 1.0 table, so a 1.0.2 JAR still reads them as `auto`;
- MiniMessage tags in Minecraft templates, player values shown as typed, and Discord text keeping its own escaping;
- status card fields online, without TPS (Spigot) and offline;
- status-card and plugin-logs validation (a channel is required, never the console channel, interval limits, per-event switches);
- DyClaim and DyRTP events turned into log lines through their public API methods, and Discord escaping of names;
- every non-chat Discord message as a Components V2 container (converter layout, the 3900-character bound, V2 message data without embeds or mentions, status card and log cards), with the earlier reply, console, shutdown and help tests updated to expect containers;
- placeholders from the online cache, the API account mapping, link and unlink notifications, plugin.yml soft dependencies, the bStats ID 34918 and its license file.

New compile-time dependencies are `adventure-text-minimessage` and `adventure-text-serializer-legacy` 4.16.0 and PlaceholderAPI 2.12.3, all `compileOnly`; the lockfile and `gradle/verification-metadata.xml` gained only those components. bStats 3.2.1 is vendored as source in `dev.dydiscord.bstats` (MIT, `licenses/bstats-LICENSE.txt`).

Artifacts:

- `DyDiscord-1.1.0.jar` (small): 2286670 bytes, SHA-256 `e3d579b5e93eb3252b813d8505ccabe7b706ebe06329aa994a7bf0ca85f64c30`
- `DyDiscord-1.1.0-standalone.jar`: 24206258 bytes, SHA-256 `e7638543791b87cbc939e429ca1b8c8cf76bef3f3cb5d532fa31e7ab7555c004`

## Live checks without a Discord bot

The small JAR above ran on disposable loopback servers with offline protocol clients. bStats was disabled.

**Upgrade on Purpur 1.20.4 (build 2176, Java 21)** with DyClaim 1.1.0, DyRTP 1.1.0 and PlaceholderAPI 2.12.3. The published 1.0.2 JAR (SHA-256 `6f34562c…`) created the data first, with a custom prefix and a Turkish player preference. **19/19 checks passed:**

- 1.1.0 enabled after the JAR was replaced; PlaceholderAPI expansion registered; DyClaim and DyRTP events hooked for plugin logs.
- `config.yml` kept the custom prefix and gained `integrations` and `metrics`; `bot.yml` gained `status-card` and `plugin-logs`.
- German, Spanish and Russian files were created for both surfaces, with no missing-message warning.
- The saved Turkish preference survived; German, Spanish and Russian could be chosen, and status answered in German with the custom prefix.
- `papi parse` returned `false|false|` for linked, bot connected and Discord name.
- Console reload succeeded, and a MiniMessage gradient arrived as hex colours.
- A DyClaim claim and a DyRTP teleport ran beside DyDiscord; shutdown was normal and the log had no DyDiscord errors.

One defect was found and fixed during this run: after `/dyd language de`, the confirmation named the language in the previous language ("Almanca" in a German sentence). It now uses the new language.

**Spigot 26.3 (build 4663, Java 25)**, which ships no Adventure. **4/4 checks passed:** the plugin enabled, a MiniMessage tag was shown as written while `&` codes applied, Russian was selectable, and no DyDiscord, Adventure or PlaceholderAPI errors were logged.

## Live checks with Discord

A second run on 2026-10-25 used the final JAR (`e3d579b5…`) after the owner entered a new token. It also turned on the console relay to the guild's #console channel with remote commands off. **11/11 checks passed:** every message was a Components V2 container with no embeds or plain content — the status card (green, `### Minecraft Server`, `**Durum:** 🟢 Çevrimiçi · **Oyuncular:** 0/20 · **Minecraft sürümü:** 1.20.4 · **TPS:** 20.0` and a relative update time), the same card edited to one player, the DyClaim and DyRTP logs, the grey console relay with its code block, the orange shutdown notice and the red Offline card. Slash commands were unchanged and no DyDiscord errors were logged; the ten messages created were deleted.

The first run (9/9, before the switch to containers):

The small JAR ran on a disposable Purpur 1.20.4 server connected to the owner's test guild (Claw's server) with the owner's bot. The owner entered the token and guild ID; the test scripts read them from `bot.yml` without printing them. The guild already held nine Turkish slash commands from another DyDiscord install. They were recorded as owned in the disposable test database and the bot ran in Turkish, so DyDiscord reused them without any change. **9/9 checks passed:**

- the bot connected; the status card was posted in #general with `Durum 🟢 Çevrimiçi`, `Oyuncular 0/20`, `Minecraft sürümü 1.20.4` and `TPS 20.0`;
- the same message was edited to show one player after a client joined;
- a DyClaim claim produced `**DcTester**, world dünyasında -16, -16 konumunda claim aldı.`;
- a DyRTP teleport produced `**DcTester** rastgele world dünyasına ışınlandı (…).`;
- a normal shutdown changed the card to `🔴 Çevrimdışı` with only the state field;
- after a restart the same card was reused and showed Online again, with no second card;
- the guild's slash commands had the same IDs, names and descriptions afterwards;
- no DyDiscord errors were logged.

The three messages created by the run were deleted afterwards. A first attempt in English correctly refused to touch the existing Turkish commands (unmanaged-command protection) and stopped before connecting; one English `players` command it had already registered was removed, leaving the original nine.

## Not verified

The Discord-side German, Spanish, Russian and English texts and their slash command descriptions were not seen in Discord, because changing the bot language would replace the guild's existing Turkish commands; unit tests cover their rendering. The trust and transfer log lines were checked by unit tests only. Folia, Bedrock/Geyser, proxies and high player counts were not tested. Translations were written carefully but not reviewed by native speakers.
