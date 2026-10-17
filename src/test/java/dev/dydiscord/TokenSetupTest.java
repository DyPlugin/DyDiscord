package dev.dydiscord;

import dev.dydiscord.command.TokenSetup;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.console.Audit;
import dev.dydiscord.discord.Bot;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenSetupTest {
    @TempDir Path folder;
    DyDiscord plugin;Configuration config;Player player;TokenSetup setup;
    static final String TOKEN="synthetic_"+"a".repeat(40);
    @BeforeEach void before()throws Exception{
        config=TestSupport.config(folder);plugin=mock(DyDiscord.class);player=mock(Player.class);
        when(plugin.configuration()).thenReturn(config);when(plugin.settings()).thenAnswer(c->config.active().settings());when(plugin.ready()).thenReturn(true);when(plugin.clock()).thenReturn(new TestSupport.Time());
        when(plugin.bot()).thenReturn(mock(Bot.class));when(plugin.audit()).thenReturn(mock(Audit.class));
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);when(player.hasPermission(TokenSetup.PERMISSION)).thenReturn(true);
        var server=mock(org.bukkit.Server.class);when(plugin.getServer()).thenReturn(server);when(player.getServer()).thenReturn(server);doReturn(List.of(player)).when(server).getOnlinePlayers();when(server.getPlayer(player.getUniqueId())).thenReturn(player);
        doAnswer(c->((Callable<?>)c.getArgument(0)).call()).when(plugin).mainValue(any());
        doAnswer(c->{try{return CompletableFuture.completedFuture(((Callable<?>)c.getArgument(0)).call());}catch(Exception e){return CompletableFuture.failedFuture(e);}}).when(plugin).submit(any());
        setup=new TokenSetup(plugin);
    }
    @Test void pasteIsCancelledScrubbedAndCommittedWithoutEcho()throws Exception{
        setup.open(player);var event=new PlayerCommandPreprocessEvent(player,"/"+TOKEN);setup.preprocess(event);
        assertTrue(event.isCancelled());assertEquals("/dydiscord-token-hidden",event.getMessage());assertEquals(TOKEN,plugin.settings().token());verify(plugin.bot()).restart();
        verify(plugin.audit()).write(eq("mc:"+player.getUniqueId()),eq("token-change"),eq("bot.yml"),eq("saved"));
        verify(plugin).message(eq(player),eq("token-entry-saved"),eq(Map.of()));
    }
    @Test void directValueIsScrubbedEvenWithoutPermission(){
        when(player.hasPermission(TokenSetup.PERMISSION)).thenReturn(false);
        for(String command:List.of("/dyda bot token "+TOKEN,"/dydiscord admin bot token "+TOKEN,"/discord admin bot token "+TOKEN,"/dydiscord:dydiscord admin bot token "+TOKEN)){
            var event=new PlayerCommandPreprocessEvent(player,command);setup.preprocess(event);assertTrue(event.isCancelled());assertEquals("/dydiscord-token-hidden",event.getMessage());
        }
        verify(plugin,never()).submit(any());
    }
    @Test void permissionRevocationAtCommitRejectsToken()throws Exception{
        var queued=new ArrayList<Callable<?>>();doAnswer(c->{queued.add(c.getArgument(0));return new CompletableFuture<>();}).when(plugin).submit(any());
        setup.open(player);setup.preprocess(new PlayerCommandPreprocessEvent(player,"/"+TOKEN));when(player.hasPermission(TokenSetup.PERMISSION)).thenReturn(false);queued.get(0).call();assertNotEquals(TOKEN,plugin.settings().token());verify(plugin.bot(),never()).restart();
    }
    @Test void clearInvalidatesQueuedPaste()throws Exception{
        var queued=new ArrayList<Callable<?>>();doAnswer(c->{queued.add(c.getArgument(0));return new CompletableFuture<>();}).when(plugin).submit(any());
        setup.open(player);setup.preprocess(new PlayerCommandPreprocessEvent(player,"/"+TOKEN));setup.clear();queued.get(0).call();assertNotEquals(TOKEN,plugin.settings().token());
    }
    @Test void malformedPasteIsHiddenButNeverSaved(){setup.open(player);var event=new PlayerCommandPreprocessEvent(player,"/bad token");setup.preprocess(event);assertTrue(event.isCancelled());assertEquals("/dydiscord-token-hidden",event.getMessage());assertNotEquals("bad token",plugin.settings().token());verify(plugin,never()).submit(any());}
    @Test void freshLoginCannotUsePreviousSession(){setup.open(player);setup.leave(player.getUniqueId());var event=new PlayerCommandPreprocessEvent(player,"/"+TOKEN);setup.preprocess(event);assertFalse(event.isCancelled());verify(plugin,never()).submit(any());}
    @Test void expiredPasteStillGetsScrubbed(){setup.open(player);((TestSupport.Time)plugin.clock()).advance(60);var event=new PlayerCommandPreprocessEvent(player,"/"+TOKEN);setup.preprocess(event);assertTrue(event.isCancelled());assertEquals("/dydiscord-token-hidden",event.getMessage());verify(plugin).message(eq(player),eq("token-entry-expired"),any());verify(plugin,never()).submit(any());}
    @Test void environmentPrecedenceRejectsEntry(){var settings=mock(dev.dydiscord.config.Settings.class);when(settings.tokenSource()).thenReturn("environment");when(plugin.settings()).thenReturn(settings);setup.open(player);verify(plugin).message(eq(player),eq("token-entry-environment"),any());var event=new PlayerCommandPreprocessEvent(player,"/"+TOKEN);setup.preprocess(event);assertFalse(event.isCancelled());verify(plugin,never()).submit(any());}
    @Test void onlyCurrentPlayerSessionCanCommit()throws Exception{
        var queued=new ArrayList<Callable<?>>();doAnswer(c->{queued.add(c.getArgument(0));return new CompletableFuture<>();}).when(plugin).submit(any());
        setup.open(player);setup.preprocess(new PlayerCommandPreprocessEvent(player,"/"+TOKEN));when(plugin.getServer().getPlayer(player.getUniqueId())).thenReturn(mock(Player.class));queued.get(0).call();assertNotEquals(TOKEN,plugin.settings().token());verify(plugin.bot(),never()).restart();
    }
    @Test void closedServiceScrubsInlineAttemptsButCannotOpen(){setup.close();setup.open(player);var event=new PlayerCommandPreprocessEvent(player,"/dyda bot token "+TOKEN);setup.preprocess(event);assertTrue(event.isCancelled());assertEquals("/dydiscord-token-hidden",event.getMessage());verify(plugin,never()).submit(any());}
    @Test void localizedCancelCancelsPendingAndQueuedEntries()throws Exception{
        for(String command:List.of("/dyda cancel","/dyda iptal","/dydiscord cancel","/discord iptal")){
            setup.open(player);var cancel=new PlayerCommandPreprocessEvent(player,command);setup.preprocess(cancel);assertTrue(cancel.isCancelled());assertEquals("/dydiscord-token-hidden",cancel.getMessage());
            var paste=new PlayerCommandPreprocessEvent(player,"/"+TOKEN);setup.preprocess(paste);assertFalse(paste.isCancelled());
        }
        var queued=new ArrayList<Callable<?>>();doAnswer(c->{queued.add(c.getArgument(0));return new CompletableFuture<>();}).when(plugin).submit(any());
        setup.open(player);setup.preprocess(new PlayerCommandPreprocessEvent(player,"/"+TOKEN));setup.preprocess(new PlayerCommandPreprocessEvent(player,"/dyda cancel"));queued.get(0).call();assertNotEquals(TOKEN,plugin.settings().token());
    }
    @Test void tokenGrammarIsBroadButBounded(){assertTrue(TokenSetup.validToken(TOKEN));assertTrue(TokenSetup.validToken("a".repeat(32)));assertTrue(TokenSetup.validToken("a".repeat(512)));assertFalse(TokenSetup.validToken("a".repeat(513)));assertFalse(TokenSetup.validToken("a".repeat(31)));assertFalse(TokenSetup.validToken("a".repeat(35)+"é"));}
    @Test void postCommitAuditFailureDoesNotClaimSavedTokenWasRolledBack()throws Exception{
        var audit=plugin.audit();doThrow(new java.io.IOException("Synthetic audit unavailable")).when(audit).write(anyString(),eq("token-change"),anyString(),eq("saved"));
        setup.open(player);setup.preprocess(new PlayerCommandPreprocessEvent(player,"/"+TOKEN));assertEquals(TOKEN,plugin.settings().token());verify(plugin).message(eq(player),eq("token-entry-saved"),any());verify(plugin,never()).message(eq(player),eq("token-entry-failed"),any());
    }
}
