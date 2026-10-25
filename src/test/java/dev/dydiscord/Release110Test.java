package dev.dydiscord;

import dev.dydiscord.config.*;
import dev.dydiscord.discord.ServerInfo;
import dev.dydiscord.discord.StatusCard;
import dev.dydiscord.hook.PlaceholderHook;
import dev.dydiscord.hook.PluginLogs;
import dev.dydiscord.language.Messages;
import dev.dydiscord.storage.Store;
import dev.dydiscord.util.Colors;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Release110Test {
    @TempDir Path folder;

    // Languages
    private Map<String,Object> resource(String file)throws Exception{try(var in=getClass().getResourceAsStream("/"+file)){return Configuration.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
    @Test void everyLanguageHasTheSameKeysAndPlaceholders()throws Exception{
        for(String surface:List.of("","discord/")){Map<String,Object> en=resource(surface+"messages_en.yml");
            for(String lang:Messages.LANGUAGES){Map<String,Object> other=resource(surface+"messages_"+lang+".yml");assertEquals(en.keySet(),other.keySet(),surface+lang);
                for(String key:en.keySet())assertEquals(Messages.placeholders((String)en.get(key)),Messages.placeholders((String)other.get(key)),surface+lang+":"+key);}}
    }
    @Test void newLanguagesLoadAndAreSelectable()throws Exception{
        var c=TestSupport.config(folder);for(String lang:List.of("de","es","ru"))assertTrue(Files.exists(folder.resolve("messages_"+lang+".yml"))&&Files.exists(folder.resolve("discord/messages_"+lang+".yml")),lang);
        assertEquals("Hilfe",c.active().messages().get(false,"de","help"));assertEquals("Ayuda",c.active().messages().get(false,"es","help"));assertEquals("Справка",c.active().messages().get(false,"ru","help"));
        assertTrue(c.active().messages().missing().isEmpty(),"fresh files are complete: "+c.active().messages().missing());
        Files.writeString(folder.resolve("config.yml"),Files.readString(folder.resolve("config.yml")).replace("lang: auto","lang: de"));
        var reloaded=c.preview();assertEquals("de",reloaded.settings().language());assertEquals("de",reloaded.settings().botLanguage(),"auto bot language follows the game language");
        assertEquals("ru",Messages.language("auto","ru","en_US"));
    }
    @Test void newLanguagePreferencesStayReadableByOlderVersions()throws Exception{
        UUID player=UUID.randomUUID();Path db=folder.resolve("prefs.db");
        try(Store store=new Store(db)){store.preference(player,"de");assertEquals("de",store.preferences().get(player));}
        try(var connection=java.sql.DriverManager.getConnection("jdbc:sqlite:"+db);var query=connection.createStatement().executeQuery("SELECT language FROM player_preferences WHERE uuid='"+player+"'")){assertTrue(query.next());assertEquals("auto",query.getString(1),"the 1.0 table only holds values a 1.0 JAR understands");}
        try(Store store=new Store(db)){store.preference(player,"tr");assertEquals("tr",store.preferences().get(player));store.preference(player,"es");assertEquals("es",store.preferences().get(player));}
    }

    // MiniMessage
    @Test void miniMessageTagsApplyAndInsertedValuesStayAsTyped()throws Exception{
        assertTrue(Colors.miniMessage(),"the test classpath ships Adventure like Paper");
        assertEquals("§aHello",Colors.color("<green>Hello"));assertEquals("§7[§aDyDiscord§7]",Colors.color("&7[<green>DyDiscord&7]"));
        assertTrue(Colors.color("<gradient:#ff0000:#0000ff>Gradient</gradient>").contains("§x"));
        assertEquals("1 < 2 and 3 > 2",Colors.color("1 < 2 and 3 > 2"));
        var c=TestSupport.config(folder);Path en=folder.resolve("messages_en.yml");
        Files.writeString(en,Files.readString(en).replaceFirst("(?m)^account: .*$","account: \"<green>Minecraft: {name} | Discord: {discord}\""));
        var messages=c.preview().messages();String shown=messages.render(false,"en","account",Map.of("name","<red>Alice","discord","bob"));
        assertTrue(shown.startsWith("§a"),shown);assertTrue(shown.contains("<red>Alice"),"a player's tag is shown, not applied: "+shown);
        assertTrue(messages.render(true,"en","players-title",Map.of("count","<b>")).contains("‹b>"),"Discord text keeps its own escaping");
    }

    // Settings
    @Test void integrationSettingsValidateChannelsAndSwitches()throws Exception{
        Settings defaults=TestSupport.settings();assertTrue(defaults.integrations().placeholders());assertTrue(defaults.integrations().metrics());assertFalse(defaults.integrations().statusCard());assertFalse(defaults.integrations().logs("dyclaim.claims"),"a blank channel disables logs");
        Map<String,Object> config=new HashMap<>(),bot=new HashMap<>(Map.of("status-card",Map.of("enabled",true)));
        assertThrows(IllegalArgumentException.class,()->IntegrationSettings.read(config,bot),"a status card needs a channel");
        String channel="123456789012345678";
        assertThrows(IllegalArgumentException.class,()->IntegrationSettings.read(config,Map.of("console",Map.of("channel-id",channel),"plugin-logs",Map.of("channel-id",channel))),"logs never share the console channel");
        var read=IntegrationSettings.read(Map.of("integrations",Map.of("placeholderapi",false)),Map.of("plugin-logs",Map.of("channel-id",channel,"dyrtp",Map.of("teleports",false))));
        assertFalse(read.placeholders());assertTrue(read.logs("dyclaim.trust"));assertFalse(read.logs("dyrtp.teleports"));
        assertThrows(IllegalArgumentException.class,()->IntegrationSettings.read(config,Map.of("status-card",Map.of("channel-id",channel,"interval-seconds",5))));
    }

    // Status card
    @Test void statusCardShowsOnlineFactsAndOfflineState()throws Exception{
        var messages=TestSupport.config(folder).active().messages();var server=new ServerInfo.Snapshot("Survival","1.21.4",3,50);
        var online=StatusCard.card(messages,"en",new StatusCard.Snapshot(server,19.96),true,Instant.EPOCH);
        assertEquals("Survival",online.getTitle());assertEquals(0x55AA55,online.getColorRaw());
        assertEquals(List.of("Status","Players","Minecraft version","TPS"),online.getFields().stream().map(f->f.getName()).toList());
        assertEquals("3/50",online.getFields().get(1).getValue());assertEquals("20.0",online.getFields().get(3).getValue());
        var spigot=StatusCard.card(messages,"de",new StatusCard.Snapshot(server,-1),true,Instant.EPOCH);assertEquals(3,spigot.getFields().size(),"no TPS field without a TPS source");assertEquals("Spieler",spigot.getFields().get(1).getName());
        var offline=StatusCard.card(messages,"en",new StatusCard.Snapshot(server,20),false,Instant.EPOCH);assertEquals(1,offline.getFields().size());assertTrue(offline.getFields().get(0).getValue().contains("Offline"));assertEquals(0xFF5555,offline.getColorRaw());
    }

    // Plugin logs
    public interface Claim{String getWorld();int getChunkX();int getChunkZ();String getOwnerName();}
    public static final class ClaimCreateEvent extends Event{private final Claim claim;public ClaimCreateEvent(Claim claim){this.claim=claim;}public Claim getClaim(){return claim;}public HandlerList getHandlers(){return new HandlerList();}}
    public static final class ClaimRemoveEvent extends Event{private final Claim claim;public ClaimRemoveEvent(Claim claim){this.claim=claim;}public Claim getClaim(){return claim;}public HandlerList getHandlers(){return new HandlerList();}}
    public static final class DyRtpPostTeleportEvent extends Event{private final Player player;private final Location location;public DyRtpPostTeleportEvent(Player player,Location location){this.player=player;this.location=location;}public Player getPlayer(){return player;}public Location getLocation(){return location;}public HandlerList getHandlers(){return new HandlerList();}}
    public static final class OtherEvent extends Event{public HandlerList getHandlers(){return new HandlerList();}}
    @Test void dyclaimAndDyrtpEventsBecomeLogEntries()throws Exception{
        // A private implementation class, as DyClaim uses: values are read through the public interface.
        Claim claim=new Claim(){public String getWorld(){return "world";}public int getChunkX(){return -2;}public int getChunkZ(){return 3;}public String getOwnerName(){return "Steve";}};
        var created=PluginLogs.entry(new ClaimCreateEvent(claim));assertEquals("dyclaim.claims",created.event());assertEquals("log-claim-created",created.key());assertEquals(Map.of("world","world","x","-32","z","48","owner","Steve"),created.values());
        assertEquals("log-claim-removed",PluginLogs.entry(new ClaimRemoveEvent(claim)).key());
        Player player=mock(Player.class);when(player.getName()).thenReturn("Alex");World world=mock(World.class);when(world.getName()).thenReturn("world_nether");
        var rtp=PluginLogs.entry(new DyRtpPostTeleportEvent(player,new Location(world,10.5,64,-20.2)));assertEquals("dyrtp.teleports",rtp.event());assertEquals(Map.of("player","Alex","world","world_nether","x","10","y","64","z","-21"),rtp.values());
        assertNull(PluginLogs.entry(new OtherEvent()));
        var messages=TestSupport.config(folder).active().messages();assertEquals("**Steve** claimed land in world at -32, 48.",messages.render(true,"en",created.key(),created.values()));
        assertEquals("**Steve\\_** claimed land in world at -32, 48.",messages.render(true,"en",created.key(),Map.of("owner","Steve_","world","world","x","-32","z","48")),"names are escaped for Discord");
    }

    // Placeholders and API
    @Test void placeholdersComeFromTheOnlineCache(){
        DyDiscord plugin=mock(DyDiscord.class);UUID player=UUID.randomUUID();Store.Link link=new Store.Link(player,"123456789012345678","Steve",Instant.EPOCH,"v1","steve.dc");
        when(plugin.ready()).thenReturn(true);when(plugin.cachedLink(player)).thenReturn(Optional.of(link));when(plugin.cachedLink(argThat(id->!player.equals(id)))).thenReturn(Optional.empty());
        assertEquals("true",PlaceholderHook.value(plugin,player,"linked"));assertEquals("steve.dc",PlaceholderHook.value(plugin,player,"discord_name"));
        assertEquals("false",PlaceholderHook.value(plugin,UUID.randomUUID(),"linked"));assertEquals("false",PlaceholderHook.value(plugin,player,"bot_connected"));assertNull(PlaceholderHook.value(plugin,player,"unknown"));
        when(plugin.ready()).thenReturn(false);assertEquals("",PlaceholderHook.value(plugin,player,"linked"));
        var account=dev.dydiscord.api.DyDiscordAPI.account(link);assertEquals(player,account.minecraftId());assertEquals("123456789012345678",account.discordId());assertEquals("Steve",account.minecraftName());
    }
    @Test void linkChangesReachTheAccountListener()throws Exception{
        List<String> seen=new ArrayList<>();UUID player=UUID.randomUUID();
        try(Store store=new Store(folder.resolve("events.db"))){store.accountListener((link,linked)->seen.add(link.name()+":"+linked));
            Store.Link link=store.link(player,"123456789012345678","Steve",Instant.EPOCH);store.unlink(link);store.unlink(link);}
        assertEquals(List.of("Steve:true","Steve:false"),seen,"a second unlink of the same link changes nothing");
    }

    // Packaging
    @Test void descriptorAndStatisticsAreConfigured()throws Exception{
        Map<String,Object> plugin=resource("plugin.yml");assertTrue(((List<?>)plugin.get("softdepend")).containsAll(List.of("PlaceholderAPI","DyClaim","DyRTP")));
        assertEquals(34918,DyDiscord.BSTATS_ID);assertNotNull(getClass().getResource("/licenses/bstats-LICENSE.txt"));
        assertEquals("dev.dydiscord.bstats.bukkit.Metrics",dev.dydiscord.bstats.bukkit.Metrics.class.getName());
    }
}
