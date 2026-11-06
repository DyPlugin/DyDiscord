package dev.dydiscord;

import dev.dydiscord.config.*;
import dev.dydiscord.discord.*;
import dev.dydiscord.storage.*;
import dev.dydiscord.support.Tickets;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Release140FlowTest {
    @TempDir Path folder;
    Configuration config;DyDiscord plugin;Bot bot;TicketThreads threads;FeatureStore store;Server server;TestSupport.Time time=new TestSupport.Time();
    final UUID player=UUID.randomUUID();

    @BeforeEach void setup()throws Exception{
        config=TestSupport.config(folder);config.change("bot.yml","tickets.channel-id","523456789012345678");config.change("bot.yml","tickets.enabled",true);
        plugin=mock(DyDiscord.class);when(plugin.settings()).thenAnswer(i->config.active().settings());when(plugin.messages()).thenAnswer(i->config.active().messages());when(plugin.clock()).thenReturn(time);when(plugin.ready()).thenReturn(true);
        doAnswer(call->{try{return CompletableFuture.completedFuture(((Callable<?>)call.getArgument(0)).call());}catch(Exception failure){return CompletableFuture.failedFuture(failure);}}).when(plugin).submit(any());
        doAnswer(call->((Callable<?>)call.getArgument(0)).call()).when(plugin).mainValue(any());
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(plugin).onMain(any());
        server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(server.getOnlinePlayers()).thenAnswer(i->List.of());
        bot=mock(Bot.class);threads=mock(TicketThreads.class);when(plugin.bot()).thenReturn(bot);when(bot.usable()).thenReturn(true);when(bot.tickets()).thenReturn(threads);
        when(bot.text(anyString(),anyMap())).thenAnswer(call->config.active().messages().render(true,"en",call.getArgument(0),call.getArgument(1)));
        store=new FeatureStore(folder.resolve("data/features.db"));
    }
    @AfterEach void close()throws Exception{store.close();}
    Player online(){Player p=mock(Player.class);when(p.getUniqueId()).thenReturn(player);when(p.getName()).thenReturn("Alex");when(p.isOnline()).thenReturn(true);when(p.hasPermission("dydiscord.ticket")).thenReturn(true);return p;}

    @Test void aTicketOpensOneThreadThenAddsToItUntilItIsClosed()throws Exception{
        Tickets tickets=new Tickets(plugin,store);Player alex=online();when(threads.open(eq("Alex"),anyString())).thenReturn("1001");when(threads.post(eq("1001"),anyString())).thenReturn(true);
        assertTrue(tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"my","house","was","griefed"}));
        verify(threads).open(eq("Alex"),argThat(text->text.contains("my house was griefed")&&text.contains("Alex")));verify(plugin).message(alex,"ticket-opened",Map.of());
        assertEquals("1001",store.ticket(player).orElseThrow().thread());
        tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"near","spawn"});
        verify(threads).post(eq("1001"),argThat(text->text.contains("near spawn")));verify(plugin).message(alex,"ticket-added",Map.of());verify(threads,times(1)).open(anyString(),anyString());
        tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"destek",new String[]{"kapat"});
        verify(threads).close(eq("1001"),argThat(text->text.contains("Alex")));verify(plugin).message(alex,"ticket-closed",Map.of());assertTrue(store.ticket(player).isEmpty());
        tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"close"});verify(plugin).message(alex,"ticket-none",Map.of());
    }
    @Test void aClosedThreadOpensANewTicketOnlyAfterTheCooldown()throws Exception{
        Tickets tickets=new Tickets(plugin,store);Player alex=online();when(threads.open(anyString(),anyString())).thenReturn("1001","1002");when(threads.post(anyString(),anyString())).thenReturn(false);
        tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"first"});
        tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"second"});
        verify(plugin).message(eq(alex),eq("ticket-cooldown"),argThat(values->values.get("seconds").equals("60")));
        time.advance(61);tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"third"});assertEquals("1002",store.ticket(player).orElseThrow().thread());
    }
    @Test void disabledTicketsAndMissingPermissionAreRefusedBeforeAnyWork()throws Exception{
        Tickets tickets=new Tickets(plugin,store);Player alex=online();config.change("bot.yml","tickets.enabled",false);
        tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"help"});verify(plugin).message(alex,"ticket-disabled",Map.of());
        when(alex.hasPermission("dydiscord.ticket")).thenReturn(false);tickets.onCommand(alex,mock(org.bukkit.command.Command.class),"ticket",new String[]{"help"});verify(plugin).message(alex,"no-permission",Map.of());
        verifyNoInteractions(threads);
    }
    @Test void staffRepliesReachOnlinePlayersAndWaitForOfflineOnes()throws Exception{
        Tickets tickets=new Tickets(plugin,store);store.open(new FeatureStore.Ticket(player,"Alex","1001",Instant.EPOCH));
        Player alex=online();when(server.getPlayer(player)).thenReturn(alex);
        tickets.reply("1001","Mod §cSam","&aWe are on it");verify(plugin).message(alex,"ticket-reply",Map.of("staff","Mod Sam","message","We are on it"));
        tickets.reply("9999","Mod","ignored");
        when(server.getPlayer(player)).thenReturn(null);tickets.reply("1001","Mod","Fixed now");
        tickets.join(new org.bukkit.event.player.PlayerJoinEvent(alex,"joined"));verify(plugin).message(alex,"ticket-reply",Map.of("staff","Mod","message","Fixed now"));
        assertTrue(store.takeReplies(player).isEmpty());
    }
    @Test void threadTitlesArePlainAndBounded(){
        assertEquals("Ticket: Alex_B",TicketThreads.title("Ticket: {player}","Alex_B"));assertEquals(100,TicketThreads.title("{player}","x".repeat(300)).length());
    }

    @Test void whoisIsLimitedToStaffAndShowsTheLinkedAccount()throws Exception{
        Member member=mock(Member.class);var none=new FeatureSettings.Whois(true,Set.of());assertFalse(Whois.allowed(member,none));
        when(member.hasPermission(Permission.MANAGE_SERVER)).thenReturn(true);assertTrue(Whois.allowed(member,none));
        Role staff=mock(Role.class);when(staff.getId()).thenReturn("923456789012345678");var listed=new FeatureSettings.Whois(true,Set.of("923456789012345678"));assertFalse(Whois.allowed(member,listed),"listed roles replace Manage Server");
        when(member.getRoles()).thenReturn(List.of(staff));assertTrue(Whois.allowed(member,listed));assertFalse(Whois.allowed(null,listed));
        assertEquals(Long.valueOf(Permission.MANAGE_SERVER.getRawValue()),Whois.defaultPermissions(none).getPermissionsRaw());

        Store links=mock(Store.class);when(plugin.store()).thenReturn(links);var link=new Store.Link(player,"723456789012345678","Alex",Instant.ofEpochSecond(1700000000),"v1","alex_dc");
        when(links.byName("alex")).thenReturn(Optional.of(link));when(links.byDiscord("723456789012345678")).thenReturn(Optional.of(link));
        OfflinePlayer offline=mock(OfflinePlayer.class);when(offline.getLastPlayed()).thenReturn(1750000000000L);when(server.getOfflinePlayer(player)).thenReturn(offline);
        String text=Whois.lookup(plugin,"alex","en");
        assertTrue(text.contains("**Alex**")&&text.contains("<@723456789012345678>")&&text.contains("alex\\_dc")&&text.contains("<t:1700000000:D>")&&text.contains("<t:1750000000:R>"),text);
        assertEquals(text,Whois.lookup(plugin,"<@!723456789012345678>","en"));
        Player alex=online();when(server.getPlayer(player)).thenReturn(alex);assertTrue(Whois.lookup(plugin,"723456789012345678","en").contains(config.active().messages().get(true,"en","whois-online")));
        when(links.byName("Nobody")).thenReturn(Optional.empty());assertTrue(Whois.lookup(plugin,"Nobody","en").startsWith("No linked account"));
        assertTrue(Whois.lookup(plugin,"@everyone <script>","en").contains("No linked account"));
    }

    @Test void theBotSkipsGuildMessageEventsWhenNoFeatureReadsMessages()throws Exception{
        config.change("bot.yml","tickets.enabled",false);config.change("bot.yml","bot.prefix-commands-enabled",false);config.change("bot.yml","console.commands.enabled",false);config.change("bot.yml","chat.discord-to-minecraft",false);
        config.change("bot.yml","token","synthetic.token.value");config.change("bot.yml","guild-id","123456789012345678");
        DyDiscord host=mock(DyDiscord.class);when(host.settings()).thenAnswer(i->config.active().settings());when(host.messages()).thenAnswer(i->config.active().messages());when(host.ready()).thenReturn(true);when(host.clock()).thenReturn(time);
        Store links=mock(Store.class);when(links.panel()).thenReturn(Optional.empty());when(host.store()).thenReturn(links);
        var console=mock(dev.dydiscord.console.ConsoleBridge.class);when(host.console()).thenReturn(console);when(console.buffer()).thenReturn(new dev.dydiscord.console.LogBuffer());
        var builder=mock(JDABuilder.class,RETURNS_SELF);when(builder.build()).thenReturn(mock(JDA.class));Bot real=new Bot(host);
        try(var builders=mockStatic(JDABuilder.class)){
            builders.when(()->JDABuilder.createLight(anyString(),anyCollection())).thenReturn(builder);real.start();
            builders.verify(()->JDABuilder.createLight(anyString(),argThat((Collection<GatewayIntent> intents)->intents.isEmpty())));
        }
        assertFalse(real.content());
    }
}
