# Community features — DyDiscord 1.0.1

English is the default; Turkish commands and ASCII aliases remain accepted.
Optional rewards and giveaways start
**disabled**. The account panel and console-output contract are unchanged.

## Public Discord commands

| Command | Behavior |
| --- | --- |
| `/server`, `/sunucu` | Visible online count, capacity, version and configured custom fields |
| `/players`, `/oyuncular` | Visible online names; at most 100 listed, total count retained |
| `/ip`, `/adres` | Owner-configured Java and optional Bedrock connection addresses |
| `/links`, `/bağlantılar` | Up to 10 named HTTPS links |

The matching `dy!` commands are available when Message Content is enabled.
`config.yml: community` supplies the public addresses, links and extra fields;
no machine/network address is detected. A Bedrock address does not install or
prove a Bedrock bridge. `bot.yml: community.information-channel-ids` optionally
restricts these four information commands to selected regular text channels.
Account interactions remain private and are not moved into a public channel.

## Player and administrator chat controls

`/dydiscord chat on|off` (`/dydiscord sohbet aç|kapat`) controls incoming Discord visibility only, protected by `dydiscord.chat.preferences`. The stored preference persists. Outgoing sharing and separate name-sound preferences are removed; old database columns are retained for rollback but ignored by those runtime paths.

Administrators use `/dyda chat discord-minecraft on|off`, `minecraft-discord on|off`, `both on|off` and `channel <id>`. The long `/dydiscord admin` forms still work. State changes persist in `bot.yml`; no-op requests do not rewrite settings. Both-direction edits are validated and written atomically. Direction changes invalidate queued traffic. Moderation/privacy, duplicate, queue lifetime and pacing protections remain.

## Features removed in 1.0.1

Manual/scheduled announcements, startup notices and their settings/permissions are removed. The normal bounded yellow shutdown embed remains. Advanced role mappings, console authorization and reward commands are configured in YAML. The former report/setup/config-validate/translations-validate/reward-preview commands are removed; status/doctor and validated reload remain.

## Rewards and updates

See [first-link rewards](link-rewards.md), [boost rewards](boost-rewards.md) and [role synchronization](role-sync.md). Rewards are optional; command dispatch is not proof of an external balance change. Private linking success does not append a second welcome. Role preview/synchronization and explicitly confirmed cleanup remain available.

`updates.notify` selects console/players/both; player notices need `dydiscord.admin.update`. `updates.include-beta` opts into future betas; superseded historical betas are excluded from stable recommendations.

## Giveaways and permissions

See [giveaways](giveaways.md) for entry buttons, persistent deadlines, conditions,
limits, manual finish/cancel and same-message result recovery. Prizes are delivered
by the owner; giveaways do not execute reward commands or transfer money.

Each administration operation has its own `dydiscord.admin.*` leaf in
`plugin.yml`. Help/TAB are filtered by those leaves; explicit deny still applies
to OP. Chat controls use `dydiscord.chat.preferences`, inherited by the player group.
The administration branch stays `admin` in both languages.

All new options apply on validated reload unless the existing bot/intent rules
require restart. Existing custom translations/comments and account links survive.
New local receipt/preference/giveaway files must be included in stopped-server
backups; restoring local data does not undo Discord roles or external rewards.

