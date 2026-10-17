package dev.dydiscord;

import dev.dydiscord.command.Router;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.discord.Bot;
import dev.dydiscord.linking.Linking;
import dev.dydiscord.storage.Store;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises real command execution and rendered player chat against SQLite. */
class LinkingPresentationTest {
    @TempDir Path folder;
    Store store;DyDiscord host;Configuration configuration;Linking linking;Player owner;Bot bot;
    List<String> delivered=new ArrayList<>();
    List<BaseComponent> components=new ArrayList<>();
    final TestSupport.Time clock=new TestSupport.Time();
    static final UUID UUID_ONE=UUID.fromString("5992f081-e6e5-4558-82af-a3515b5a35bf");
    static final String DISCORD_ONE="123456789012345678";
    @BeforeEach void setup()throws Exception{
        configuration=TestSupport.config(folder.resolve("config"));
        configuration.change("config.yml","discord.invite-url","https://discord.gg/example");
        configuration.change("bot.yml","guild-id",DISCORD_ONE);configuration.change("bot.yml","panel.channel-id","223456789012345678");
        store=new Store(folder.resolve("data.db"));linking=new Linking(store,clock,new SecureRandom());
        host=mock(DyDiscord.class);bot=mock(Bot.class);Server server=mock(Server.class);owner=mock(Player.class);Player.Spigot chat=mock(Player.Spigot.class);
        when(owner.getUniqueId()).thenReturn(UUID_ONE);when(owner.getName()).thenReturn("Alice");when(owner.isOnline()).thenReturn(true);when(owner.hasPermission(anyString())).thenReturn(true);when(owner.spigot()).thenReturn(chat);
        when(host.getServer()).thenReturn(server);when(server.getPlayer(UUID_ONE)).thenReturn(owner);when(host.ready()).thenReturn(true);
        when(host.settings()).thenAnswer(call->configuration.active().settings());when(host.messages()).thenAnswer(call->configuration.active().messages());when(host.language(any())).thenReturn("en");when(bot.usable()).thenReturn(true);when(bot.invitation()).thenReturn("https://discord.gg/example");
        field("configuration",configuration);field("store",store);field("linking",linking);field("bot",bot);field("sessions",new ConcurrentHashMap<>(Map.of(UUID_ONE,1L)));
        doCallRealMethod().when(host).execute(any(),any(),any());doCallRealMethod().when(host).message(any(),anyString(),any());doCallRealMethod().when(host).notifyLinked(any());
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(host).onMain(any());
        doAnswer(call->((Callable<?>)call.getArgument(0)).call()).when(host).mainValue(any());
        doAnswer(call->{CompletableFuture<Object> result=new CompletableFuture<>();try{result.complete(((Callable<?>)call.getArgument(0)).call());}catch(Throwable e){result.completeExceptionally(e);}return result;}).when(host).submit(any());
        doAnswer(call->{components.add(call.getArgument(0));delivered.add(((BaseComponent)call.getArgument(0)).toPlainText());return null;}).when(chat).sendMessage(any(BaseComponent.class));
    }
    @AfterEach void close()throws Exception{store.close();}
    private void field(String name,Object value)throws Exception{var field=DyDiscord.class.getDeclaredField(name);field.setAccessible(true);field.set(host,value);}
    private void command(String path){assertTrue(new Router(host,false).onCommand(owner,mock(Command.class),"discord",new String[]{path}));}
    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void codeAndInvitationHaveSeparateCopyAndOpenActions(String language)throws Exception{
        when(host.language(any())).thenReturn(language);
        try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);command("link");}
        assertEquals(2,delivered.size());assertTrue(delivered.get(1).contains("https://discord.gg/example"));String result=delivered.get(0);assertTrue(result.matches(".*[A-HJ-NP-Z2-9]{"+host.settings().codeLength()+"}.*"));assertFalse(result.contains("http"));assertFalse(result.contains("discord.gg"));assertFalse(result.contains("discord.com/channels"));verify(bot,never()).panel();assertEquals(net.md_5.bungee.api.chat.ClickEvent.Action.COPY_TO_CLIPBOARD,components.get(0).getClickEvent().getAction());assertEquals(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL,components.get(1).getClickEvent().getAction());assertNotNull(components.get(0).getHoverEvent());assertNotNull(components.get(1).getHoverEvent());
    }
    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void linkedNotificationAndAccountShowFriendlyNamesWithoutIdsOrDates(String language)throws Exception{
        when(host.language(any())).thenReturn(language);Store.Link link=store.link(UUID_ONE,DISCORD_ONE,"Alice","the_claww",clock.instant());
        try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);host.notifyLinked(link);command("status");}
        assertEquals(2,delivered.size());for(String result:delivered){assertTrue(result.contains("Alice"));assertTrue(result.contains("the_claww"));assertFalse(result.contains(DISCORD_ONE));assertFalse(result.contains(clock.instant().toString()));assertFalse(result.contains("UTC"));}
    }
    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void legacyAccountUsesLocalizedFriendlyFallbackWithoutRawId(String language)throws Exception{
        when(host.language(any())).thenReturn(language);store.link(UUID_ONE,DISCORD_ONE,"Alice",clock.instant());
        try(var bukkit=mockStatic(Bukkit.class)){bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);command("status");}
        assertEquals(1,delivered.size());assertTrue(delivered.get(0).contains(language.equals("tr")?"Discord hesabı":"Discord account"));assertFalse(delivered.get(0).contains(DISCORD_ONE));assertFalse(delivered.get(0).contains(clock.instant().toString()));
    }
}
