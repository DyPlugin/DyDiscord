package dev.dydiscord;

import dev.dydiscord.chat.*;
import dev.dydiscord.discord.Bot;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatProviderAndTransportTest {
    @TempDir Path folder;
    public static class Query {}
    public static class Meta {public String getPrefix(){return "&6Admin";}}
    public static class Cached {final Query expected;Cached(Query query){expected=query;}public Meta getMetaData(Query query){assertSame(expected,query);return new Meta();}}
    public static class UserData {final Cached cached;UserData(Query query){cached=new Cached(query);}public Cached getCachedData(){return cached;}public String getPrimaryGroup(){return "staff";}}
    public static class Users {final UserData user;Users(UserData user){this.user=user;}public UserData getUser(UUID uuid){return user;}}
    public static class Context {final Query query;Context(Query query){this.query=query;}public Query getQueryOptions(Object player){return query;}}
    public static class LuckPermsProvider implements net.luckperms.api.LuckPerms {final Query query=new Query();public Users getUserManager(){return new Users(new UserData(query));}public Context getContextManager(){return new Context(query);}}
    @Test void luckPermsContextPrefixTakesPriorityOverVaultAndAbsentProvidersGiveNoRank(){
        DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);PluginManager manager=mock(PluginManager.class);ServicesManager services=mock(ServicesManager.class);when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(manager);when(server.getServicesManager()).thenReturn(services);Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals("",new Ranks(plugin).rank(player));clearInvocations(manager);Plugin lp=mock(Plugin.class);when(lp.isEnabled()).thenReturn(true);when(manager.getPlugin("LuckPerms")).thenReturn(lp);when(services.load(net.luckperms.api.LuckPerms.class)).thenReturn(new LuckPermsProvider());assertEquals("Admin",new Ranks(plugin).rank(player));verify(manager,never()).getPlugin("Vault");
    }
    @Test void vaultPrefixAndGroupFallbackUseRegisteredPublicService(){
        DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);PluginManager manager=mock(PluginManager.class);ServicesManager services=mock(ServicesManager.class);when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(manager);when(server.getServicesManager()).thenReturn(services);Player player=mock(Player.class);Plugin vault=mock(Plugin.class);when(vault.isEnabled()).thenReturn(true);when(manager.getPlugin("Vault")).thenReturn(vault);net.milkbowl.vault.chat.Chat api=mock(net.milkbowl.vault.chat.Chat.class);when(services.load(net.milkbowl.vault.chat.Chat.class)).thenReturn(api);when(api.getPlayerPrefix(player)).thenReturn("&9Moderator");Ranks ranks=new Ranks(plugin);assertEquals("Moderator",ranks.rank(player));when(api.getPlayerPrefix(player)).thenReturn("");when(api.getPrimaryGroup(player)).thenReturn("helper");assertEquals("helper",ranks.rank(player));when(api.getPrimaryGroup(player)).thenReturn("default");assertEquals("",ranks.rank(player));
    }
    @Test void chatAndEventTransportDisableAllMentionsAndReplies()throws Exception{
        Configuration config=TestSupport.config(folder);config.change("bot.yml","chat.channel-id","423456789012345678");DyDiscord plugin=mock(DyDiscord.class);when(plugin.settings()).thenAnswer(i->config.active().settings());when(plugin.messages()).thenAnswer(i->config.active().messages());when(plugin.clock()).thenReturn(new TestSupport.Time());Store store=mock(Store.class);when(store.panel()).thenReturn(Optional.empty());when(plugin.store()).thenReturn(store);Bot bot=spy(new Bot(plugin));TextChannel channel=mock(TextChannel.class);Guild guild=mock(Guild.class);SelfMember self=mock(SelfMember.class);when(channel.getGuild()).thenReturn(guild);when(guild.getSelfMember()).thenReturn(self);when(self.hasPermission(eq(channel),any(net.dv8tion.jda.api.Permission[].class))).thenReturn(true);doReturn(channel).when(bot).channel(anyString());
        MessageCreateAction action=mock(MessageCreateAction.class,RETURNS_SELF);when(action.submit()).thenReturn(CompletableFuture.completedFuture(mock(Message.class)));when(channel.sendMessage(anyString())).thenReturn(action);when(channel.sendMessage(any(net.dv8tion.jda.api.utils.messages.MessageCreateData.class))).thenReturn(action);when(channel.sendMessageEmbeds(any(MessageEmbed.class),any(MessageEmbed[].class))).thenReturn(action);
        bot.sendChat(new ChatBridge.Outgoing("Hello @everyone",null,0,0,Instant.MAX),()->true);bot.sendChat(new ChatBridge.Outgoing("Alice joined",0x55AA55,0,0,Instant.MAX),()->true);verify(action,times(2)).setAllowedMentions(List.of());verify(action,times(2)).mentionRepliedUser(false);
    }
    @Test void restartNeverSendsShutdownButConfirmedStoppingSendsBeforeBotCloses()throws Exception{
        Configuration config=TestSupport.config(folder);config.change("bot.yml","console.channel-id","323456789012345678");config.change("bot.yml","bot.language","tr");DyDiscord plugin=mock(DyDiscord.class);when(plugin.settings()).thenReturn(config.active().settings());when(plugin.messages()).thenReturn(config.active().messages());when(plugin.clock()).thenReturn(new TestSupport.Time());when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("shutdown-tests"));Store store=mock(Store.class);when(store.panel()).thenReturn(Optional.empty());when(plugin.store()).thenReturn(store);Bot bot=spy(new Bot(plugin));doReturn(true).when(bot).usable();doNothing().when(bot).validateChannel(anyString(),eq(true));TextChannel channel=mock(TextChannel.class);doReturn(channel).when(bot).channel(anyString());MessageCreateAction action=mock(MessageCreateAction.class,RETURNS_SELF);when(channel.sendMessage(anyString())).thenReturn(action);when(action.submit()).thenReturn(CompletableFuture.completedFuture(mock(Message.class)));
        Guild guild=mock(Guild.class);SelfMember self=mock(SelfMember.class);when(channel.getGuild()).thenReturn(guild);when(guild.getSelfMember()).thenReturn(self);when(self.hasPermission(channel,net.dv8tion.jda.api.Permission.MESSAGE_EMBED_LINKS)).thenReturn(true);when(channel.sendMessageEmbeds(any(MessageEmbed.class),any(MessageEmbed[].class))).thenReturn(action);when(channel.sendMessage(any(net.dv8tion.jda.api.utils.messages.MessageCreateData.class))).thenReturn(action);
        bot.serverShutdownNotice(false);verify(channel,never()).sendMessage(any(net.dv8tion.jda.api.utils.messages.MessageCreateData.class));bot.serverShutdownNotice(true);var card=org.mockito.ArgumentCaptor.forClass(net.dv8tion.jda.api.utils.messages.MessageCreateData.class);verify(channel).sendMessage(card.capture());assertEquals("Sunucu kapanıyor.\n",TestSupport.v2Text(card.getValue()));assertEquals(0xFFAA00,card.getValue().getComponents().get(0).asContainer().getAccentColorRaw());verify(channel,never()).sendMessage(anyString());verify(action).setAllowedMentions(List.of());verify(action).mentionRepliedUser(false);assertFalse(plugin.ready(),"The notice must work while ordinary work is already closed");
    }
}
