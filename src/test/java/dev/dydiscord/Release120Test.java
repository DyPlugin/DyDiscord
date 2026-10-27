package dev.dydiscord;

import dev.dydiscord.config.*;
import dev.dydiscord.hook.PluginLogs;
import dev.dydiscord.linking.Linking;
import dev.dydiscord.moderation.CheatCheck;
import dev.dydiscord.storage.Store;
import dev.dydiscord.util.Tasks;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 1.2.0: Folia, cheat checks, reports, required linking, account sync and DyHomes logs. */
class Release120Test {
    @TempDir Path folder;

    static Map<String,Object> yaml(String resource)throws Exception{try(var in=Release120Test.class.getResourceAsStream(resource)){return Configuration.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
    static ModerationSettings moderation(Map<String,Object> config,Map<String,Object> bot){return ModerationSettings.read(config,bot,"");}
    @SuppressWarnings("unchecked") static void put(Map<String,Object> root,String path,Object value){String[] keys=path.split("\\.");Map<String,Object> node=root;for(int i=0;i<keys.length-1;i++)node=(Map<String,Object>)node.computeIfAbsent(keys[i],k->new LinkedHashMap<>());node.put(keys[keys.length-1],value);}

    @Test void bundledSettingsKeepEveryNewToolOffOrHarmless()throws Exception{
        ModerationSettings settings=TestSupport.settings().moderation();
        assertFalse(settings.requireLink());assertTrue(settings.bypassOps());assertTrue(settings.freeze());assertEquals(15,settings.reminderSeconds());
        assertEquals("dm",settings.noticeMode());assertTrue(settings.directMessage());assertFalse(settings.channelNotice());
        assertEquals("off",settings.banSync());assertFalse(settings.banToDiscord());assertFalse(settings.banFromDiscord());assertFalse(settings.nicknameSync());
        assertTrue(settings.allowedCommands().containsAll(Set.of("msg","link")));assertTrue(settings.quitCommands().isEmpty());assertEquals(60,settings.reportCooldown());
        assertEquals(ModerationSettings.defaults().allowedCommands(),settings.allowedCommands());
    }
    @Test void invalidModerationValuesAreRejectedWithTheirPath()throws Exception{
        var config=yaml("/config.yml");var bot=yaml("/bot.yml");
        put(bot,"cheat-check.notify","pager");assertEquals("Invalid setting: cheat-check.notify",assertThrows(IllegalArgumentException.class,()->moderation(config,bot)).getMessage());
        put(bot,"cheat-check.notify","both");put(bot,"moderation.nickname-format","[MC] player");assertEquals("Invalid setting: moderation.nickname-format",assertThrows(IllegalArgumentException.class,()->moderation(config,bot)).getMessage());
        put(bot,"moderation.nickname-format","[MC] {name}");put(config,"cheat-check.quit-commands",List.of("op {player}"));assertEquals("Invalid setting: cheat-check.quit-commands",assertThrows(IllegalArgumentException.class,()->moderation(config,bot)).getMessage());
        put(config,"cheat-check.quit-commands",List.of("ban {player} Left during a cheat check"));put(bot,"reports.channel-id","123456789012345678");
        assertEquals("Invalid setting: reports.channel-id",assertThrows(IllegalArgumentException.class,()->ModerationSettings.read(config,bot,"123456789012345678")).getMessage());
        ModerationSettings valid=moderation(config,bot);assertEquals(List.of("ban {player} Left during a cheat check"),valid.quitCommands());assertTrue(valid.directMessage()&&valid.channelNotice());
        assertEquals("[MC] Alexander_The_Great_Of_Mine",valid.nickname("Alexander_The_Great_Of_Minecraft"),"cut to Discord's 32 characters");
    }
    @Test void banSyncFromDiscordNeedsABotRestartForItsIntent()throws Exception{
        var config=yaml("/config.yml");var bot=yaml("/bot.yml");Settings before=Settings.read(config,bot,null);
        put(bot,"moderation.ban-sync","minecraft-to-discord");assertFalse(before.needsRestart(Settings.read(config,bot,null)));
        put(bot,"moderation.ban-sync","both");assertTrue(before.needsRestart(Settings.read(config,bot,null)));
    }
    @Test void dyHomesLogSwitchesDefaultToQuietTeleports()throws Exception{
        IntegrationSettings logs=TestSupport.settings().integrations();
        assertTrue(IntegrationSettings.EVENTS.containsAll(List.of("dyhomes.warps","dyhomes.public-homes","dyhomes.teleports")));
        var bot=yaml("/bot.yml");put(bot,"plugin-logs.channel-id","123456789012345678");IntegrationSettings on=IntegrationSettings.read(yaml("/config.yml"),bot);
        assertTrue(on.logs("dyhomes.warps"));assertTrue(on.logs("dyhomes.public-homes"));assertFalse(on.logs("dyhomes.teleports"));assertFalse(logs.logs("dyhomes.warps"),"no channel, no logs");
        assertFalse(IntegrationSettings.defaults().logEvents().contains("dyhomes.teleports"));
    }
    @Test void foliaServersFindFoliaBuildsOnModrinth(){assertEquals(Set.of("folia","paper","spigot","bukkit"),dev.dydiscord.update.Modrinth.loaders("Folia"));}
    @Test void descriptorDeclaresFoliaCommandsAndGroupedPermissions()throws Exception{
        var descriptor=yaml("/plugin.yml");assertEquals(true,descriptor.get("folia-supported"));
        @SuppressWarnings("unchecked") var commands=(Map<String,Map<String,Object>>)descriptor.get("commands");
        assertTrue(((List<?>)commands.get("cheatcheck").get("aliases")).contains("hile-kontrol"));assertTrue(((List<?>)commands.get("report").get("aliases")).contains("rapor"));
        @SuppressWarnings("unchecked") var permissions=(Map<String,Map<String,Object>>)descriptor.get("permissions");
        @SuppressWarnings("unchecked") var admin=(Map<String,Object>)permissions.get("dydiscord.admin").get("children");@SuppressWarnings("unchecked") var player=(Map<String,Object>)permissions.get("dydiscord.player").get("children");
        assertEquals(true,admin.get("dydiscord.cheatcheck"));assertEquals(true,admin.get("dydiscord.cheatcheck.notify"));assertEquals(true,admin.get("dydiscord.report.notify"));assertEquals(true,player.get("dydiscord.report"));
        assertNull(admin.get("dydiscord.cheatcheck.exempt"),"staff can still be checked unless granted the exemption");
    }
    @Test void everyLanguageShipsTheNewTexts()throws Exception{
        List<String> game=List.of("cheatcheck-usage","cheatcheck-linked","cheatcheck-unlinked","cheatcheck-title","cheatcheck-subtitle-linked","cheatcheck-delivery-unlinked","cheatcheck-frozen","cheatcheck-quit","player-report-sent","player-report-staff","require-link-kick","require-link-unavailable","moderation-banned");
        List<String> discord=List.of("cheatcheck-dm","cheatcheck-voice","cheatcheck-delivery-dm","log-cheatcheck-started","log-cheatcheck-ended","log-cheatcheck-quit","log-report","log-report-location","log-warp-created","log-warp-moved","log-warp-deleted","log-home-public","log-home-private","log-home-teleport");
        Map<String,Object> english=yaml("/messages_en.yml"),englishDiscord=yaml("/discord/messages_en.yml");
        for(String lang:dev.dydiscord.language.Messages.LANGUAGES){
            var texts=yaml("/messages_"+lang+".yml");var bot=yaml("/discord/messages_"+lang+".yml");
            for(String key:game){assertTrue(texts.get(key) instanceof String,lang+" "+key);assertEquals(dev.dydiscord.language.Messages.placeholders((String)english.get(key)),dev.dydiscord.language.Messages.placeholders((String)texts.get(key)),lang+" "+key);assertTrue(dev.dydiscord.language.Messages.releaseKey(key),key);}
            for(String key:discord){assertTrue(bot.get(key) instanceof String,lang+" discord "+key);assertEquals(dev.dydiscord.language.Messages.placeholders((String)englishDiscord.get(key)),dev.dydiscord.language.Messages.placeholders((String)bot.get(key)),lang+" discord "+key);assertTrue(dev.dydiscord.language.Messages.releaseKey(key),key);}
        }
        assertTrue(((String)yaml("/messages_tr.yml").get("require-link-kick")).contains("{code}"));
    }

    @Test void joinScreenCodesSkipTheCooldownReplaceEachOtherAndSurviveQuits()throws Exception{
        try(Store store=new Store(folder.resolve("data.db"))){
            TestSupport.Time time=new TestSupport.Time();Settings settings=TestSupport.settings();Linking linking=new Linking(store,time,new SecureRandom());UUID uuid=UUID.randomUUID();
            String first=linking.issueOffline(uuid,"Alice",settings,true).code();String second=linking.issueOffline(uuid,"Alice",settings,true).code();
            assertTrue(linking.inspect(first).isEmpty(),"a newer join attempt replaces the old code");
            Linking.Code code=linking.inspect(second).orElseThrow();assertEquals(Linking.OFFLINE,code.session());
            linking.leave(uuid);assertTrue(linking.inspect(second).isPresent());
            assertEquals("bot-unavailable",assertThrows(Linking.Rejected.class,()->linking.issueOffline(UUID.randomUUID(),"Bob",settings,false)).key);
            Store.Link saved=linking.redeem(second,"123456789012345678",settings,true,c->c.session()==Linking.OFFLINE);assertEquals(uuid,saved.uuid());
            assertEquals("already-linked",assertThrows(Linking.Rejected.class,()->linking.issueOffline(uuid,"Alice",settings,true)).key);
        }
    }
    @Test void offlineCodesNeedNoOnlineSession()throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);doCallRealMethod().when(plugin).sessionValid(any());doCallRealMethod().when(plugin).validOwner(any());
        Linking.Code code=new Linking.Code(UUID.randomUUID(),"Alice","digest",java.time.Instant.MAX,Linking.OFFLINE);
        assertTrue(plugin.sessionValid(code));assertTrue(plugin.validOwner(code));
    }

