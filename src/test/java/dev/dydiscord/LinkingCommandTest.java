package dev.dydiscord;

import dev.dydiscord.command.Router;
import dev.dydiscord.discord.*;
import dev.dydiscord.linking.Linking;
import dev.dydiscord.storage.Store;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real router, command execution, linking and SQLite with a simulated Bukkit session. */
class LinkingCommandTest {
    @TempDir Path folder;
    Store store; Linking linking; DyDiscord plugin; Server server;
    Map<UUID,Long> sessions;

    @BeforeEach void setup()throws Exception {
        store=new Store(folder.resolve("links.db"));
        linking=new Linking(store,new TestSupport.Time(),new SecureRandom());
        plugin=mock(DyDiscord.class);server=mock(Server.class);
        sessions=new ConcurrentHashMap<>();
        field("sessions",sessions);field("store",store);field("linking",linking);
        Bot bot=mock(Bot.class);when(bot.usable()).thenReturn(true);
        when(bot.panel()).thenReturn(new PanelService(store));field("bot",bot);
        when(plugin.getServer()).thenReturn(server);when(plugin.ready()).thenReturn(true);
        when(plugin.settings()).thenReturn(TestSupport.settings());
        when(plugin.language(any())).thenReturn("en");
        doCallRealMethod().when(plugin).execute(any(),any(),any());
        doCallRealMethod().when(plugin).validOwner(any());
        doCallRealMethod().when(plugin).sessionValid(any());
        doCallRealMethod().when(plugin).leave(any());
        // Execute queued work deterministically; real scheduler deadlines have separate tests.
        doAnswer(call->{CompletableFuture<Object> result=new CompletableFuture<>();try{result.complete(((Callable<?>)call.getArgument(0)).call());}catch(Throwable e){result.completeExceptionally(e);}return result;}).when(plugin).submit(any());
        doAnswer(call->((Callable<?>)call.getArgument(0)).call()).when(plugin).mainValue(any());
    }
    @AfterEach void close()throws Exception{store.close();}
    private void field(String name,Object value)throws Exception{var field=DyDiscord.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,value);}
    private Player player(String name){
        Player player=mock(Player.class);
        UUID uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
        when(player.getUniqueId()).thenReturn(uuid);when(player.getName()).thenReturn(name);
        when(player.isOnline()).thenReturn(true);when(player.hasPermission("dydiscord.link")).thenReturn(true);
        when(server.getPlayer(uuid)).thenReturn(player);sessions.put(uuid,1L);return player;
    }
    private String issue(Player player,String label,String... args){
        boolean shortcut=!Set.of("discord","dydiscord","dydiscord:dydiscord").contains(label);
        assertTrue(new Router(plugin,shortcut).onCommand(player,mock(Command.class),label,args));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String,String>> values=ArgumentCaptor.forClass(Map.class);
        verify(plugin).message(eq(player),eq("link-code"),values.capture());
        return values.getValue().get("code");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void bothModesLinkThroughRootsLanguagesAndShortcuts(boolean onlineMode)throws Exception{
        when(server.getOnlineMode()).thenReturn(onlineMode);
        String[][] paths={{"discord","link"},{"discord","eşle"},{"discord","esle"},
            {"dydiscord","link"},{"dydiscord:dydiscord","link"},{"link"},{"esle"},{"eşle"},
            {"hesap-esle"},{"hesap-eşle"}};
        for(int i=0;i<paths.length;i++){
            Player owner=player("Player"+i);String[] path=paths[i];
            String code=issue(owner,path[0],Arrays.copyOfRange(path,1,path.length));
            Store.Link saved=linking.redeem(code,Long.toString(123456789012345678L+i),plugin.settings(),true,
                pending->{try{return plugin.validOwner(pending)&&plugin.sessionValid(pending);}catch(Exception e){return false;}});
            assertEquals(owner.getUniqueId(),saved.uuid());
            assertEquals(saved,store.byUuid(owner.getUniqueId()).orElseThrow());
            assertTrue(linking.inspect(code).isEmpty());
        }
    }
    @Test void offlineModePermissionLossBeforeQueuedIssueStillRejects()throws Exception{
        when(server.getOnlineMode()).thenReturn(false);Player owner=player("Alice");
        doAnswer(call->{when(owner.hasPermission("dydiscord.link")).thenReturn(false);return ((Callable<?>)call.getArgument(0)).call();}).when(plugin).mainValue(any());
        new Router(plugin,false).onCommand(owner,mock(Command.class),"discord",new String[]{"link"});
        verify(plugin).message(eq(owner),eq("no-permission"),any());
        verify(plugin,never()).message(eq(owner),eq("link-code"),any());
        assertTrue(store.byUuid(owner.getUniqueId()).isEmpty());
    }
    @Test void offlineModeLogoutRevokesIssuedCode()throws Exception{
        when(server.getOnlineMode()).thenReturn(false);Player owner=player("Alice");
        String code=issue(owner,"discord","link");
        plugin.leave(new PlayerQuitEvent(owner,""));
        assertTrue(linking.inspect(code).isEmpty());
        assertThrows(Linking.Rejected.class,()->linking.redeem(code,"123456789012345678",plugin.settings(),true,c->true));
        assertTrue(store.byUuid(owner.getUniqueId()).isEmpty());
    }
}
