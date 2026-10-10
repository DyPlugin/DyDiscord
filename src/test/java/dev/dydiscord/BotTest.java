package dev.dydiscord;
import dev.dydiscord.discord.*;
import dev.dydiscord.storage.Store;
import dev.dydiscord.config.*;
import net.dv8tion.jda.api.requests.RestAction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BotTest {
    @TempDir Path folder;
    private DyDiscord host()throws Exception{DyDiscord plugin=mock(DyDiscord.class);Store store=mock(Store.class);when(store.panel()).thenReturn(Optional.empty());when(plugin.store()).thenReturn(store);when(plugin.settings()).thenReturn(TestSupport.settings());when(plugin.clock()).thenReturn(new TestSupport.Time());when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));return plugin;}
    @Test void queuedReadyCallbackCannotReviveStoppedBot()throws Exception{
        DyDiscord host=host();Bot bot=new Bot(host);when(host.ready()).thenReturn(true);
        java.util.concurrent.atomic.AtomicReference<Callable<?>> queued=new java.util.concurrent.atomic.AtomicReference<>();
        when(host.submit(any())).thenAnswer(call->{queued.set(call.getArgument(0));return new CompletableFuture<>();});
        Class<?> type=Class.forName("dev.dydiscord.discord.Bot$Events");var ctor=type.getDeclaredConstructor(Bot.class,long.class);ctor.setAccessible(true);
        var listener=(net.dv8tion.jda.api.hooks.ListenerAdapter)ctor.newInstance(bot,bot.generation());
        listener.onReady(mock(net.dv8tion.jda.api.events.session.ReadyEvent.class));assertNotNull(queued.get());
        bot.stop();assertDoesNotThrow(()->queued.get().call());assertEquals(Bot.State.DISABLED,bot.state());verify(host,never()).error(any());
    }
    @Test void stopAwaitsDetachedJdaOutsideBotMonitor()throws Exception{
        DyDiscord host=host();Bot bot=new Bot(host);when(host.ready()).thenReturn(true);
        net.dv8tion.jda.api.JDA previous=mock(net.dv8tion.jda.api.JDA.class);when(previous.getRegisteredListeners()).thenReturn(List.of());
        var field=Bot.class.getDeclaredField("jda");field.setAccessible(true);field.set(bot,previous);
        when(previous.awaitShutdown(4,TimeUnit.SECONDS)).thenAnswer(call->{
            assertFalse(Thread.holdsLock(bot),"Waiting while holding Bot monitor can block an in-flight listener");
            assertNull(field.get(bot));assertFalse(bot.alive(0));assertEquals(Bot.State.STOPPING,bot.state());return true;
        });
        bot.stop();var sequence=inOrder(previous);sequence.verify(previous).shutdownNow();sequence.verify(previous).awaitShutdown(4,TimeUnit.SECONDS);
        assertEquals(Bot.State.DISABLED,bot.state());assertFalse(bot.usable());
    }
    @Test void closingHostCannotRestartNewJdaAfterOldSessionStops()throws Exception{
        DyDiscord host=host();Configuration config=TestSupport.config(folder);
        config.change("bot.yml","token","synthetic-test-token");config.change("bot.yml","guild-id","123456789012345678");config.change("bot.yml","enabled",true);
        when(host.settings()).thenReturn(config.active().settings());when(host.ready()).thenReturn(false);
        Bot bot=new Bot(host);net.dv8tion.jda.api.JDA previous=mock(net.dv8tion.jda.api.JDA.class);when(previous.getRegisteredListeners()).thenReturn(List.of());when(previous.awaitShutdown(4,TimeUnit.SECONDS)).thenReturn(true);
        var field=Bot.class.getDeclaredField("jda");field.setAccessible(true);field.set(bot,previous);
        try(var builders=mockStatic(net.dv8tion.jda.api.JDABuilder.class)){bot.restart();builders.verifyNoInteractions();}
        verify(previous).shutdownNow();verify(previous).awaitShutdown(4,TimeUnit.SECONDS);assertNull(field.get(bot));assertEquals(Bot.State.DISABLED,bot.state());
    }
    @Test void disabledBotRestartCyclesRemainDisabledWithoutNetworkSession()throws Exception{Bot bot=new Bot(host());for(int i=0;i<50;i++){bot.restart();assertEquals(Bot.State.DISABLED,bot.state());assertFalse(bot.usable());assertFalse(bot.alive(bot.generation()));}}
    @Test void apiFailuresAreUnwrappedSo404CanReachPanelRecovery()throws Exception{Bot bot=new Bot(host());@SuppressWarnings("unchecked") RestAction<String> action=mock(RestAction.class);when(action.timeout(10,TimeUnit.SECONDS)).thenReturn(action);java.io.IOException failure=new java.io.IOException("API failure");when(action.submit()).thenReturn(CompletableFuture.failedFuture(failure));assertSame(failure,assertThrows(java.io.IOException.class,()->bot.await(action)));}
    @Test void publicPrefixResponsesHaveBoundedAdmission(){TestSupport.Time clock=new TestSupport.Time();IngressLimit limiter=new IngressLimit(clock);assertTrue(limiter.admit("a"));assertFalse(limiter.admit("a"));for(int i=0;i<119;i++)assertTrue(limiter.admit("user"+i));assertFalse(limiter.admit("extra"));clock.advance(60);assertTrue(limiter.admit("a"));}
    @Test void disabledBotAcceptsInitialConnectionAndChannelSetupWithoutNetwork()throws Exception{
        Configuration c=TestSupport.config(folder);Settings before=c.active().settings();
        Files.writeString(folder.resolve("bot.yml"),"enabled: false\ntoken: 'synthetic-test-token'\nguild-id: '123456789012345678'\npanel:\n  channel-id: '223456789012345678'\nconsole:\n  channel-id: '323456789012345678'\n  commands:\n    allowed-role-ids: ['423456789012345678']\n");
        Bot bot=new Bot(host());Configuration.Snapshot next=c.preview();
        assertDoesNotThrow(()->bot.validateChanges(before,next.settings()));
        c.apply(next);assertFalse(c.active().settings().botEnabled());assertEquals("123456789012345678",c.active().settings().guild());assertFalse(bot.usable());
    }
    @Test void disconnectedConsoleSetupRemainsPendingUntilLiveValidation()throws Exception{
        Configuration c=TestSupport.config(folder);Settings before=c.active().settings();
        Files.writeString(folder.resolve("bot.yml"),"enabled: true\ntoken: 'synthetic-test-token'\nguild-id: '123456789012345678'\nconsole:\n  enabled: true\n  channel-id: '323456789012345678'\n  commands:\n    enabled: true\n");
        Bot bot=new Bot(host());Settings next=c.preview().settings();
        assertDoesNotThrow(()->bot.validateChanges(before,next));assertFalse(bot.usable());assertEquals(Bot.State.DISABLED,bot.state());
        assertThrows(IllegalStateException.class,()->bot.validateChannel(next.consoleChannel(),true));
    }
    @Test void connectedBotRejectsPolicyForDifferentGuildAndPreservesConfiguration()throws Exception{
        Configuration c=TestSupport.config(folder);Settings applied=c.active().settings();
        c.change("bot.yml","guild-id","123456789012345678");Settings before=c.active().settings();String contents=Files.readString(folder.resolve("bot.yml"));
        Bot bot=mock(Bot.class);when(bot.usable()).thenReturn(true);var field=Bot.class.getDeclaredField("applied");field.setAccessible(true);field.set(bot,applied);doCallRealMethod().when(bot).validateChanges(any(),any());
        assertThrows(java.io.IOException.class,()->c.change("bot.yml","panel.channel-id","223456789012345678",next->bot.validateChanges(before,next)));
        assertEquals(contents,Files.readString(folder.resolve("bot.yml")));assertSame(before,c.active().settings());
    }
}