    // DyHomes events are found by their simple names and read through public accessors.
    public record SavedLocation(String world,double x,double y,double z) {}
    public record Warp(String name,SavedLocation location) {}
    public record Home(String name,UUID owner,boolean publicHome) {}
    public static final class WarpChangeEvent extends Event {
        private final CommandSender sender;private final Warp warp;private final boolean created,deleted;
        WarpChangeEvent(CommandSender sender,Warp warp,boolean created,boolean deleted){this.sender=sender;this.warp=warp;this.created=created;this.deleted=deleted;}
        public CommandSender getSender(){return sender;}public Warp getWarp(){return warp;}public boolean isCreated(){return created;}public boolean isDeleted(){return deleted;}
        @Override public HandlerList getHandlers(){return new HandlerList();}
    }
    public static final class HomePublicChangeEvent extends Event {
        private final Home home;HomePublicChangeEvent(Home home){this.home=home;}public Home getHome(){return home;}
        @Override public HandlerList getHandlers(){return new HandlerList();}
    }
    @Test void dyHomesEventsBecomeLogEntries()throws Exception{
        CommandSender admin=mock(CommandSender.class);when(admin.getName()).thenReturn("Admin");
        var created=PluginLogs.entry(new WarpChangeEvent(admin,new Warp("spawn",new SavedLocation("world",10.7,64,-3.2)),true,false));
        assertEquals("dyhomes.warps",created.event());assertEquals("log-warp-created",created.key());
        assertEquals(Map.of("warp","spawn","player","Admin","world","world","x","10","y","64","z","-4"),created.values());
        assertEquals("log-warp-deleted",PluginLogs.entry(new WarpChangeEvent(admin,new Warp("spawn",new SavedLocation("world",0,0,0)),false,true)).key());
        assertEquals("log-warp-moved",PluginLogs.entry(new WarpChangeEvent(admin,new Warp("spawn",new SavedLocation("world",0,0,0)),false,false)).key());
        UUID owner=UUID.randomUUID();OfflinePlayer offline=mock(OfflinePlayer.class);when(offline.getName()).thenReturn("Alice");
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)){bukkit.when(()->Bukkit.getOfflinePlayer(owner)).thenReturn(offline);
            var open=PluginLogs.entry(new HomePublicChangeEvent(new Home("base",owner,true)));
            assertEquals("dyhomes.public-homes",open.event());assertEquals("log-home-public",open.key());assertEquals("Alice",open.values().get("owner"));assertEquals("base",open.values().get("home"));
            assertEquals("log-home-private",PluginLogs.entry(new HomePublicChangeEvent(new Home("base",owner,false))).key());
        }
    }

    /** A plugin mock whose scheduled player work is recorded instead of run. */
    static final class Host {
        final DyDiscord plugin=mock(DyDiscord.class);final Tasks tasks=mock(Tasks.class);final List<Runnable> main=new ArrayList<>();
        Host()throws Exception{
            when(plugin.ready()).thenReturn(true);when(plugin.settings()).thenReturn(TestSupport.settings());when(plugin.tasks()).thenReturn(tasks);
            when(tasks.entityTimer(any(),any(),anyLong(),anyLong())).thenReturn(mock(Tasks.Task.class));when(tasks.entity(any(),any(),anyLong())).thenReturn(mock(Tasks.Task.class));
            when(plugin.cachedLink(any())).thenReturn(Optional.empty());when(plugin.clock()).thenReturn(new TestSupport.Time());
            when(plugin.submit(any())).thenReturn(new CompletableFuture<>());doAnswer(i->{main.add(i.getArgument(0));return null;}).when(plugin).onMain(any());
        }
    }
    static Player player(String name){Player p=mock(Player.class);UUID id=UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));when(p.getUniqueId()).thenReturn(id);when(p.getName()).thenReturn(name);when(p.isOnline()).thenReturn(true);return p;}
    @Test void cheatCheckFreezesTellsStaffAndEndsCleanly()throws Exception{
        Host host=new Host();CheatCheck check=new CheatCheck(host.plugin);Player staff=player("Mod"),target=player("Suspect");
        when(staff.hasPermission(anyString())).thenReturn(true);when(target.hasPermission(anyString())).thenReturn(false);
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(staff,target));bukkit.when(()->Bukkit.getPlayerExact("Suspect")).thenReturn(target);bukkit.when(()->Bukkit.getPlayer(target.getUniqueId())).thenReturn(target);
            Command command=mock(Command.class);
            check.onCommand(staff,command,"hile-kontrol",new String[]{"Suspect"});
            assertTrue(check.active(target.getUniqueId()));verify(host.tasks).entityTimer(eq(target),any(),eq(1L),eq(300L));
            verify(host.plugin).message(target,"cheatcheck-unlinked",Map.of("staff","Mod"));verify(host.plugin).message(staff,"cheatcheck-started",Map.of("player","Suspect"));
            check.onCommand(staff,command,"cheatcheck",new String[]{"Suspect"});verify(host.plugin).message(staff,"cheatcheck-already",Map.of("player","Suspect"));

            World world=mock(World.class);Location from=new Location(world,1,64,1,0,0),to=new Location(world,2,64,1,90,10);
            PlayerMoveEvent move=new PlayerMoveEvent(target,from,to);check.move(move);
            assertEquals(1,move.getTo().getX());assertEquals(90,move.getTo().getYaw(),"looking around stays possible");
            PlayerCommandPreprocessEvent home=new PlayerCommandPreprocessEvent(target,"/home base",Set.of());check.command(home);assertTrue(home.isCancelled());
            PlayerCommandPreprocessEvent msg=new PlayerCommandPreprocessEvent(target,"/minecraft:msg Mod hello",Set.of());check.command(msg);assertFalse(msg.isCancelled());

            check.onCommand(staff,command,"cheatcheck",new String[]{"Suspect","bitir"});
            assertFalse(check.active(target.getUniqueId()));verify(host.plugin).message(target,"cheatcheck-ended-player",Map.of());
            PlayerCommandPreprocessEvent after=new PlayerCommandPreprocessEvent(target,"/home base",Set.of());check.command(after);assertFalse(after.isCancelled());
        }
    }
    @Test void exemptPlayersAndUnknownNamesAreRefused()throws Exception{
        Host host=new Host();CheatCheck check=new CheatCheck(host.plugin);Player staff=player("Mod"),admin=player("Owner");
        when(staff.hasPermission(anyString())).thenReturn(true);when(admin.hasPermission("dydiscord.cheatcheck.exempt")).thenReturn(true);
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(staff,admin));bukkit.when(()->Bukkit.getPlayerExact("Owner")).thenReturn(admin);
            check.onCommand(staff,mock(Command.class),"cheatcheck",new String[]{"Owner"});verify(host.plugin).message(staff,"cheatcheck-exempt",Map.of("player","Owner"));assertFalse(check.active(admin.getUniqueId()));
            check.onCommand(staff,mock(Command.class),"cheatcheck",new String[]{"Ghost"});verify(host.plugin).message(staff,"cheatcheck-not-found",Map.of("player","Ghost"));
            check.onCommand(staff,mock(Command.class),"cheatcheck",new String[]{"Owner","later"});verify(host.plugin).message(staff,"cheatcheck-usage",Map.of());
        }
    }
    @Test void leavingDuringACheckRunsTheConfiguredConsoleCommands()throws Exception{
        Host host=new Host();var config=yaml("/config.yml");put(config,"cheat-check.quit-commands",List.of("ban {player} Left during a cheat check"));
        Settings settings=Settings.read(config,yaml("/bot.yml"),null);when(host.plugin.settings()).thenReturn(settings);
        Server server=mock(Server.class);ConsoleCommandSender console=mock(ConsoleCommandSender.class);when(server.getConsoleSender()).thenReturn(console);when(host.plugin.getServer()).thenReturn(server);
        CheatCheck check=new CheatCheck(host.plugin);Player staff=player("Mod"),target=player("Suspect");when(staff.hasPermission(anyString())).thenReturn(true);
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)){
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(staff,target));bukkit.when(()->Bukkit.getPlayerExact("Suspect")).thenReturn(target);
            check.onCommand(staff,mock(Command.class),"cheatcheck",new String[]{"Suspect"});
            check.quit(new PlayerQuitEvent(target,"bye"));
            assertFalse(check.active(target.getUniqueId()));verify(host.plugin).message(staff,"cheatcheck-quit",Map.of("player","Suspect","staff","Mod"));
            assertEquals(1,host.main.size());host.main.get(0).run();verify(server).dispatchCommand(console,"ban Suspect Left during a cheat check");
        }
    }
}
