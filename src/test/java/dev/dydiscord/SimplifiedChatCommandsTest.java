package dev.dydiscord;
import dev.dydiscord.command.Definition;
import dev.dydiscord.community.CommunityCommands;
import dev.dydiscord.config.*;
import dev.dydiscord.chat.*;
import dev.dydiscord.discord.Bot;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SimplifiedChatCommandsTest {
 @TempDir Path folder;
 DyDiscord host;Configuration config;CommandSender sender;CommunityCommands commands;
 @BeforeEach void setup()throws Exception{
  config=spy(TestSupport.config(folder));host=mock(DyDiscord.class);sender=mock(CommandSender.class);
  when(host.configuration()).thenReturn(config);when(host.settings()).thenAnswer(c->config.active().settings());when(host.bot()).thenReturn(mock(Bot.class));when(host.chat()).thenReturn(mock(ChatBridge.class));when(host.language(any())).thenReturn("tr");when(host.messages()).thenAnswer(c->config.active().messages());when(sender.hasPermission(anyString())).thenReturn(true);
  doAnswer(c->((Callable<?>)c.getArgument(0)).call()).when(host).mainValue(any());
  doAnswer(c->{try{return CompletableFuture.completedFuture(((Callable<?>)c.getArgument(0)).call());}catch(Exception e){return CompletableFuture.failedFuture(e);}}).when(host).submit(any());commands=new CommunityCommands(host);
 }
 Definition definition(String key){return Definition.ALL.stream().filter(d->d.key().equals(key)).findFirst().orElseThrow();}
 @Test void unchangedDirectionsDoNotWriteOrInvalidate()throws Exception{
  commands.execute(sender,definition("chat-both"),new String[]{"kapat"});
  verify(config,never()).change(anyString(),anyMap(),any());verify(host.chat(),never()).invalidate();verify(host).message(eq(sender),eq("chat-already-off"),eq(Map.of("direction","Discord ↔ Minecraft")));
 }
 @Test void bothDirectionsAreValidatedAndPersistedTogether()throws Exception{
  commands.execute(sender,definition("chat-both"),new String[]{"aç"});
  assertTrue(config.active().settings().chat().toDiscord());assertTrue(config.active().settings().chat().toMinecraft());verify(config).change(eq("bot.yml"),eq(Map.of("chat.discord-to-minecraft",true,"chat.minecraft-to-discord",true)),any());
  verify(host.chat()).invalidate();verify(host).message(eq(sender),eq("chat-enabled"),any());
 }
 @Test void failedValidationChangesNeitherDirection()throws Exception{
  var bot=host.bot();doThrow(new IllegalStateException()).when(bot).validateChanges(any(),any());var before=config.active();
  commands.execute(sender,definition("chat-both"),new String[]{"aç"});assertSame(before,config.active());assertFalse(config.active().settings().chat().toMinecraft());assertFalse(config.active().settings().chat().toDiscord());
 }
 @Test void personalNoopDoesNotWrite()throws Exception{
  Player player=mock(Player.class);when(player.isOnline()).thenReturn(true);when(player.hasPermission(anyString())).thenReturn(true);UUID id=UUID.randomUUID();when(player.getUniqueId()).thenReturn(id);when(host.chat().preference(id)).thenReturn(CompletableFuture.completedFuture(new ChatPreferences.Preference(true,true,false)));
  commands.execute(player,definition("chat-inbound"),new String[]{"aç"});verify(host.chat(),never()).preference(any(),anyString(),anyBoolean());verify(host).message(eq(player),eq("chat-personal-already-on"),any());
 }
}

