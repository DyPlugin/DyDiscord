# DyDiscord 1.3.0

This release makes the chat bridge feel at home in Discord: Minecraft chat can show each player's own name and head, and advancements and first joins can be announced. Account links, settings and custom translations are kept, and every new option starts off.

## Added

- **Player names and heads in Discord.** With `chat.webhook.enabled`, Minecraft chat is posted through a webhook the bot creates in the chat channel, under the player's name (and rank) with their head as the picture.
  - The bot needs the Manage Webhooks permission in the chat channel. Without it, or if Discord refuses the webhook, chat is sent as the bot as before and the console says why once.
  - `chat.webhook.avatar-url` sets the head image; `{uuid}` and `{name}` are replaced. The default uses mc-heads.net.
  - Mentions stay disabled, and webhook messages are never relayed back into Minecraft.
- **Advancements.** `chat.events.advancement` announces the advancements Minecraft itself announces in chat, while the `announceAdvancements` game rule is on. Recipes and other advancements Minecraft keeps quiet are skipped.
- **First joins.** `chat.events.first-join` welcomes players who join for the first time, in place of the normal join message.
- Both events have their own colour and Discord template (`chat-advancement`, `chat-first-join`) in every language.

## Upgrading

Stop the server, back up the JAR and the `plugins/DyDiscord` folder, then replace the JAR with **one** 1.3.0 package (`DyDiscord-1.3.0.jar` or `DyDiscord-1.3.0-standalone.jar`). New settings and messages are added; links, settings and translations are kept.

Full changelog: https://dynastyplugins.com/dydiscord/versions/1.3.0
