package dev.dydiscord;

import dev.dydiscord.command.Router;
import dev.dydiscord.config.*;
import dev.dydiscord.console.ConsoleBridge;
import dev.dydiscord.discord.Bot;
import dev.dydiscord.linking.Linking;
import dev.dydiscord.storage.Store;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationLifecycleTest {
    @TempDir Path folder;
    Configuration configuration;DyDiscord plugin;Bot bot;ConsoleCommandSender sender;Store store;Map<UUID,Long> sessions;Map<UUID,String> preferences;
    @BeforeEach void setup()throws Exception{
        configuration=TestSupport.config(folder);plugin=mock(DyDiscord.class);
        field("configuration",configuration);sessions=new ConcurrentHashMap<>();preferences=new ConcurrentHashMap<>();field("sessions",sessions);field("preferences",preferences);
        field("warnedTranslations",ConcurrentHashMap.newKeySet());
        field("linking",mock(Linking.class));field("console",mock(ConsoleBridge.class));
        store=new Store(folder.resolve("data.db"));field("store",store);
        when(plugin.store()).thenReturn(store);when(plugin.clock()).thenReturn(new TestSupport.Time());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test-config-lifecycle"));
        when(plugin.ready()).thenReturn(true);doCallRealMethod().when(plugin).language(any());
        doCallRealMethod().when(plugin).settings();doCallRealMethod().when(plugin).messages();
        doCallRealMethod().when(plugin).execute(any(),any(),any());
        doAnswer(call->{CompletableFuture<Object> result=new CompletableFuture<>();try{result.complete(((Callable<?>)call.getArgument(0)).call());}catch(Throwable e){result.completeExceptionally(e);}return result;}).when(plugin).submit(any());
        doAnswer(call->((Callable<?>)call.getArgument(0)).call()).when(plugin).mainValue(any());
        bot=spy(new Bot(plugin));doNothing().when(bot).restart();field("bot",bot);
        sender=mock(ConsoleCommandSender.class);when(sender.getName()).thenReturn("CONSOLE");when(sender.hasPermission(anyString())).thenReturn(true);
    }
    @AfterEach void close()throws Exception{store.close();}
    private void field(String name,Object value)throws Exception{var field=DyDiscord.class.getDeclaredField(name);field.setAccessible(true);field.set(plugin,value);}
    private void editedBotFile()throws Exception{Files.writeString(folder.resolve("bot.yml"),"enabled: false\ntoken: 'synthetic-test-token'\nguild-id: '123456789012345678'\npanel:\n  channel-id: '223456789012345678'\nconsole:\n  channel-id: '323456789012345678'\n");}
    private void command(String... args){new Router(plugin,false).onCommand(sender,mock(Command.class),"dydiscord",args);}
    private Player player(){Player player=mock(Player.class);UUID uuid=UUID.randomUUID();when(player.getName()).thenReturn("Alice");when(player.getUniqueId()).thenReturn(uuid);when(player.getLocale()).thenReturn("en_US");when(player.isOnline()).thenReturn(true);when(player.hasPermission(anyString())).thenReturn(true);sessions.put(uuid,1L);return player;}
    @Test void reloadLoadsInitialBotFileWhileBotRemainsDisabled()throws Exception{
        editedBotFile();command("admin","reload");
        assertEquals("123456789012345678",plugin.settings().guild());assertEquals("223456789012345678",plugin.settings().panelChannel());assertFalse(plugin.settings().botEnabled());
        verify(plugin).message(eq(sender),eq("reload-success"),any());verify(plugin).message(eq(sender),eq("discord-validation-pending"),any());verify(bot,never()).restart();
    }
    @Test void enableAfterReloadUsesNewSettingsAndRequestsOneRestart()throws Exception{
        editedBotFile();command("admin","reload");command("admin","bot","enable");
        assertTrue(plugin.settings().botEnabled());assertTrue(Settings.get(Configuration.read(folder.resolve("bot.yml")),"enabled",false).equals(true));
        verify(bot).restart();verify(plugin).message(eq(sender),eq("saved"),any());verify(plugin,never()).message(eq(sender),eq("usage"),any());
    }
    @Test void incompleteSetupHasActionableResponseAndStaysDisabled(){
        command("admin","bot","enable");assertFalse(plugin.settings().botEnabled());
        verify(plugin).message(eq(sender),eq("bot-setup-incomplete"),any());verify(plugin,never()).message(eq(sender),eq("usage"),any());verify(bot,never()).restart();
    }
    @ParameterizedTest @CsvSource({"lang,false","language,false","dil,false","lang,true","language,true","dil,true"})
    void languageAliasesWorkWithUnloadedBotFileAndNoConnection(String alias,boolean inGame)throws Exception{
        editedBotFile();CommandSender actor=inGame?player():sender;new Router(plugin,false).onCommand(actor,mock(Command.class),"dydiscord",new String[]{"admin",alias,"tr"});
        assertEquals("tr",plugin.settings().language());assertEquals("tr",Configuration.read(folder.resolve("config.yml")).get("lang"));assertEquals("123456789012345678",plugin.settings().guild());
        verify(plugin).message(eq(actor),eq("saved"),any());verify(plugin,never()).message(eq(actor),eq("failed"),any());verify(bot,never()).restart();
    }
    @Test void inGamePersonalLanguagePersistsAndRespectsGlobalOverride()throws Exception{
        Player owner=player();Router router=new Router(plugin,false);
        router.onCommand(owner,mock(Command.class),"discord",new String[]{"dil","tr"});
        assertEquals("tr",store.preferences().get(owner.getUniqueId()));assertEquals("tr",plugin.language(owner));
        command("admin","language","en");router.onCommand(owner,mock(Command.class),"discord",new String[]{"language","tr"});
        assertEquals("en",plugin.language(owner));verify(plugin).message(eq(owner),eq("language-forced"),eq(Map.of("language","English")));
        command("admin","lang","auto");assertEquals("tr",plugin.language(owner));assertEquals("tr",store.preferences().get(owner.getUniqueId()));
    }
}
