package dev.dydiscord;

import dev.dydiscord.chat.ChatBridge;
import dev.dydiscord.chat.ChatText;
import dev.dydiscord.config.ChatSettings;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.discord.Bot;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.IWebhookContainerUnion;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Release130Test {
    @TempDir Path folder;

    @Test void newChatFeaturesAreOffByDefaultAndTheAvatarMustBeHttps() throws Exception {
        Configuration config = TestSupport.config(folder);
        ChatSettings chat = config.active().settings().chat();
        assertFalse(chat.webhook());
        assertFalse(chat.firstJoin());
        assertFalse(chat.advancement());
        assertEquals(ChatSettings.AVATAR, chat.avatar());
        assertEquals(0xFFAA00, chat.advancementColor());
        assertThrows(Exception.class, () -> config.change("bot.yml", "chat.webhook.avatar-url", "http://example.com/{uuid}.png"));
        config.change("bot.yml", "chat.webhook.enabled", true);
        config.change("bot.yml", "chat.events.advancement.enabled", true);
        assertTrue(config.active().settings().chat().webhook());
        assertTrue(config.active().settings().chat().advancement());
    }

    @Test void webhookNameAndAvatarAreSafe() {
        assertEquals("Admin Alex", ChatText.webhookName("&6Admin", "Alex"));
        assertEquals("Dc_fan", ChatText.webhookName("", "Discord_fan"), "Discord refuses names with discord in them");
        assertTrue(ChatText.webhookName("", "x".repeat(200)).length() <= 80);
        UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertEquals("https://mc-heads.net/avatar/123e4567-e89b-12d3-a456-426614174000/64", ChatText.avatar(ChatSettings.AVATAR, id, "Alex"));
        assertEquals("https://example.com/Alex%20B.png", ChatText.avatar("https://example.com/{name}.png", id, "Alex B"));
        assertEquals("Alex has made the advancement **Stone Age**.",
                ChatText.advancement("{name} has made the advancement **{advancement}**.", "Alex", "§aStone Age"));
    }

    private record Fixture(Bot bot, TextChannel channel, SelfMember self, MessageCreateAction botMessage) {}

    private Fixture fixture(Configuration config) throws Exception {
        DyDiscord plugin = mock(DyDiscord.class);
        when(plugin.settings()).thenAnswer(i -> config.active().settings());
        when(plugin.messages()).thenAnswer(i -> config.active().messages());
        when(plugin.clock()).thenReturn(new TestSupport.Time());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("webhook-tests"));
        Store store = mock(Store.class);
        when(store.panel()).thenReturn(Optional.empty());
        when(plugin.store()).thenReturn(store);
        Bot bot = spy(new Bot(plugin));
        TextChannel channel = mock(TextChannel.class);
        when(channel.getId()).thenReturn("423456789012345678");
        Guild guild = mock(Guild.class);
        SelfMember self = mock(SelfMember.class);
        when(self.getId()).thenReturn("1");
        when(channel.getGuild()).thenReturn(guild);
        when(guild.getSelfMember()).thenReturn(self);
        when(self.hasPermission(eq(channel), any(Permission[].class))).thenReturn(true);
        doReturn(channel).when(bot).channel(anyString());
        MessageCreateAction action = mock(MessageCreateAction.class, RETURNS_SELF);
        when(action.submit()).thenReturn(CompletableFuture.completedFuture(mock(Message.class)));
        when(channel.sendMessage(anyString())).thenReturn(action);
        return new Fixture(bot, channel, self, action);
    }

    @SuppressWarnings("unchecked")
    @Test void playerChatGoesThroughOneReusedWebhookWithTheirNameAndHead() throws Exception {
        Configuration config = TestSupport.config(folder);
        config.change("bot.yml", "chat.channel-id", "423456789012345678");
        Fixture f = fixture(config);
        RestAction<List<Webhook>> list = mock(RestAction.class, RETURNS_SELF);
        when(list.submit()).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(f.channel().retrieveWebhooks()).thenReturn(list);
        Webhook hook = mock(Webhook.class);
        IWebhookContainerUnion home = mock(IWebhookContainerUnion.class);
        when(home.getId()).thenReturn("423456789012345678");
        when(hook.getChannel()).thenReturn(home);
        WebhookAction create = mock(WebhookAction.class, RETURNS_SELF);
        when(create.submit()).thenReturn(CompletableFuture.completedFuture(hook));
        when(f.channel().createWebhook("DyDiscord")).thenReturn(create);
        WebhookMessageCreateAction<Message> send = mock(WebhookMessageCreateAction.class, RETURNS_SELF);
        when(send.submit()).thenReturn(CompletableFuture.completedFuture(mock(Message.class)));
        when(hook.sendMessage(anyString())).thenReturn(send);

        ChatBridge.Outgoing message = new ChatBridge.Outgoing("**Admin** Alex » hi", null, 0, 0, Instant.MAX, "Admin Alex", "https://mc-heads.net/avatar/x/64", "hi");
        f.bot().sendChat(message, () -> true);
        f.bot().sendChat(message, () -> true);
        verify(f.channel(), times(1)).createWebhook("DyDiscord");
        verify(hook, times(2)).sendMessage("hi");
        verify(send, times(2)).setUsername("Admin Alex");
        verify(send, times(2)).setAvatarUrl("https://mc-heads.net/avatar/x/64");
        verify(send, times(2)).setAllowedMentions(List.of());
        verify(f.channel(), never()).sendMessage(anyString());
    }

    @Test void withoutManageWebhooksChatIsSentAsTheBot() throws Exception {
        Configuration config = TestSupport.config(folder);
        config.change("bot.yml", "chat.channel-id", "423456789012345678");
        Fixture f = fixture(config);
        when(f.self().hasPermission(f.channel(), Permission.MANAGE_WEBHOOKS)).thenReturn(false);
        f.bot().sendChat(new ChatBridge.Outgoing("Alex » hi", null, 0, 0, Instant.MAX, "Alex", ChatSettings.AVATAR, "hi"), () -> true);
        verify(f.channel()).sendMessage("Alex » hi");
        verify(f.channel(), never()).retrieveWebhooks();
    }

    @Test void webhookFailureFallsBackToTheBot() throws Exception {
        Configuration config = TestSupport.config(folder);
        config.change("bot.yml", "chat.channel-id", "423456789012345678");
        Fixture f = fixture(config);
        when(f.channel().retrieveWebhooks()).thenThrow(new IllegalStateException("missing access"));
        f.bot().sendChat(new ChatBridge.Outgoing("Alex » hi", null, 0, 0, Instant.MAX, "Alex", ChatSettings.AVATAR, "hi"), () -> true);
        verify(f.channel()).sendMessage("Alex » hi");
    }
}
