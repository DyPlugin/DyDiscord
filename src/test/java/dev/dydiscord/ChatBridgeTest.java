package dev.dydiscord;

import dev.dydiscord.chat.*;
import dev.dydiscord.config.*;
import dev.dydiscord.discord.Bot;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatBridgeTest {
    static final String CHANNEL="423456789012345678",GUILD="123456789012345678";
    @TempDir Path folder;Configuration config;DyDiscord plugin;Bot bot;ChatBridge bridge;TestSupport.Time time;Player player;Server server;
    @BeforeEach void setup()throws Exception{
        config=TestSupport.config(folder);config.change("bot.yml","guild-id",GUILD);config.change("bot.yml","chat.channel-id",CHANNEL);config.change("bot.yml","chat.minecraft-to-discord",true);config.change("bot.yml","chat.discord-to-minecraft",true);
        plugin=mock(DyDiscord.class);bot=mock(Bot.class);time=new TestSupport.Time();when(plugin.clock()).thenReturn(time);when(plugin.settings()).thenAnswer(i->config.active().settings());when(plugin.messages()).thenAnswer(i->config.active().messages());when(plugin.bot()).thenReturn(bot);when(plugin.ready()).thenReturn(true);when(bot.usable()).thenReturn(true);when(bot.content()).thenReturn(true);when(bot.alive(7)).thenReturn(true);when(bot.generation()).thenReturn(7L);when(bot.applied()).thenAnswer(i->config.active().settings());when(bot.activeLanguage()).thenReturn("en");when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("chat-tests"));
        server=mock(Server.class);when(plugin.getServer()).thenReturn(server);player=mock(Player.class);when(player.getName()).thenReturn("Alice");when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);when(player.getLocation()).thenReturn(new org.bukkit.Location(null,0,0,0));when(player.hasPermission(anyString())).thenReturn(true);when(player.getMetadata(anyString())).thenReturn(List.of());when(player.canSee(any(Player.class))).thenReturn(true);doReturn(List.of(player)).when(server).getOnlinePlayers();
        when(plugin.getDataFolder()).thenReturn(folder.toFile());doAnswer(i->{((Runnable)i.getArgument(0)).run();return null;}).when(plugin).onMain(any());bridge=new ChatBridge(plugin);when(plugin.chat()).thenReturn(bridge);bridge.preference(player.getUniqueId()).get(5,TimeUnit.SECONDS);
    }
    @AfterEach void close(){bridge.close();}
    @Test void consecutiveMinecraftMessagesAreNotSilentlyDropped(){
        bridge.minecraft(player,"Bir");bridge.minecraft(player,"İki");bridge.minecraft(player,"Üç");
        assertEquals(3,bridge.queued());
    }
    @Test void consecutiveDiscordMessagesKeepOrderWhileDuplicateIdsAreStillIgnored(){
        var chat=mock(Player.Spigot.class);when(player.spigot()).thenReturn(chat);
        bridge.receive(incoming("burst-1",CHANNEL,"Bir"),7);bridge.receive(incoming("burst-2",CHANNEL,"İki"),7);bridge.receive(incoming("burst-2",CHANNEL,"İki"),7);bridge.receive(incoming("burst-3",CHANNEL,"Üç"),7);
        var messages=org.mockito.ArgumentCaptor.forClass(net.md_5.bungee.api.chat.TextComponent.class);verify(chat,times(3)).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().get(0).toPlainText().endsWith("Bir"));assertTrue(messages.getAllValues().get(1).toPlainText().endsWith("İki"));assertTrue(messages.getAllValues().get(2).toPlainText().endsWith("Üç"));
    }
    @Test void rankAndNoRankFormattingCannotPingOrInjectMarkdown(){
        assertEquals("**Admin** Alice **»** Hi",ChatText.discord("{rank}{name} **»** {message}","&cAdmin","Alice","Hi"));assertEquals("Alice **»** Hi",ChatText.discord("{rank}{name} **»** {message}","","Alice","Hi"));
        String text=ChatText.discord("{rank}{name} **»** {message}","@everyone **Boss**","@here","**fake**\n@everyone <@123> <@&456> §cRed &lBold\u202efake");assertFalse(text.contains("@everyone"));assertFalse(text.contains("@here"));assertFalse(text.contains("<@"));assertFalse(text.contains("\n"));assertFalse(text.contains("§"));assertTrue(text.contains("\\*\\*fake\\*\\*"));assertFalse(text.contains("\u202e"));
        assertEquals("\\# Fake",ChatText.event("{death}","Alice","# Fake"));
    }
    @Test void minecraftUsesComponentsWithoutBoldCodesOrEmptyRoleSpacing(){
        var message=ChatText.minecraft("&9[Discord] &7{role}&f{name} &7» &f{message}","",0,"**Name**","**Hello**\n§4Fake &cRed");assertEquals("[Discord] Name » Hello Fake Red",message.toPlainText());for(var child:message.getExtra())assertFalse(child.isBold());assertFalse(message.toPlainText().contains("**"));
        assertEquals("[Discord] Admin Name » Hi",ChatText.minecraft("&9[Discord] &7{role}&f{name} &7» &f{message}","Admin",0xFFFFFF,"Name","Hi").toPlainText());assertEquals(net.md_5.bungee.api.ChatColor.of("#010101"),ChatText.readableColor(0x010101));
    }
    @Test void highestSuitableRoleExcludesEveryoneAndIntegrationRoles(){Role everyone=mock(Role.class),managed=mock(Role.class),low=mock(Role.class),high=mock(Role.class);when(everyone.isPublicRole()).thenReturn(true);when(managed.isManaged()).thenReturn(true);when(managed.getPosition()).thenReturn(100);when(low.getPosition()).thenReturn(1);when(high.getPosition()).thenReturn(20);assertSame(high,ChatBridge.highestRole(List.of(everyone,low,managed,high)));assertNull(ChatBridge.highestRole(List.of(everyone,managed)));}
    @Test void cancelledMutedHiddenAndRestrictedChatIsNeverPublic(){UUID a=UUID.randomUUID(),b=UUID.randomUUID();Set<UUID> all=Set.of(a,b);assertTrue(ChatBridge.publicChat(false,false,false,all,all));assertFalse(ChatBridge.publicChat(true,false,false,all,all));assertFalse(ChatBridge.publicChat(false,true,false,all,all));assertFalse(ChatBridge.publicChat(false,false,true,all,all));assertFalse(ChatBridge.publicChat(false,false,false,Set.of(a),all));assertFalse(ChatBridge.publicChat(false,false,false,Set.of(),Set.of()));}
    @Test void newHumanTextOnlyExcludesCommandsBotsWebhooksAndHistory(){assertTrue(ChatBridge.human(false,false,true,"Hello","dy!"));assertFalse(ChatBridge.human(true,false,true,"Hello","dy!"));assertFalse(ChatBridge.human(false,true,true,"Hello","dy!"));assertFalse(ChatBridge.human(false,false,false,"Hello","dy!"));assertFalse(ChatBridge.human(false,false,true,"dy!help","dy!"));assertFalse(ChatBridge.human(false,false,true,"","dy!"));}
    @Test void boundedQueueExpiresAndNeverReplaysAcrossReloadOrDisconnect()throws Exception{
        for(int i=0;i<128;i++)assertTrue(bridge.offer("text",null));assertFalse(bridge.offer("overflow",null));time.advance(6);bridge.flush();verify(bot,never()).sendChat(any(),any());bridge.invalidate();assertEquals(0,bridge.queued());assertTrue(bridge.offer("new",null));when(bot.usable()).thenReturn(false);bridge.flush();assertEquals(0,bridge.queued());when(bot.usable()).thenReturn(true);bridge.flush();verify(bot,never()).sendChat(any(),any());
    }
    @Test void failedNetworkDropsQueueAndDoesNotRetry()throws Exception{bridge.offer("one",null);bridge.offer("two",null);doThrow(new IllegalStateException("synthetic failure")).when(bot).sendChat(any(),any());bridge.flush();assertEquals(0,bridge.queued());assertFalse(bridge.offer("during backoff",null));time.advance(16);assertTrue(bridge.offer("later",null));verify(bot,times(1)).sendChat(any(),any());}
    @Test void channelMustDifferFromConsoleAndPanelAndInvalidReloadKeepsSettings()throws Exception{var active=config.active();assertThrows(IllegalArgumentException.class,()->config.change("bot.yml","console.channel-id",CHANNEL));assertSame(active,config.active());assertThrows(IllegalArgumentException.class,()->config.change("bot.yml","panel.channel-id",CHANNEL));assertThrows(IllegalArgumentException.class,()->config.change("bot.yml","chat.events.join.color","bad"));}
    @Test void incomingToggleNeedsRestartButOutgoingDoesNot()throws Exception{var old=config.active().settings();config.change("bot.yml","chat.minecraft-to-discord",false);assertFalse(old.needsRestart(config.active().settings()));config.change("bot.yml","chat.discord-to-minecraft",false);assertTrue(old.needsRestart(config.active().settings()));}
    private MessageReceivedEvent incoming(String id,String channel,String text){var event=mock(MessageReceivedEvent.class);Message message=mock(Message.class);User user=mock(User.class);Guild guild=mock(Guild.class);Member member=mock(Member.class);MessageChannelUnion ch=mock(MessageChannelUnion.class);when(ch.getId()).thenReturn(channel);when(guild.getId()).thenReturn(GUILD);when(event.isFromGuild()).thenReturn(true);when(event.getGuild()).thenReturn(guild);when(event.getChannel()).thenReturn(ch);when(event.getMessageId()).thenReturn(id);when(event.getMessage()).thenReturn(message);when(message.getContentRaw()).thenReturn(text);when(message.getContentDisplay()).thenReturn(text);when(message.getTimeCreated()).thenReturn(OffsetDateTime.ofInstant(time.instant(),ZoneOffset.UTC));when(event.getAuthor()).thenReturn(user);when(user.getId()).thenReturn("523456789012345678");when(event.getMember()).thenReturn(member);when(member.getEffectiveName()).thenReturn("Discord Alice");when(member.getRoles()).thenReturn(List.of());return event;}
    @Test void reloadDuringIncomingAdmissionCannotDeliverOldChannelText()throws Exception{
        Player.Spigot spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);var event=incoming("reload-race",CHANNEL,"Old channel text");
        when(event.getMessage().getContentDisplay()).thenAnswer(call->{config.change("bot.yml","chat.channel-id","723456789012345678");bridge.invalidate();return "Old channel text";});
        bridge.receive(event,7);verifyNoInteractions(spigot);assertEquals(0,bridge.queued());
    }
    @Test void queuedIncomingCannotUseNewChannelBeforeReloadInvalidation()throws Exception{
        List<Runnable> tasks=new ArrayList<>();doAnswer(call->{tasks.add(call.getArgument(0));return null;}).when(plugin).onMain(any());var spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);
        bridge.receive(incoming("queued-reload",CHANNEL,"Old channel"),7);config.change("bot.yml","chat.channel-id","723456789012345678");tasks.forEach(Runnable::run);verifyNoInteractions(spigot);assertEquals(0,bridge.queued());
    }
    @Test void queuedOutgoingCannotBeSentToNewChannelBeforeInvalidation()throws Exception{
        assertTrue(bridge.offer("Old public message",null));config.change("bot.yml","chat.channel-id","723456789012345678");bridge.flush();verify(bot,never()).sendChat(any(),any());assertEquals(0,bridge.queued());
    }
    @Test void duplicateDiscordMessageHasOneDeliveryAndNoCommandDispatch(){Player.Spigot spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);var event=incoming("623456789012345678",CHANNEL,"Hello");bridge.receive(event,7);bridge.receive(event,7);verify(spigot,times(1)).sendMessage(any(net.md_5.bungee.api.chat.BaseComponent.class));verify(server,never()).dispatchCommand(any(),anyString());assertEquals(0,bridge.queued());}
    @Test void otherChannelAndPrefixCannotReachPlayersOrConsole(){var spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);bridge.receive(incoming("one","other","op Alice"),7);bridge.receive(incoming("two",CHANNEL,"dy!help"),7);verifyNoInteractions(spigot);verify(server,never()).dispatchCommand(any(),anyString());}
    @Test void persistedInboundPreferenceFiltersQueuedDeliveryAndUnknownPlayers()throws Exception{
        var spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);
        List<Runnable> tasks=new ArrayList<>();doAnswer(i->{tasks.add(i.getArgument(0));return null;}).when(plugin).onMain(any());
        bridge.receive(incoming("before-optout",CHANNEL,"Hello"),7);bridge.preference(player.getUniqueId(),"inbound",false).get(5,TimeUnit.SECONDS);tasks.forEach(Runnable::run);verifyNoInteractions(spigot);
        bridge.leave(player.getUniqueId());assertNull(bridge.preferencePreview(player.getUniqueId()));
        time.advance(2);bridge.receive(incoming("after-leave",CHANNEL,"Hello again"),7);tasks.forEach(Runnable::run);verifyNoInteractions(spigot);
    }
    @Test void personalIncomingPreferenceNeverControlsPublicOutgoingChat()throws Exception{
        bridge.preference(player.getUniqueId(),"inbound",false).get(5,TimeUnit.SECONDS);
        assertThrows(IllegalArgumentException.class,()->bridge.preference(player.getUniqueId(),"outbound",false));
        bridge.minecraft(player,"Public outgoing");assertEquals(1,bridge.queued());bridge.flush();verify(bot).sendChat(any(),any());
    }
    @Test void pausesDiscardQueuedWorkAndResumeOnlyFreshMessages()throws Exception{
        bridge.offer("old",null);assertTrue(bridge.pause("outbound",true));assertEquals(0,bridge.queued());assertFalse(bridge.offer("paused",null));assertTrue(bridge.paused("outbound"));
        bridge.pause("outbound",false);assertTrue(bridge.offer("fresh",null));bridge.flush();verify(bot).sendChat(argThat(value->value.text().equals("fresh")),any());
        var spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);bridge.pause("inbound",true);bridge.receive(incoming("paused-in",CHANNEL,"Hello"),7);verifyNoInteractions(spigot);
        assertFalse(bridge.pause("unknown",true));
    }
    @Test void pauseResumeDuringAdmissionNeverReplaysPrePauseWork()throws Exception{
        var spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);var event=incoming("pause-race",CHANNEL,"Stale");
        when(event.getMessage().getContentDisplay()).thenAnswer(call->{bridge.pause("inbound",true);bridge.pause("inbound",false);return "Stale";});
        bridge.receive(event,7);verifyNoInteractions(spigot);
        when(player.getName()).thenAnswer(call->{bridge.pause("outbound",true);bridge.pause("outbound",false);return "Alice";});
        bridge.minecraft(player,"Stale outgoing");bridge.flush();verify(bot,never()).sendChat(any(),any());
    }
    @Test void slowmodePacesDeliveryWithoutAccumulatingExpiredChat()throws Exception{
        when(bot.chatSlowmodeSeconds()).thenReturn(10);bridge.offer("first",null);bridge.offer("stale",null);bridge.flush();bridge.flush();verify(bot,times(1)).sendChat(any(),any());
        time.advance(11);bridge.flush();assertEquals(0,bridge.queued());verify(bot,times(1)).sendChat(any(),any());
        bridge.offer("fresh",null);bridge.flush();verify(bot,times(2)).sendChat(any(),any());
    }
    @Test void replyAuthorIsSafeAndNoSeparateSoundNotificationIsAvailable()throws Exception{
        var spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);var event=incoming("reply",CHANNEL,"Alice hi");Message replied=mock(Message.class);User author=mock(User.class);when(replied.getAuthor()).thenReturn(author);when(author.getName()).thenReturn("&c**Boss**\nName");when(event.getMessage().getReferencedMessage()).thenReturn(replied);
        bridge.receive(event,7);var payload=org.mockito.ArgumentCaptor.forClass(net.md_5.bungee.api.chat.BaseComponent.class);verify(spigot).sendMessage(payload.capture());assertTrue(payload.getValue().toPlainText().contains("↪ Boss Name"));verify(player,never()).playSound(any(org.bukkit.Location.class),any(org.bukkit.Sound.class),anyFloat(),anyFloat());
        assertThrows(IllegalArgumentException.class,()->bridge.preference(player.getUniqueId(),"notify",true));
    }
    @Test void bundledIncomingFormatHasGrayTextAndDarkGraySeparator(){
        var text=ChatText.minecraft(plugin.messages().get(true,"tr","chat-to-minecraft"),"VIP",0xabcdef,"Alice","Hello");
        assertEquals("[Discord] VIP Alice >> Hello",text.toPlainText());
        assertTrue(text.getExtra().stream().anyMatch(part->part.toPlainText().contains(">>")&&part.getColor().equals(net.md_5.bungee.api.ChatColor.DARK_GRAY)));
        assertTrue(text.getExtra().stream().anyMatch(part->part.toPlainText().equals("Hello")&&part.getColor().equals(net.md_5.bungee.api.ChatColor.GRAY)));
    }
    @Test void delayedIncomingWorkExpiresBeforeTouchingPlayers(){List<Runnable> tasks=new ArrayList<>();doAnswer(i->{tasks.add(i.getArgument(0));return null;}).when(plugin).onMain(any());var spigot=mock(Player.Spigot.class);when(player.spigot()).thenReturn(spigot);bridge.receive(incoming("one",CHANNEL,"Hello"),7);time.advance(6);tasks.forEach(Runnable::run);verifyNoInteractions(spigot);}
    @Test void cancelledMutedAndPrivateLegacyEventsDoNotReachBot()throws Exception{ChatEvents events=new ChatEvents(plugin);AsyncPlayerChatEvent cancelled=new AsyncPlayerChatEvent(false,player,"Hidden",new HashSet<>(Set.of(player)));cancelled.setCancelled(true);events.legacy(cancelled);verify(bot,never()).sendChat(any(),any());assertEquals(0,bridge.queued());MetadataValue muted=mock(MetadataValue.class);when(muted.asBoolean()).thenReturn(true);when(player.getMetadata("muted")).thenReturn(List.of(muted));events.legacy(new AsyncPlayerChatEvent(false,player,"muted",new HashSet<>(Set.of(player))));assertEquals(0,bridge.queued());when(player.getMetadata("muted")).thenReturn(List.of());events.legacy(new AsyncPlayerChatEvent(false,player,"private",new HashSet<>()));assertEquals(0,bridge.queued());}
    @Test void unknownChatPluginCannotLeakItsPrivateOrLateModeratedMessages(){org.bukkit.plugin.Plugin other=mock(org.bukkit.plugin.Plugin.class);when(other.isEnabled()).thenReturn(true);org.bukkit.event.Listener listener=new org.bukkit.event.Listener(){};var registered=new org.bukkit.plugin.RegisteredListener(listener,(l,e)->{},org.bukkit.event.EventPriority.MONITOR,other,false);AsyncPlayerChatEvent.getHandlerList().register(registered);try{new ChatEvents(plugin).legacy(new AsyncPlayerChatEvent(false,player,"private plugin message",new HashSet<>(Set.of(player))));assertEquals(0,bridge.queued());}finally{AsyncPlayerChatEvent.getHandlerList().unregister(registered);}}
    @Test void privateTeamDeathsAndHiddenKillersAreNotDisclosed(){assertTrue(bridge.publicDeath(player,"Alice fell"));org.bukkit.scoreboard.Scoreboard board=mock(org.bukkit.scoreboard.Scoreboard.class);org.bukkit.scoreboard.Team team=mock(org.bukkit.scoreboard.Team.class);when(player.getScoreboard()).thenReturn(board);when(board.getEntryTeam("Alice")).thenReturn(team);when(team.getOption(org.bukkit.scoreboard.Team.Option.DEATH_MESSAGE_VISIBILITY)).thenReturn(org.bukkit.scoreboard.Team.OptionStatus.NEVER);assertFalse(bridge.publicDeath(player,"Alice fell"));when(team.getOption(org.bukkit.scoreboard.Team.Option.DEATH_MESSAGE_VISIBILITY)).thenReturn(org.bukkit.scoreboard.Team.OptionStatus.FOR_OWN_TEAM);assertFalse(bridge.publicDeath(player,"Alice fell"));when(team.getOption(org.bukkit.scoreboard.Team.Option.DEATH_MESSAGE_VISIBILITY)).thenReturn(org.bukkit.scoreboard.Team.OptionStatus.ALWAYS);Player hidden=mock(Player.class);when(hidden.getUniqueId()).thenReturn(UUID.randomUUID());when(hidden.getName()).thenReturn("HiddenAdmin");when(hidden.getMetadata(anyString())).thenReturn(List.of());MetadataValue flag=mock(MetadataValue.class);when(flag.asBoolean()).thenReturn(true);when(hidden.getMetadata("vanished")).thenReturn(List.of(flag));when(player.getKiller()).thenReturn(hidden);assertFalse(bridge.publicDeath(player,"Alice was slain"));when(player.getKiller()).thenReturn(null);doReturn(List.of(player,hidden)).when(server).getOnlinePlayers();assertFalse(bridge.publicDeath(player,"Alice was slain by HiddenAdmin"));}
    @Test void eachPublicEventIsQueuedOnceAndSuppressedOrHiddenEventsAreIgnored()throws Exception{
        config.change("bot.yml","chat.events.join.enabled",true);config.change("bot.yml","chat.events.quit.enabled",true);config.change("bot.yml","chat.events.death.enabled",true);ChatEvents events=new ChatEvents(plugin);PlayerJoinEvent join=new PlayerJoinEvent(player,"joined");events.join(join);events.join(join);assertEquals(1,bridge.queued());events.join(new PlayerJoinEvent(player,null));assertEquals(1,bridge.queued());PlayerQuitEvent quit=new PlayerQuitEvent(player,"quit");events.quit(quit);events.quit(quit);assertEquals(2,bridge.queued());
        World world=mock(World.class);when(player.getWorld()).thenReturn(world);when(world.getGameRuleValue(GameRule.SHOW_DEATH_MESSAGES)).thenReturn(true);PlayerDeathEvent death=mock(PlayerDeathEvent.class);when(death.getEntity()).thenReturn(player);when(death.getDeathMessage()).thenReturn("Alice fell from a high place");events.death(death);events.death(death);assertEquals(3,bridge.queued());bridge.flush();bridge.flush();bridge.flush();var payload=org.mockito.ArgumentCaptor.forClass(ChatBridge.Outgoing.class);verify(bot,times(3)).sendChat(payload.capture(),any());assertEquals("Alice fell from a high place",payload.getAllValues().get(2).text());assertNotNull(payload.getAllValues().get(2).color());
        when(world.getGameRuleValue(GameRule.SHOW_DEATH_MESSAGES)).thenReturn(false);events.death(mock(PlayerDeathEvent.class,invocation->{if(invocation.getMethod().getName().equals("getEntity"))return player;return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);}));assertEquals(0,bridge.queued());MetadataValue vanished=mock(MetadataValue.class);when(vanished.asBoolean()).thenReturn(true);when(player.getMetadata("vanished")).thenReturn(List.of(vanished));events.join(new PlayerJoinEvent(player,"joined"));events.quit(new PlayerQuitEvent(player,"quit"));assertEquals(0,bridge.queued());
    }
    public static class ServerState {private final boolean stop;public ServerState(boolean stop){this.stop=stop;}public boolean isStopping(){return stop;}}
    @Test void shutdownOnlyRecognizesActualServerStoppingAndHasBoundedWait(){assertFalse(ShutdownNotice.stopping(new ServerState(false)));assertTrue(ShutdownNotice.stopping(new ServerState(true)));assertFalse(ShutdownNotice.stopping(new Object()));java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();assertFalse(ShutdownNotice.send(false,true,()->{calls.incrementAndGet();return CompletableFuture.completedFuture(null);},30));assertEquals(0,calls.get());CompletableFuture<Void> blocked=new CompletableFuture<>();long start=System.nanoTime();assertFalse(ShutdownNotice.send(true,true,()->blocked,30));assertTrue(blocked.isCancelled());assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));assertTrue(ShutdownNotice.send(true,true,()->CompletableFuture.completedFuture(null),30));}
}
