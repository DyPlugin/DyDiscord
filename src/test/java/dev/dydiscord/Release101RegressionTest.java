package dev.dydiscord;

import dev.dydiscord.command.Router;
import dev.dydiscord.config.*;
import dev.dydiscord.discord.*;
import dev.dydiscord.chat.ChatText;
import dev.dydiscord.storage.Store;
import org.bukkit.command.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Release101RegressionTest {
    @TempDir Path folder;
    static void field(Object target,Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    DyDiscord host(Configuration config)throws Exception{
        var host=mock(DyDiscord.class);when(host.settings()).thenAnswer(c->config.active().settings());when(host.messages()).thenAnswer(c->config.active().messages());
        when(host.clock()).thenReturn(new TestSupport.Time());when(host.getLogger()).thenReturn(java.util.logging.Logger.getLogger("release101"));
        var store=mock(Store.class);when(store.panel()).thenReturn(Optional.empty());when(host.store()).thenReturn(store);return host;
    }
    @Test void initialPanelIsAutomaticallyCreatedWhenConfigured()throws Exception{
        var config=TestSupport.config(folder);config.change("bot.yml","guild-id","123456789012345678");config.change("bot.yml","panel.channel-id","223456789012345678");
        var bot=spy(new Bot(host(config)));field(bot,Bot.class,"applied",config.active().settings());doReturn(true).when(bot).usable();doNothing().when(bot).refreshPanel();
        bot.reconcilePanelMode();verify(bot).refreshPanel();
    }
    @Test void disabledOrUnconfiguredPanelIsNotAutomaticallySent()throws Exception{
        var config=TestSupport.config(folder);var bot=spy(new Bot(host(config)));doReturn(true).when(bot).usable();
        bot.reconcilePanelMode();verify(bot,never()).refreshPanel();config.change("bot.yml","panel.enabled",false);bot.reconcilePanelMode();verify(bot,never()).refreshPanel();
    }
    @Test void inactiveUnrelatedChatChannelCannotPreventPanelConfiguration()throws Exception{
        var config=TestSupport.config(folder);config.change("bot.yml","guild-id","123456789012345678");config.change("bot.yml","chat.channel-id","323456789012345678");
        Settings before=config.active().settings();config.change("bot.yml","panel.channel-id","223456789012345678");
        var bot=spy(new Bot(host(config)));field(bot,Bot.class,"applied",before);doReturn(true).when(bot).usable();doNothing().when(bot).validateChannel("223456789012345678",false);
        doThrow(new IllegalStateException("unrelated channel unavailable")).when(bot).validateChannel("323456789012345678",false);
        assertDoesNotThrow(()->bot.validateChanges(before,config.active().settings()));verify(bot,never()).validateChannel("323456789012345678",false);
    }
    @Test void malformedYamlIdentifiesItsFileWithoutLeakingValues()throws Exception{
        var config=TestSupport.config(folder);Files.writeString(folder.resolve("config.yml"),"secret: [private-example\n");
        var failure=assertThrows(java.io.IOException.class,config::load);assertTrue(failure.getMessage().contains("config.yml"));assertFalse(failure.getMessage().contains("private-example"));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={0x112233,0x000001,0x553366,0x55aaff})
    void discordRoleRgbIsPreservedExactly(int color){assertEquals(new java.awt.Color(color),ChatText.readableColor(color).getColor());}
    @Test void gradientRoleUsesOnlyItsPrimaryColor(){
        var role=mock(net.dv8tion.jda.api.entities.Role.class);when(role.getColors()).thenReturn(new net.dv8tion.jda.api.entities.RoleColors(0x112233,0xabcdef,0));
        assertEquals(0x112233,dev.dydiscord.chat.ChatBridge.primaryRoleColor(role));assertEquals(0,dev.dydiscord.chat.ChatBridge.primaryRoleColor(null));
    }
    @Test void failedStartupDoesNotKeepClaimingItIsInitializing()throws Exception{
        var host=mock(DyDiscord.class);field(host,DyDiscord.class,"configuration",new Configuration(folder));field(host,DyDiscord.class,"initializationFailed",true);doCallRealMethod().when(host).message(any(),anyString(),any());
        var sender=mock(CommandSender.class);try(var bukkit=mockStatic(org.bukkit.Bukkit.class)){bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);host.message(sender,"busy",Map.of());}
        var text=ArgumentCaptor.forClass(String.class);verify(sender).sendMessage(text.capture());assertTrue(text.getValue().contains("could not start"));assertFalse(text.getValue().contains("initializing"));
    }
    @Test void blankConsoleChannelDoesNotScheduleFailingPermissionChecks()throws Exception{
        var config=TestSupport.config(folder);var host=host(config);when(host.ready()).thenReturn(true);var bot=mock(Bot.class);when(host.bot()).thenReturn(bot);when(bot.usable()).thenReturn(true);
        try(var relay=new dev.dydiscord.console.ConsoleBridge(host)){relay.buffer().offer("Actual server line");var flush=relay.getClass().getDeclaredMethod("flush");flush.setAccessible(true);flush.invoke(relay);verify(host,never()).submit(any());}
    }
    @Test void adminHelpIsPaginatedAfterPermissionsAreFiltered()throws Exception{
        var config=TestSupport.config(folder);var host=host(config);when(host.ready()).thenReturn(true);when(host.language(any())).thenReturn("tr");
        var sender=mock(CommandSender.class);when(sender.hasPermission(anyString())).thenReturn(true);var router=new Router(host,false);
        router.onCommand(sender,mock(Command.class),"dydiscord",new String[]{"admin","yardım"});
        var lines=ArgumentCaptor.forClass(String.class);verify(sender,atLeastOnce()).sendMessage(lines.capture());assertTrue(lines.getAllValues().size()<=10,"First page must not dump all admin commands");
        reset(sender);when(sender.hasPermission(anyString())).thenReturn(true);
        router.onCommand(sender,mock(Command.class),"dydiscord",new String[]{"admin","yardım","2"});
        verify(sender,atLeastOnce()).sendMessage(anyString());verify(host,never()).message(eq(sender),eq("usage"),any());
    }
    @Test void helpPageNeverBypassesExplicitLeafDenial()throws Exception{
        var config=TestSupport.config(folder);var host=host(config);when(host.ready()).thenReturn(true);when(host.language(any())).thenReturn("en");
        var sender=mock(CommandSender.class);when(sender.hasPermission("dydiscord.admin.reload")).thenReturn(true);
        new Router(host,false).onCommand(sender,mock(Command.class),"dydiscord",new String[]{"admin","help","1"});
        var lines=ArgumentCaptor.forClass(String.class);verify(sender,atLeastOnce()).sendMessage(lines.capture());
        assertTrue(lines.getAllValues().stream().anyMatch(s->s.contains("admin reload")));assertFalse(lines.getAllValues().stream().anyMatch(s->s.contains("console")));
    }
    @Test void discordMessageHelpUsesStructuredEmbedWithoutReplyMentions()throws Exception{
        var config=TestSupport.config(folder);config.change("bot.yml","guild-id","123456789012345678");var host=host(config);var bot=spy(new Bot(host));
        doReturn(true).when(bot).alive(0);doReturn(true).when(bot).usable();field(bot,Bot.class,"applied",config.active().settings());field(bot,Bot.class,"content",true);
        var type=Class.forName("dev.dydiscord.discord.Bot$Events");var constructor=type.getDeclaredConstructor(Bot.class,long.class);constructor.setAccessible(true);var listener=(net.dv8tion.jda.api.hooks.ListenerAdapter)constructor.newInstance(bot,0L);
        var event=mock(net.dv8tion.jda.api.events.message.MessageReceivedEvent.class,RETURNS_DEEP_STUBS);
        when(event.isFromGuild()).thenReturn(true);when(event.getGuild().getId()).thenReturn(config.active().settings().guild());when(event.getAuthor().getId()).thenReturn("423456789012345678");when(event.getChannel().getId()).thenReturn("523456789012345678");when(event.getMessage().getContentRaw()).thenReturn("dy!help");
        var action=mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction.class,RETURNS_SELF);when(event.getChannel().sendMessageEmbeds(anyCollection())).thenReturn(action);
        listener.onMessageReceived(event);
        @SuppressWarnings("unchecked") var cards=ArgumentCaptor.forClass(Collection.class);verify(event.getChannel()).sendMessageEmbeds(cards.capture());
        var card=(net.dv8tion.jda.api.entities.MessageEmbed)cards.getValue().iterator().next();assertNotNull(card.getTitle());assertTrue(card.getFields().size()>=2);assertNull(card.getFooter());
        verify(action).setAllowedMentions(List.of());verify(action).mentionRepliedUser(false);verify(event.getChannel(),never()).sendMessage(anyString());
    }
}
