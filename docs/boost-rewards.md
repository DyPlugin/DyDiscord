# Discord boost rewards

The research included DiscordSRV's documented boost start/end event examples in
[alerts.yml](https://docs.discordsrv.com/alerts/) and its
[group synchronization settings](https://docs.discordsrv.com/synchronization/).
DyDiscord uses its own bounded reconciliation and durable command receipts;
it does not copy the alert configuration or add a DiscordSRV dependency.

DyDiscord v1.0.1-BETA can run owner-configured Minecraft console commands for
a linked account currently boosting the configured Discord guild. Use your
installed rank, economy or site-credit plugin's documented commands. DyDiscord
does not bundle those providers, call an arbitrary website API or request their
credentials.

## Configuration

The shipped `bot.yml` starts with:

```yaml
boost-rewards:
  enabled: false
  commands: []
  remove-commands: []
  recheck-seconds: 300
  cooldown-hours: 168
```

| Setting | Behavior |
| --- | --- |
| `enabled` | Explicit opt-in; disabling stops reconciliation without reversing existing rewards |
| `commands` | Up to 10 award commands, each at most 256 characters |
| `remove-commands` | Optional commands for a recorded boost's end or removed account link; same limits |
| `recheck-seconds` | Periodic fresh-member checks; default 300, range 30–3600 seconds |
| `cooldown-hours` | Minimum interval between claims for the same guild/Discord account; default/minimum 168, maximum 8760 hours |

Only `{player}` (the linked Minecraft name) and `{uuid}` are supported. Every
command must target at least one of them. A leading `/` is removed. Multiline,
control-character and unknown-placeholder templates are rejected. Direct
dangerous command roots such as `op`, `execute` and `sudo` are rejected, but
this filter is not a general audit of other plugins' command capabilities.
The commands run with server console authority; the server owner must choose
and test the provider commands and affected rank group deliberately.

Configure commands while the feature is disabled and verify the actual provider
command locally against a test account. Then reload with
`/dydiscord admin reload`, enable the setting and restart the bot if the requested
intents changed. Enabling can reward accounts already linked and currently
boosting; it is not restricted to boosts started after activation.

Boost rewards request **Server Members** intent. Enable that intent in the
Developer Portal for prompt member-event updates. If it is unavailable,
periodic fresh REST membership checks remain active. Lookup errors preserve
uncertain state; they are not treated as a boost ending. Bukkit command dispatch
runs on the server thread and rechecks the captured configuration, link and bot
session before execution.

## Temporary LuckPerms rank

Create the `booster` group and choose its permissions first. To grant it for
30 days, set:

```yaml
boost-rewards:
  enabled: true
  commands:
    - "lp user {uuid} parent addtemp booster 30d"
  remove-commands: []
  recheck-seconds: 300
  cooldown-hours: 168
```

LuckPerms owns the expiry; DyDiscord does not implement a second rank timer.
Offline-user membership and expired temporary inheritance are handled through
the real LuckPerms adapter. If you want the temporary membership removed when
boosting ends, use the matching provider removal command deliberately; leaving
`remove-commands` empty lets the 30-day rank expire normally. LuckPerms documents
temporary membership in its [parent command reference](https://luckperms.net/wiki/Parent-Commands).

An optional `roles.groups` rule can map the existing `booster` group to a
nonprivileged Discord role. This remains Minecraft → Discord synchronization;
arbitrary Discord role membership does not grant Minecraft groups or OP.
Configure the bot's role hierarchy and Manage Roles permission separately.
See [role synchronization](role-sync.md).

For a permanent rank, use the provider's permanent-add command and, if wanted,
its matching remove command. Money/site credit usually has no removal command;
use the provider's documented console syntax rather than assuming a universal
economy or website command. A successful Bukkit dispatch is not proof that a
provider credited money or completed an external transaction.

## Reward periods and durable records

A reward key is the configured guild, Discord account and member's
`premium_since` timestamp. Discord defines that field as the time the member
started boosting the guild. It does not report individual paid boost purchases;
DyDiscord awards once per observed continuous member boost period, not once per
purchased boost. An unchanged period is not rewarded again after seven days.
If a new period begins, the cooldown still applies to that Discord account.
See [Discord's guild member contract](https://docs.discord.com/developers/resources/guild#guild-member-object).

Receipts are kept in `plugins/DyDiscord/data/boosts.db`, schema **1**, independently
of the main schema-3 account database. They retain guild/Discord IDs, the original
Minecraft UUID/name, boost-period and claim times, and award/removal states even
after an account is unlinked; those receipts prevent repeating an external reward.
Each award is committed as `CLAIMED`
before commands are dispatched. A completed dispatch is recorded as `DISPATCHED`;
failure, timeout or uncertainty becomes `UNKNOWN`. On reopening, interrupted
`CLAIMED` attempts become `UNKNOWN` and are never automatically replayed.
`DISPATCHED` records acceptance by Bukkit, not a confirmed provider outcome.

Optional removal has its own durable claim and runs once for a recorded period
when its boost ends, the member leaves or its linked Minecraft account changes.
It is reconciled only while the feature is enabled. Removal templates added
later can apply to earlier receipts that have no recorded removal attempt.
The recorded UUID/name identifies the original award target. A missed prior
period is not revoked merely because the same linked member is now boosting in
a later period. A command sequence can partly run before failing; there is no
automatic rollback or replay of the remaining commands.

Keep this database and the provider's own records. Deleting receipts or restoring
an old `boosts.db` can repeat a reward already credited externally. Before any
restore, disable boost rewards and reconcile reward/removal attempts with the
provider records. Restoring DyDiscord files cannot undo money, site credit,
Minecraft ranks or Discord roles. See [backup and rollback](upgrade-and-rollback.md).

## Research and verification scope

DiscordSRV's official [synchronization settings](https://docs.discordsrv.com/synchronization/)
and [LuckPerms contexts](https://docs.discordsrv.com/lp-contexts/) were reviewed
for group/role and boosting behavior. DyDiscord uses explicit group rules and
durable award receipts; DiscordSRV is not a dependency and its contexts are not
implemented here.

The isolated real LuckPerms and real JDA role checks, tested artifact and final
results are recorded in [continuation verification](continuation-verification-20261012.md).
These role checks do not prove an actual paid boost, economy/site transaction,
human modal interaction, visual client or Java 17 runtime scenario. Each needs
separate evidence; Java 17 bytecode is only the compilation target.

## Türkçe kısa kurulum

`bot.yml` içindeki `boost-rewards` ilk kurulumda kapalıdır. Kendi para/site/rütbe
eklentinize ait konsol komutlarını `commands` listesine yazın; yalnızca
`{player}` ve `{uuid}` kullanılır. Hazır para/site sağlayıcısı yoktur. Mevcut bir
LuckPerms grubu için `lp user {uuid} parent addtemp booster 30d` 30 günlük rütbe
verir; oyuncu çevrimdışıyken de süreyi LuckPerms yönetir. Ayrı `roles.groups`
kuralı bu grubu Discord rolüne bağlayabilir.

Ödül kişinin kesintisiz boost dönemi için bir kez kaydedilir; satın aldığı her
boost başına veya her hafta verilmez. Bekleme en az 168 saattir. `remove-commands`
isteğe bağlıdır; boost bittiğinde veya eşleme kaldırıldığında kayıtlı ödül için
bir kez çalışabilir. Özelliği kapatmak eski ödülleri geri almaz. Belirsiz komut
kayıtları tekrar denenmez; kısmi işlem varsa sağlayıcının kaydını kontrol edin.

`data/boosts.db` dosyasını diğer verilerle birlikte yedekleyin. Eski ödül kaydını
geri yüklemek dış krediyi tekrar verdirebilir. Geri yüklemeden önce özelliği
kapatıp para/site/rütbe kayıtlarını karşılaştırın. DyDiscord yedeğine dönmek,
verilmiş krediyi veya değiştirilmiş rolü geri almaz.
