# DyDiscord 1.0.2 and DiscordSRV 1.30.5 — measured chat delivery

Date: 2026-10-18. Local validation only; no platform publication.

## Result

There is **no overall speed-superiority result for DyDiscord**. Low-rate chat was close in these runs; DiscordSRV was faster in short bursts. No superiority advertisement/chart is supplied. Both delivered every measured chat marker in order, without duplicates. A lower latency result in one workload does not establish CPU/RAM efficiency or whole-plugin superiority.

| Workload | Plugin | Observed/attempted | Median ms | p95 ms | Range ms |
| --- | --- | --- | ---: | ---: | ---: |
| Low-rate chat | DyDiscord | 40/40 | 329.9 | 439.4 | 258.5–829.0 |
| Low-rate chat | DiscordSRV | 40/40 | 313.2 | 472.8 | 238.9–675.4 |
| Three-message burst | DyDiscord | 18/18 | 972.8 | 2078.3 | 263.6–2078.3 |
| Three-message burst | DiscordSRV | 18/18 | 399.0 | 1163.1 | 254.1–1163.1 |
| Console (not comparable) | DyDiscord | 20/20 | 795.0 | 1546.8 | 246.2–2846.1 |
| Console (not comparable) | DiscordSRV | 0/20 | unmeasured | unmeasured | unmeasured–unmeasured |

`p95` uses nearest rank. With 18 burst samples it equals the maximum; this is a small local dataset, not a significance test. Failed/unobserved console samples have no latency value and must never be represented as zero or a speed advantage. DiscordSRV console markers were not observed by the helper; their absence has not been attributed to a confirmed plugin fault. Its configuration assigned the console channel. No comparable console speed claim follows.

## Procedure

- Same Windows 11 desktop, Intel Core i5-1240P, Java 21.0.12.1, Purpur 1.20.4 build 2176, 512 MB–1 GB heap, loopback-only isolated server and flat saved world. Original server and authentication settings were untouched. Offline BenchPlayer exists only in this isolated workload.
- Balanced A1 DyDiscord, B1 DiscordSRV, B2 DiscordSRV, A2 DyDiscord; one target plugin per start. A preceding six-message DyDiscord pilot succeeded and is excluded from the table.
- Per start: four excluded warmups spaced 1.33 s; twenty low-rate messages 1.37 s apart; three groups of three messages spaced 180 ms with 4 s between groups; ten console markers 1.31 s apart. A 10 s observation deadline applies to both plugins, with 10.5 s final drain.
- Rankless matched text `{name} » {message}` / `%username% » %message%`, no webhooks. Reverse chat, join/quit/death/achievement notices, panels, role sync, rewards and update checks disabled for the workload. No authorization/privacy guard removed. Spark background profiling and bStats disabled. Idle Gradle daemon remained; no builds/tests/plot rendering ran during samples. Desktop load and external network are not fully controlled.
- Passive test observer timestamps the real chat event and the target JDA MessageReceivedEvent using System.nanoTime in the same server JVM. Console timestamp is recorded immediately before logger output. It checks the expected destination channel, message order, duplicate and deadline. It does not substitute queue admission or HTTP completion for Discord receipt. Observer HTTP diagnostics go to a separate file to avoid relay feedback.
- Console relay defaults differ (DyDiscord 250 ms check; DiscordSRV 5 s flush). Even with complete receipts this would measure default user-visible delivery, not equal-policy implementation efficiency.
- Existing original guild commands were left unmanaged by the isolated DyDiscord database. Test-only channels were created and removed. Original command definitions and five owned IDs were repaired/verified after command IDs changed during comparison; no linked account rows were changed. Runtime credential copies were cleared and the isolated listener stopped. A bulk-restore 400 error is retained in local evidence; independent recovery succeeded.

## Artifacts and limits

- A1 DyDiscord: `6f34562c78d8b834c591fe88c646bd587825b4cc8d8243fffd69aece5f841cd9`.
- B1 DiscordSRV: `ef2fa1f2eb146c7c77412b7190a7dd33f1fc91282683fe45407739554c8aefef`.
- B2 DiscordSRV: `ef2fa1f2eb146c7c77412b7190a7dd33f1fc91282683fe45407739554c8aefef`.
- A2 DyDiscord: `6f34562c78d8b834c591fe88c646bd587825b4cc8d8243fffd69aece5f841cd9`.

Raw data: [samples.csv](samples.csv), [summary.json](summary.json). Missing rows are retained. Earlier attempts with REST timeouts or observer log feedback were excluded; they cannot substantiate latency. The final measured run's delivery results stand separately from its subsequently repaired cleanup failure.

The changed source wakes the outgoing worker on demand rather than polling, keeps bounded expiry even under long slowmode, and allows a bounded slash-registration rate-limit wait. These have deterministic regressions; no valid pre-change paired speed dataset was obtained. Do not claim an observed before/after speedup. The retained 500 ms post-send pacing contributes to burst delay; it was not removed to win a benchmark.

Discord → Minecraft human-message latency, human modal/leave-button interactions, giveaway visual rendering, live role/reward delivery, extended load and a full platform/version matrix were not measured here. Historical acceptance results belong to their earlier JARs.
