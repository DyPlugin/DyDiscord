package dev.dydiscord;

import dev.dydiscord.alerts.Alerts;
import dev.dydiscord.chat.ChatBridge;
import dev.dydiscord.chat.ChatText;
import dev.dydiscord.config.*;
import dev.dydiscord.language.Messages;
import dev.dydiscord.storage.FeatureStore;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.md_5.bungee.api.chat.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Release140Test {
    public static final class PaperLike {public double[] getTPS(){return new double[]{19.5,20,20};}}
    @TempDir Path folder;
    static Map<String,Object> yaml(String resource)throws Exception{try(var in=Release140Test.class.getResourceAsStream(resource)){return Configuration.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
    @SuppressWarnings("unchecked") static void put(Map<String,Object> root,String path,Object value){String[] keys=path.split("\\.");Map<String,Object> node=root;for(int i=0;i<keys.length-1;i++)node=(Map<String,Object>)node.computeIfAbsent(keys[i],k->new LinkedHashMap<String,Object>());node.put(keys[keys.length-1],value);}
    static Settings read(Map<String,Object> bot)throws Exception{return Settings.read(yaml("/config.yml"),bot,null);}

    @Test void everyNewFeatureStartsOffAndDefaultsMatchTheBundledFile()throws Exception{
        Settings settings=TestSupport.settings();FeatureSettings features=settings.features();
        assertFalse(features.tickets().enabled());assertTrue(features.tickets().privateThreads());assertEquals(60,features.tickets().cooldown());
        assertFalse(features.alerts().enabled(),"no channel, no alerts");assertEquals(15,features.alerts().lowTps());assertEquals(90,features.alerts().memoryPercent());assertEquals(30,features.alerts().cooldownMinutes());
        assertFalse(features.whois().enabled());assertTrue(features.whois().roles().isEmpty());
        assertFalse(features.groups().enabled());assertTrue(features.groups().rules().isEmpty());assertEquals(300,features.groups().recheckSeconds());
        assertFalse(settings.chat().attachments());
        assertEquals(FeatureSettings.defaults(),features);
    }
    @Test void ticketsNeedMessageContentAndRestartLikeWhois()throws Exception{
        var bot=yaml("/bot.yml");put(bot,"bot.prefix-commands-enabled",false);put(bot,"console.commands.enabled",false);put(bot,"chat.discord-to-minecraft",false);
        Settings off=read(bot);assertFalse(off.contentIntentRequested());
        put(bot,"tickets.channel-id","523456789012345678");put(bot,"tickets.enabled",true);Settings on=read(bot);
        assertTrue(on.contentIntentRequested());assertTrue(off.needsRestart(on));
        put(bot,"whois.enabled",true);assertTrue(on.needsRestart(read(bot)));
        var alerts=yaml("/bot.yml");put(alerts,"alerts.channel-id","623456789012345678");assertFalse(TestSupport.settings().needsRestart(read(alerts)),"alerts apply on reload");
    }
    @Test void invalidFeatureSettingsAreRefused()throws Exception{
        var noChannel=yaml("/bot.yml");put(noChannel,"tickets.enabled",true);assertThrows(IllegalArgumentException.class,()->read(noChannel));
        var console=yaml("/bot.yml");put(console,"console.channel-id","723456789012345678");put(console,"tickets.channel-id","723456789012345678");assertThrows(IllegalArgumentException.class,()->read(console));
        var memory=yaml("/bot.yml");put(memory,"alerts.memory-percent",30);assertThrows(IllegalArgumentException.class,()->read(memory));
        var tps=yaml("/bot.yml");put(tps,"alerts.low-tps",25);assertThrows(IllegalArgumentException.class,()->read(tps));
        var group=yaml("/bot.yml");put(group,"discord-groups.rules",List.of(Map.of("role-id","823456789012345678","group","VIP Group")));assertThrows(IllegalArgumentException.class,()->read(group));
        var role=yaml("/bot.yml");put(role,"discord-groups.rules",List.of(Map.of("role-id","not-a-role","group","vip")));assertThrows(IllegalArgumentException.class,()->read(role));
        var whois=yaml("/bot.yml");put(whois,"whois.allowed-role-ids",List.of("x"));assertThrows(IllegalArgumentException.class,()->read(whois));
    }
    @Test void aRoleManagedByMinecraftToDiscordSyncCannotAlsoGrantAGroup()throws Exception{
        var bot=yaml("/bot.yml");put(bot,"roles.linked-role-ids",List.of("923456789012345678"));put(bot,"discord-groups.rules",List.of(Map.of("role-id","923456789012345678","group","vip")));
        assertEquals("Invalid setting: discord-groups.rules.role-id",assertThrows(IllegalArgumentException.class,()->read(bot)).getMessage());
    }
    @Test void milestonesAreSortedAndRulesMapRolesToGroups()throws Exception{
        var bot=yaml("/bot.yml");put(bot,"alerts.channel-id","623456789012345678");put(bot,"alerts.player-milestones",List.of(100,50,100));
        put(bot,"discord-groups.rules",List.of(Map.of("role-id","823456789012345678","group","vip"),Map.of("role-id","823456789012345679","group","admin")));put(bot,"discord-groups.staff-groups",List.of("admin"));
        FeatureSettings features=read(bot).features();assertEquals(List.of(50,100),features.alerts().milestones());assertTrue(features.alerts().enabled());
        assertEquals(Set.of("vip"),features.groups().desired(Set.of("823456789012345678","1")));assertTrue(features.groups().trusted("admin"));assertTrue(features.groups().trusted("vip"));
    }
    @Test void staffLikeGroupNamesNeedExplicitTrust(){
        for(String name:List.of("admin","owner","op","moderator","mod","srmod","staff","helper","developer","headadmin"))assertTrue(FeatureSettings.staffName(name),name);
        for(String name:List.of("vip","member","default","builder","supporter","elite"))assertFalse(FeatureSettings.staffName(name),name);
        var groups=new FeatureSettings.Groups(true,List.of(new FeatureSettings.Rule("1","admin")),Set.of(),300);assertFalse(groups.trusted("admin"));
    }
    @Test void newSettingsAreAddedToOldFilesWithDescriptionsBelowTheKey()throws Exception{
        Configuration config=TestSupport.config(folder);Path bot=folder.resolve("bot.yml");
        var old=new LinkedHashMap<>(Configuration.read(bot));for(String key:List.of("tickets","alerts","whois","discord-groups"))old.remove(key);@SuppressWarnings("unchecked") var chat=(Map<String,Object>)old.get("chat");chat.remove("attachments");
        Files.writeString(bot,new org.yaml.snakeyaml.Yaml().dump(old));config.load();
        String text=Files.readString(bot);for(String key:List.of("tickets:","alerts:","whois:","discord-groups:","  attachments: false"))assertTrue(text.contains(key),key);
        assertTrue(text.contains("  enabled: false\n  # Needs Message Content intent to read staff replies")||text.contains("  enabled: false\r\n  # Needs Message Content intent to read staff replies"));
        assertFalse(config.active().settings().features().tickets().enabled());
    }

    @Test void everyLanguageShipsTheNewTextsWithTheSamePlaceholders()throws Exception{
        List<String> game=List.of("ticket-usage","ticket-disabled","ticket-opened","ticket-added","ticket-closed","ticket-none","ticket-cooldown","ticket-failed","ticket-reply","ticket-staff","chat-attachment-image","chat-attachment-video","chat-attachment-file","chat-attachment-sticker","chat-attachment-hover");
        List<String> discord=List.of("slash-whois","whois-option","whois-linked","whois-not-linked","whois-not-found","whois-online","whois-last-seen","whois-never","ticket-thread-name","ticket-opened-discord","ticket-message","ticket-closed-discord","alert-low-tps","alert-memory","alert-players");
        var english=yaml("/messages_en.yml");var englishDiscord=yaml("/discord/messages_en.yml");
        for(String lang:Messages.LANGUAGES){var texts=yaml("/messages_"+lang+".yml");var bot=yaml("/discord/messages_"+lang+".yml");
            for(String key:game){assertTrue(texts.get(key) instanceof String,lang+" "+key);assertEquals(Messages.placeholders((String)english.get(key)),Messages.placeholders((String)texts.get(key)),lang+" "+key);assertTrue(Messages.releaseKey(key),key);if(!lang.equals("en")&&Set.of("ticket-opened","ticket-failed","chat-attachment-hover").contains(key))assertNotEquals(english.get(key),texts.get(key),lang+" "+key+" is translated");}
            for(String key:discord){assertTrue(bot.get(key) instanceof String,lang+" "+key);assertEquals(Messages.placeholders((String)englishDiscord.get(key)),Messages.placeholders((String)bot.get(key)),lang+" "+key);assertTrue(Messages.releaseKey(key),key);}
            assertTrue(((String)bot.get("slash-whois")).length()<=100);assertTrue(((String)bot.get("whois-option")).length()<=100);
        }
    }
    @Test void descriptorDeclaresTheTicketCommandWithTurkishAliases()throws Exception{
        var descriptor=yaml("/plugin.yml");@SuppressWarnings("unchecked") var commands=(Map<String,Map<String,Object>>)descriptor.get("commands");
        assertTrue(((List<?>)commands.get("ticket").get("aliases")).containsAll(List.of("destek","talep")));assertEquals("dydiscord.ticket",commands.get("ticket").get("permission"));
        @SuppressWarnings("unchecked") var permissions=(Map<String,Map<String,Object>>)descriptor.get("permissions");
        @SuppressWarnings("unchecked") var player=(Map<String,Object>)permissions.get("dydiscord.player").get("children");@SuppressWarnings("unchecked") var admin=(Map<String,Object>)permissions.get("dydiscord.admin").get("children");
        assertEquals(true,player.get("dydiscord.ticket"));assertEquals(true,admin.get("dydiscord.ticket.notify"));assertNull(player.get("dydiscord.ticket.notify"));
        assertEquals("whois",dev.dydiscord.command.Words.canonical("kimdir"));assertEquals("kimdir",dev.dydiscord.command.Words.display("whois","tr"));
    }

    @Test void featureStoreKeepsTicketsRepliesAndGrants()throws Exception{
        Path file=folder.resolve("data/features.db");try(FeatureStore store=new FeatureStore(file)){
            UUID player=UUID.randomUUID();assertTrue(store.grants(player).isEmpty());assertTrue(store.takeReplies(player).isEmpty());assertFalse(Files.exists(file),"nothing is created before a feature is used");
            store.open(new FeatureStore.Ticket(player,"Alex","1000",Instant.EPOCH));assertEquals("Alex",store.ticketByThread("1000").orElseThrow().name());
            store.open(new FeatureStore.Ticket(player,"Alex","2000",Instant.EPOCH));assertTrue(store.ticketByThread("1000").isEmpty());assertEquals("2000",store.ticket(player).orElseThrow().thread());
            for(int i=0;i<25;i++)store.queueReply(player,"Mod","reply "+i);var replies=store.takeReplies(player);assertEquals(20,replies.size());assertEquals("reply 5",replies.get(0).message());assertTrue(store.takeReplies(player).isEmpty());
            store.grant(player,"vip");store.grant(player,"vip");assertEquals(Set.of("vip"),store.grants(player));store.revoke(player,"vip");assertTrue(store.grants(player).isEmpty());
            assertTrue(store.close(player));assertFalse(store.close(player));
        }
    }
    @Test void unknownFeatureSchemaIsRefusedUnchanged()throws Exception{
        Path file=folder.resolve("features.db");try(var db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var s=db.createStatement()){s.execute("CREATE TABLE tickets(other TEXT)");}
        try(FeatureStore store=new FeatureStore(file)){assertThrows(java.sql.SQLException.class,()->store.ticket(UUID.randomUUID()));}
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var s=db.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table'")){r.next();assertEquals(1,r.getInt(1));}
    }

    @Test void healthAlertsNeedTwoReadingsRespectTheCooldownAndAnnounceEachMilestoneOnce(){
        Alerts alerts=new Alerts(mock(DyDiscord.class));var settings=new FeatureSettings.Alerts("1",15,90,List.of(10,20),30,"");Instant now=Instant.parse("2026-11-06T00:00:00Z");long gib=1L<<30;
        assertTrue(alerts.check(new Alerts.Reading(20,gib,4*gib,12),settings,now).isEmpty(),"first reading only sets the milestone baseline");
        assertTrue(alerts.check(new Alerts.Reading(12,gib,4*gib,12),settings,now.plusSeconds(30)).isEmpty(),"one low reading is a spike");
        var low=alerts.check(new Alerts.Reading(12,gib,4*gib,12),settings,now.plusSeconds(60));assertEquals(1,low.size());assertEquals(Alerts.Kind.TPS,low.get(0).kind());assertEquals("12.0",low.get(0).values().get("tps"));
        assertTrue(alerts.check(new Alerts.Reading(12,gib,4*gib,12),settings,now.plusSeconds(90)).isEmpty(),"cooldown");
        assertEquals(1,alerts.check(new Alerts.Reading(12,gib,4*gib,12),settings,now.plusSeconds(60+1800)).size());
        alerts.check(new Alerts.Reading(20,95L<<20,100L<<20,12),settings,now.plusSeconds(2000));var memory=alerts.check(new Alerts.Reading(20,95L<<20,100L<<20,12),settings,now.plusSeconds(2030));
        assertEquals(List.of(Alerts.Kind.MEMORY),memory.stream().map(Alerts.Alert::kind).toList());assertEquals("95",memory.get(0).values().get("percent"));
        var players=alerts.check(new Alerts.Reading(20,gib,4*gib,21),settings,now.plusSeconds(2060));assertEquals(1,players.size());assertEquals("20",players.get(0).values().get("milestone"));
        assertTrue(alerts.check(new Alerts.Reading(20,gib,4*gib,25),settings,now.plusSeconds(4000)).isEmpty(),"already announced");
        alerts.check(new Alerts.Reading(20,gib,4*gib,5),settings,now.plusSeconds(4030));
        assertEquals("10",alerts.check(new Alerts.Reading(20,gib,4*gib,11),settings,now.plusSeconds(6000)).get(0).values().get("milestone"),"dropping below re-arms it");
    }
    @Test void spigotTpsIsUnknownAndNeverAlerts(){
        Alerts alerts=new Alerts(mock(DyDiscord.class));var settings=new FeatureSettings.Alerts("1",15,0,List.of(),5,"");
        for(int i=0;i<4;i++)assertTrue(alerts.check(new Alerts.Reading(-1,0,0,0),settings,Instant.EPOCH.plusSeconds(i*30L)).isEmpty());
        assertEquals(-1,dev.dydiscord.util.Tps.current(new Object()));assertEquals(19.5,dev.dydiscord.util.Tps.current(new PaperLike()));
    }

    @Test void attachmentsBecomeClickableLabelsOnlyForDiscordMedia(){
        assertTrue(ChatText.discordMedia("https://cdn.discordapp.com/attachments/1/2/cat.png"));assertTrue(ChatText.discordMedia("https://media.discordapp.net/stickers/1.png"));
        for(String bad:List.of("http://cdn.discordapp.com/a.png","https://evil.example/a.png","https://user@cdn.discordapp.com/a.png","https://cdn.discordapp.com:8443/a.png","https://cdn.discordapp.com/a b.png"))assertFalse(ChatText.discordMedia(bad),bad);
        TextComponent line=new TextComponent("hi");
        List<ChatText.Attachment> items=new ArrayList<>(List.of(new ChatText.Attachment("image","cat&a.png","https://cdn.discordapp.com/attachments/1/2/cat.png"),new ChatText.Attachment("file","x","https://evil.example/x")));
        for(int i=0;i<8;i++)items.add(new ChatText.Attachment("sticker","s"+i,"https://media.discordapp.net/stickers/"+i+".png"));
        ChatText.attachments(line,items,kind->"&b["+kind+"]","&7Open {name}");
        List<BaseComponent> links=line.getExtra().stream().filter(part->part.getClickEvent()!=null).toList();
        assertEquals(ChatText.MAX_ATTACHMENTS,links.size());assertEquals("https://cdn.discordapp.com/attachments/1/2/cat.png",links.get(0).getClickEvent().getValue());assertEquals(ClickEvent.Action.OPEN_URL,links.get(0).getClickEvent().getAction());
        assertTrue(links.get(0).toPlainText().contains("[image]"));assertTrue(line.getExtra().stream().noneMatch(part->part.getClickEvent()!=null&&part.getClickEvent().getValue().contains("evil")));
    }
    @Test void attachmentsAndStickersAreReadFromTheDiscordMessage(){
        Message message=mock(Message.class);Message.Attachment image=mock(Message.Attachment.class);when(image.isImage()).thenReturn(true);when(image.getFileName()).thenReturn("cat.png");when(image.getUrl()).thenReturn("https://cdn.discordapp.com/attachments/1/2/cat.png");
        Message.Attachment other=mock(Message.Attachment.class);when(other.getFileName()).thenReturn("notes.txt");when(other.getUrl()).thenReturn("https://cdn.discordapp.com/attachments/1/2/notes.txt");
        StickerItem sticker=mock(StickerItem.class);when(sticker.getName()).thenReturn("wave");when(sticker.getIconUrl()).thenReturn("https://media.discordapp.net/stickers/9.png");
        when(message.getAttachments()).thenReturn(List.of(image,other));when(message.getStickers()).thenReturn(List.of(sticker));
        assertEquals(List.of("image","file","sticker"),ChatBridge.attachments(message).stream().map(ChatText.Attachment::kind).toList());
    }
    @Test void precompiledChatCleanupKeepsTheSameResults(){
        assertEquals("Admin Alex",ChatText.plain("&x&f&f&0&0&0&0&lAdmin &#55ff55Alex",64));assertEquals("a b",ChatText.plain("a\u0000\n\tb",64));assertEquals("x",ChatText.plain("§cx",64));
        assertEquals("Dc Cly",ChatText.webhookName("","discord clyde"));
        assertEquals("**bold\\-ish** Alex » hi\\!",ChatText.discord("{rank}{name} » {message}","bold-ish","Alex","hi!"));
    }
}
