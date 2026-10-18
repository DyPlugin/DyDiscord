# DyDiscord–DiscordSRV performance comparison

Status: completed scoped delivery runs. See [actual data and limitations](performance/2026-10-18/README.md). Results are mixed and do not support overall speed superiority. Earlier failed starts and instrumentation attempts remain excluded.

## Reference charts

- [DyClaim on Modrinth](https://modrinth.com/plugin/dyclaim) links a specific
  block-break processing benchmark, not whole-plugin CPU/RAM usage.
- DyRTP's local `docs/betterrtp-comparison.md` and
  `docs/images/DyRTP-vs-BetterRTP-en.png` measure command-to-teleport durations.
  Neither dataset can be reused as evidence about DyDiscord.

Use the same restrained chart style: light background, a shared linear zero-based
scale, DyDiscord blue and comparison gray, actual versions, units, sample counts,
test date and a short limitation below the plot. Render measured data with a
plotting library, not an image generator. Publish raw samples and the procedure
beside any eventual chart.

## Isolated procedure

1. Use a new loopback-only test server folder, the same server build, Java,
   hardware, heap and saved world snapshot for both plugins. Install one target
   plugin per start. Preserve original production and sibling-project files.
2. Verify the actual JAR version and hash of local DyDiscord 1.0.2 and the
   official DiscordSRV 1.30.5 release. Keep them distinct from previous releases.
3. Use a bot reserved for testing and a designated test guild/channel. Do not
   share a running bot session, change production channels or publish tokens.
   A configured test credential path is required before this stage can run.
4. Match text-only formats and enabled features. Disable unrelated optional
   work for both. Keep authorization, mention suppression and admission checks.
   Do not remove DyDiscord privacy checks to manufacture a faster result.
5. Measure Minecraft → Discord delivery from an accepted real player chat event
   to its uniquely marked message being observed in Discord. Use a monotonic
   clock in one measurement process wherever possible. Record any additional
   observer/polling latency and the exact boundaries. Async task submission or
   queue admission alone is not delivery.
6. Measure real console-output → Discord delivery separately. Do not confuse
   remote command authorization time with ordinary log relay time.
7. Run at least two starts per plugin in balanced A–B–B–A order. Use identical
   warmup and input schedules, first a normal low-rate stream and then a bounded
   short burst. Check successful receipts and order for every marked message.
   Respect Discord rate limits; do not run synthetic flood traffic.
8. Keep every sample, timeout, dropped message, rate-limit response and failed
   start. Report median, p95, observed range, successful/attempted count and
   configuration. Never let rejection or dropped traffic count as fast delivery.
9. Discord → Minecraft requires new human messages for the actual supported
   path. Bot/webhook traffic is deliberately rejected and cannot substitute for
   a human workload. Do not use an automated Discord human token. If not tested,
   mark this direction unmeasured.
10. Profile CPU/allocation separately if a bottleneck is reproduced. A lower
    latency number does not prove lower CPU, RAM or overall superiority. Fix only
    demonstrated DyDiscord bottlenecks, retain regression coverage and rerun the
    same workload after the correction.

## Publication decision

If DyDiscord has a repeatable advantage, describe only the measured scenarios
with enough context to reproduce them. Mixed results must remain visible.
Without comparable successful-delivery samples, no speed-superiority chart or
ratio can be claimed. No release, platform edit or production installation is
part of measurement preparation.
