package dev.dydiscord;

import dev.dydiscord.chat.ChatBridge;
import dev.dydiscord.chat.ChatEvents;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.console.ConsoleBridge;
import dev.dydiscord.discord.Bot;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsoleChatBoundaryTest {
    private static final String CHANNEL = "423456789012345678";
    @TempDir Path folder;

    private final class ChatFixture implements AutoCloseable {
        final Configuration configuration = TestSupport.config(folder);
        final TestSupport.Time time = new TestSupport.Time();
        final DyDiscord plugin = mock(DyDiscord.class);
        final Bot bot = mock(Bot.class);
        final Server server = mock(Server.class);
        final Player player = mock(Player.class);
        final List<Runnable> mainTasks = new ArrayList<>();
        final ChatBridge bridge;
        final ChatEvents events;

        ChatFixture() throws Exception {
            configuration.change("bot.yml", "chat.channel-id", CHANNEL);
            configuration.change("bot.yml", "chat.minecraft-to-discord", true);
            configuration.change("bot.yml", "chat.events.join.enabled", true);
            when(plugin.settings()).thenAnswer(call -> configuration.active().settings());
            when(plugin.messages()).thenAnswer(call -> configuration.active().messages());
            when(plugin.clock()).thenReturn(time);
            when(plugin.bot()).thenReturn(bot);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.ready()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("chat-boundary-tests"));
            when(bot.usable()).thenReturn(true);
            when(bot.generation()).thenReturn(7L);
            when(bot.alive(7L)).thenReturn(true);
            when(bot.activeLanguage()).thenReturn("en");
            when(player.getName()).thenReturn("Alice");
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            when(player.hasPermission(anyString())).thenReturn(true);
            when(player.getMetadata(anyString())).thenReturn(List.of());
            doReturn(List.of(player)).when(server).getOnlinePlayers();
            doAnswer(call -> { mainTasks.add(call.getArgument(0)); return null; }).when(plugin).onMain(any());
            bridge = new ChatBridge(plugin);
            when(plugin.chat()).thenReturn(bridge);
            events = new ChatEvents(plugin);
        }

        void schedulePublicChat() {
            events.legacy(new AsyncPlayerChatEvent(true, player, "Before the boundary", new HashSet<>(List.of(player))));
            assertEquals(1, mainTasks.size());
            assertEquals(0, bridge.queued());
        }

        void runMainTasks() { List.copyOf(mainTasks).forEach(Runnable::run); }
        @Override public void close() { bridge.close(); }
    }

    @Test void scheduledMinecraftChatCannotMoveToReloadedChannel() throws Exception {
        try (ChatFixture fixture = new ChatFixture()) {
            fixture.schedulePublicChat();
            fixture.configuration.change("bot.yml", "chat.channel-id", "723456789012345678");
            fixture.bridge.invalidate();
            fixture.runMainTasks();
            assertEquals(0, fixture.bridge.queued(), "Old events must not be admitted under the new channel policy");
        }
    }

    @Test void scheduledMinecraftChatCannotSurviveSameSettingsInvalidation() throws Exception {
        try (ChatFixture fixture = new ChatFixture()) {
            fixture.schedulePublicChat();
            fixture.bridge.invalidate();
            fixture.runMainTasks();
            assertEquals(0, fixture.bridge.queued(), "Disconnect invalidation also covers work awaiting the main thread");
        }
    }

    @Test void scheduledMinecraftChatCannotAcquireNewBotGeneration() throws Exception {
        try (ChatFixture fixture = new ChatFixture()) {
            fixture.schedulePublicChat();
            when(fixture.bot.alive(7L)).thenReturn(false);
            when(fixture.bot.alive(8L)).thenReturn(true);
            when(fixture.bot.generation()).thenReturn(8L);
            fixture.bridge.invalidate();
            fixture.runMainTasks();
            assertEquals(0, fixture.bridge.queued(), "Pre-restart events must not inherit the replacement bot session");
        }
    }

    @Test void scheduledMinecraftChatExpiresFromEventTime() throws Exception {
        try (ChatFixture fixture = new ChatFixture()) {
            fixture.schedulePublicChat();
            fixture.time.advance(6);
            fixture.runMainTasks();
            assertEquals(0, fixture.bridge.queued(), "Main-thread delay must not reset the five-second message lifetime");
        }
    }
    @Test void unknownSynchronousChatHandlerPausesPublicForwarding() throws Exception {
        var handlers=org.bukkit.event.player.PlayerChatEvent.getHandlerList();var other=mock(org.bukkit.plugin.Plugin.class);
        var listener=new org.bukkit.plugin.RegisteredListener(new org.bukkit.event.Listener(){},(l,e)->{},org.bukkit.event.EventPriority.NORMAL,other,false);
        handlers.register(listener);
        try(ChatFixture fixture=new ChatFixture()){
            fixture.schedulePublicChat();fixture.runMainTasks();assertEquals(0,fixture.bridge.queued());
        }finally{handlers.unregister(listener);}
    }
    @Test void freshScheduledPublicChatStillDeliversWithOriginalExpiry() throws Exception {
        try(ChatFixture fixture=new ChatFixture()){
            fixture.schedulePublicChat();fixture.time.advance(4);fixture.runMainTasks();assertEquals(1,fixture.bridge.queued());fixture.time.advance(1);fixture.bridge.flush();
            verify(fixture.bot,never()).sendChat(any(),any());assertEquals(0,fixture.bridge.queued());
        }
    }

    @Test void scheduledJoinCannotMoveToReloadedChannel() throws Exception {
        try (ChatFixture fixture = new ChatFixture()) {
            fixture.events.join(new PlayerJoinEvent(fixture.player, "Alice joined"));
            assertEquals(1, fixture.mainTasks.size());
            fixture.configuration.change("bot.yml", "chat.channel-id", "723456789012345678");
            fixture.bridge.invalidate();
            fixture.runMainTasks();
            assertEquals(0, fixture.bridge.queued(), "Lifecycle events also retain their original admission boundary");
        }
    }

    @Test void invalidatedPermissionCheckCannotConsumeNewPolicyLogs() throws Exception {
        Configuration configuration = TestSupport.config(folder);
        configuration.change("bot.yml", "console.enabled", true);
        configuration.change("bot.yml", "console.channel-id", "323456789012345678");
        DyDiscord plugin = mock(DyDiscord.class);
        Bot bot = mock(Bot.class);
        when(plugin.settings()).thenAnswer(call -> configuration.active().settings());
        when(plugin.clock()).thenReturn(new TestSupport.Time());
        when(plugin.ready()).thenReturn(true);
        when(plugin.bot()).thenReturn(bot);
        when(bot.usable()).thenReturn(true);
        when(bot.generation()).thenReturn(7L);
        when(bot.alive(7L)).thenReturn(true);
        TextChannel channel = mock(TextChannel.class);
        MessageCreateAction action = mock(MessageCreateAction.class, RETURNS_SELF);
        when(channel.sendMessage(anyString())).thenReturn(action);
        when(action.submit()).thenReturn(CompletableFuture.completedFuture(mock(Message.class)));
        when(bot.channel("323456789012345678")).thenReturn(channel);
        doAnswer(call -> {
            CompletableFuture<Object> result = new CompletableFuture<>();
            try { result.complete(((Callable<?>)call.getArgument(0)).call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
            return result;
        }).when(plugin).submit(any());
        try (ConsoleBridge bridge = new ConsoleBridge(plugin)) {
            bridge.buffer().offer("Before disconnect");
            doAnswer(call -> {
                // Gateway disconnect invalidates independently of the single I/O executor.
                bridge.invalidate();
                bridge.buffer().offer("After invalidation");
                return null;
            }).when(bot).validateChannel("323456789012345678", true);
            var flush = ConsoleBridge.class.getDeclaredMethod("flush");
            flush.setAccessible(true);
            flush.invoke(bridge);
            assertEquals(1, bridge.buffer().size(), "An obsolete flush must not drain replacement-policy logs");
            assertTrue(bridge.buffer().drain().contains("After invalidation"));
        }
    }
}
