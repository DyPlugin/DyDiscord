package dev.dydiscord;
import dev.dydiscord.console.*;
import dev.dydiscord.config.*;
import dev.dydiscord.discord.*;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConsoleTest {
    @org.junit.jupiter.api.Test void inlineTokenAttemptsAreMaskedBeforeConfiguredTokenIsKnown(){
        var buffer=new dev.dydiscord.console.LogBuffer();String secret="synthetic_"+"q".repeat(40);
        for(String root:java.util.List.of("dyda","dydiscord admin","discord yönetim"))assertFalse(buffer.sanitize("issued command /"+root+" bot token "+secret).contains(secret));
    }

    @TempDir Path folder;
    @Test void fullAuditFileRejectsBeforeWritingAndCannotGrowOnRepeatedFailures()throws Exception{
        Path file=folder.resolve("audit-2026-10-07.log");long limit=20L*1024*1024;
        try(var channel=java.nio.channels.FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){channel.position(limit-1);channel.write(java.nio.ByteBuffer.wrap(new byte[]{0}));}
        Audit audit=new Audit(folder,new TestSupport.Time());for(int i=0;i<3;i++){assertThrows(java.io.IOException.class,()->audit.write("actor","console-accepted","message","result"));assertEquals(limit,Files.size(file));}
    }
    @Test void auditValuesCannotCreateUnicodeLinesOrDirectionSpoofing()throws Exception{
        new Audit(folder,new TestSupport.Time()).write("actor","action","target","reason\u2028fake-system\u202eoverride\u2029second");
        String text=Files.readString(folder.resolve("audit-2026-10-07.log"));assertFalse(text.contains("\u2028"));assertFalse(text.contains("\u202e"));assertFalse(text.contains("\u2029"));assertEquals(1,text.lines().count());
    }
    private final class DispatchFixture implements AutoCloseable {
        final TestSupport.Time clock=new TestSupport.Time();
        final DyDiscord plugin=mock(DyDiscord.class);final Bot bot=mock(Bot.class);
        final Server server=mock(Server.class);final ConsoleCommandSender sender=mock(ConsoleCommandSender.class);
        final MessageReceivedEvent event=mock(MessageReceivedEvent.class,RETURNS_DEEP_STUBS);
        final TextChannel channel=mock(TextChannel.class,RETURNS_DEEP_STUBS);final Member member=mock(Member.class);
        final String message="423456789012345678";final Store store;final ConsoleBridge bridge;final Audit audit;
        DispatchFixture(boolean dispatched)throws Exception {
            Configuration configuration=TestSupport.config(folder);
            configuration.change("bot.yml","guild-id","123456789012345678");
            configuration.change("bot.yml","console.channel-id","223456789012345678");
            configuration.change("bot.yml","console.enabled",true);configuration.change("bot.yml","console.commands.enabled",true);
            Settings settings=configuration.active().settings();store=new Store(folder.resolve("data.db"));audit=new Audit(folder.resolve("logs"),clock);
            when(plugin.settings()).thenReturn(settings);when(plugin.clock()).thenReturn(clock);when(plugin.bot()).thenReturn(bot);
            when(plugin.store()).thenReturn(store);when(plugin.audit()).thenReturn(audit);when(plugin.getServer()).thenReturn(server);
            when(server.getConsoleSender()).thenReturn(sender);when(server.dispatchCommand(sender,"list")).thenReturn(dispatched);
            when(bot.alive(1L)).thenReturn(true);when(bot.usable()).thenReturn(true);when(bot.content()).thenReturn(true);
            when(bot.channel(settings.consoleChannel())).thenReturn(channel);when(bot.await(any())).thenReturn(member);
            when(bot.consoleAuthority(member,settings)).thenReturn("administrator");
            when(event.getMessage().getType()).thenReturn(MessageType.DEFAULT);when(event.getMessage().getContentRaw()).thenReturn("list");
            when(event.getMessage().getAttachments()).thenReturn(List.of());when(event.getMessage().getTimeCreated()).thenReturn(clock.instant().atOffset(ZoneOffset.UTC));
            when(event.getGuild().getId()).thenReturn(settings.guild());when(event.getChannel().getId()).thenReturn(settings.consoleChannel());
            when(event.getChannel().getType()).thenReturn(ChannelType.TEXT);when(event.getAuthor().getId()).thenReturn("323456789012345678");when(event.getMessageId()).thenReturn(message);
            doAnswer(call->{CompletableFuture<Object> result=new CompletableFuture<>();try{result.complete(((Callable<?>)call.getArgument(0)).call());}catch(Throwable e){result.completeExceptionally(e);}return result;}).when(plugin).submit(any());
            doAnswer(call->((Callable<?>)call.getArgument(0)).call()).when(plugin).mainValue(any());bridge=new ConsoleBridge(plugin);
        }
        String receipt()throws Exception {try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+folder.resolve("data.db").toAbsolutePath());var statement=db.prepareStatement("SELECT result FROM console_receipts WHERE message=?")){statement.setString(1,message);try(var result=statement.executeQuery()){assertTrue(result.next());return result.getString(1);}}}
        String auditText()throws Exception {try(var files=Files.list(folder.resolve("logs"))){return Files.readString(files.findFirst().orElseThrow());}}
        @Override public void close()throws Exception {bridge.close();store.close();}
    }
    @Test void successfulDispatchKeepsDurableResultWithoutExtraDiscordAcknowledgment()throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(true)){
            fixture.bridge.receive(fixture.event,1L);
            verify(fixture.server).dispatchCommand(fixture.sender,"list");assertEquals("dispatched",fixture.receipt());
            assertTrue(fixture.auditText().contains("action=console-result"));assertTrue(fixture.auditText().contains("result=dispatched"));
            verify(fixture.event.getChannel(),never()).sendMessage(anyString());verify(fixture.bot,never()).text(anyString(),any());verify(fixture.plugin,never()).error(any());
        }
    }
    @Test void unknownCommandRecordsNotFoundWithoutExtraDiscordResponse()throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(false)){
            fixture.bridge.receive(fixture.event,1L);
            verify(fixture.server).dispatchCommand(fixture.sender,"list");assertEquals("not-found",fixture.receipt());
            assertTrue(fixture.auditText().contains("result=not-found"));verify(fixture.event.getChannel(),never()).sendMessage(anyString());
            verify(fixture.bot,never()).text(anyString(),any());verify(fixture.plugin,never()).error(any());
        }
    }
    @Test void deniedAuthorityDoesNotDispatchOrSendDiscordResponse()throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(true)){
            when(fixture.bot.consoleAuthority(fixture.member,fixture.plugin.settings())).thenReturn("");
            fixture.bridge.receive(fixture.event,1L);
            verify(fixture.server,never()).dispatchCommand(any(),anyString());verify(fixture.plugin).error(any(IllegalStateException.class));
            verify(fixture.event.getChannel(),never()).sendMessage(anyString());verify(fixture.bot,never()).text(anyString(),any());
        }
    }
    @Test void changedPolicyBeforeDispatchRemainsRejectedAndSilent()throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(true)){
            doAnswer(call->{when(fixture.bot.content()).thenReturn(false);return ((Callable<?>)call.getArgument(0)).call();}).when(fixture.plugin).mainValue(any());
            fixture.bridge.receive(fixture.event,1L);
            verify(fixture.server,never()).dispatchCommand(any(),anyString());assertEquals("rejected",fixture.receipt());assertTrue(fixture.auditText().contains("result=rejected"));
            verify(fixture.event.getChannel(),never()).sendMessage(anyString());verify(fixture.bot,never()).text(anyString(),any());verify(fixture.plugin,never()).error(any());
        }
    }
    @Test void executorFailureIsLoggedLocallyWithoutDiscordResponseOrReplay()throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(true)){
            when(fixture.server.dispatchCommand(fixture.sender,"list")).thenThrow(new org.bukkit.command.CommandException("private command detail"));
            fixture.bridge.receive(fixture.event,1L);fixture.clock.advance(2);fixture.bridge.receive(fixture.event,1L);
            verify(fixture.server,times(1)).dispatchCommand(fixture.sender,"list");assertEquals("accepted",fixture.receipt());verify(fixture.plugin).error(any(org.bukkit.command.CommandException.class));
            verify(fixture.event.getChannel(),never()).sendMessage(anyString());verify(fixture.bot,never()).text(anyString(),any());
        }
    }
    @ParameterizedTest @ValueSource(strings={"dy!help","say hi\nsay bad","//stop"})
    void botPrefixAndInvalidConsoleInputDoNotDispatchOrSendResponse(String text)throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(true)){
            when(fixture.event.getMessage().getContentRaw()).thenReturn(text);fixture.bridge.receive(fixture.event,1L);
            verify(fixture.server,never()).dispatchCommand(any(),anyString());verify(fixture.event.getChannel(),never()).sendMessage(anyString());
            verify(fixture.bot,never()).text(anyString(),any());verify(fixture.plugin,never()).submit(any());
        }
    }
    @Test void fullCommandQueueDoesNotDispatchOrSendResponse()throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(true)){
            var field=ConsoleBridge.class.getDeclaredField("pending");field.setAccessible(true);var pending=(java.util.concurrent.atomic.AtomicInteger)field.get(fixture.bridge);pending.set(50);
            fixture.bridge.receive(fixture.event,1L);assertEquals(50,pending.get());
            verify(fixture.server,never()).dispatchCommand(any(),anyString());verify(fixture.event.getChannel(),never()).sendMessage(anyString());verify(fixture.plugin,never()).submit(any());
        }
    }
    @Test void repeatedGatewayMessageNeitherDispatchesNorAcknowledgesAgain()throws Exception {
        try(DispatchFixture fixture=new DispatchFixture(true)){
            fixture.bridge.receive(fixture.event,1L);fixture.clock.advance(2);fixture.bridge.receive(fixture.event,1L);
            verify(fixture.server,times(1)).dispatchCommand(fixture.sender,"list");assertEquals("dispatched",fixture.receipt());
            verify(fixture.event.getChannel(),never()).sendMessage(anyString());verify(fixture.plugin,never()).error(any());
        }
    }
    private org.apache.logging.log4j.core.appender.AbstractAppender relay(ConsoleBridge bridge)throws Exception{
        bridge.attach();var field=ConsoleBridge.class.getDeclaredField("appender");field.setAccessible(true);
        var appender=(org.apache.logging.log4j.core.appender.AbstractAppender)field.get(bridge);assertNotNull(appender);assertTrue(bridge.attached());return appender;
    }
    private org.apache.logging.log4j.core.LogEvent log(org.apache.logging.log4j.Level level,String name,String thread,String text){
        return org.apache.logging.log4j.core.impl.Log4jLogEvent.newBuilder().setLevel(level).setLoggerName(name).setThreadName(thread).setMessage(new org.apache.logging.log4j.message.SimpleMessage(text)).build();
    }
    private DyDiscord relayHost()throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);Bot bot=mock(Bot.class);when(plugin.bot()).thenReturn(bot);when(plugin.ready()).thenReturn(true);
        Configuration c=TestSupport.config(folder);c.change("bot.yml","console.enabled",true);when(plugin.settings()).thenReturn(c.active().settings());return plugin;
    }
    @Test void infoCommandResultsAndServerLogsAreRelayedInOrder()throws Exception{
        DyDiscord plugin=relayHost();try(ConsoleBridge bridge=new ConsoleBridge(plugin)){
            var appender=relay(bridge);String logger="net.minecraft.server.MinecraftServer",thread="Server thread";
            appender.append(log(org.apache.logging.log4j.Level.INFO,logger,thread,"System chat: Nothing changed. The player is already an operator"));
            appender.append(log(org.apache.logging.log4j.Level.INFO,logger,thread,"System chat: Made Alice no longer a server operator"));
            appender.append(log(org.apache.logging.log4j.Level.WARN,logger,thread,"server warning"));
            appender.append(log(org.apache.logging.log4j.Level.ERROR,logger,thread,"server error"));
            appender.append(log(org.apache.logging.log4j.Level.DEBUG,logger,thread,"private debug output"));
            appender.append(log(org.apache.logging.log4j.Level.TRACE,logger,thread,"private trace output"));
            assertEquals(4,bridge.buffer().size());String output=bridge.buffer().drain();
            assertTrue(output.indexOf("[INFO] System chat: Nothing changed")<output.indexOf("[INFO] System chat: Made Alice"));
            assertTrue(output.contains("[WARN] server warning"));assertTrue(output.contains("[ERROR] server error"));assertFalse(output.contains("debug"));assertFalse(output.contains("trace"));
        }
    }
    @Test void relayStillExcludesItsOwnFailuresAndDiscordHttpLogs()throws Exception{
        DyDiscord plugin=relayHost();try(ConsoleBridge bridge=new ConsoleBridge(plugin)){
            var appender=relay(bridge);
            for(String logger:List.of("DyDiscord","dev.dydiscord.lib.jda.requests.Requester","net.dv8tion.jda.requests.Requester","okhttp3.OkHttpClient"))appender.append(log(org.apache.logging.log4j.Level.ERROR,logger,"Server thread","relay failure"));
            appender.append(log(org.apache.logging.log4j.Level.ERROR,"other.plugin","DyDiscord-io","relay failure"));
            assertEquals(0,bridge.buffer().size());
        }
    }
    @Test void closedOrInactiveRelayCannotCaptureFurtherLogs()throws Exception{
        DyDiscord plugin=relayHost();ConsoleBridge bridge=new ConsoleBridge(plugin);try{
            var appender=relay(bridge);var event=log(org.apache.logging.log4j.Level.INFO,"net.minecraft.server.MinecraftServer","Server thread","System chat: command result");
            when(plugin.ready()).thenReturn(false);appender.append(event);assertEquals(0,bridge.buffer().size());
            when(plugin.ready()).thenReturn(true);appender.append(event);assertEquals(1,bridge.buffer().size());
            bridge.close();appender.append(event);assertEquals(0,bridge.buffer().size());assertFalse(bridge.attached());
        }finally{bridge.close();}
    }
    @Test void idleRelayDoesNotQueueNetworkPermissionChecks()throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);Bot bot=mock(Bot.class);when(plugin.bot()).thenReturn(bot);when(plugin.ready()).thenReturn(true);when(bot.usable()).thenReturn(true);
        Configuration c=TestSupport.config(folder);c.change("bot.yml","console.enabled",true);c.change("bot.yml","console.channel-id","323456789012345678");when(plugin.settings()).thenReturn(c.active().settings());
        when(plugin.submit(any())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
        try(ConsoleBridge bridge=new ConsoleBridge(plugin)){var flush=ConsoleBridge.class.getDeclaredMethod("flush");flush.setAccessible(true);flush.invoke(bridge);verify(plugin,never()).submit(any());bridge.buffer().offer("test log");when(plugin.clock()).thenReturn(new TestSupport.Time());when(plugin.getLogger()).thenReturn(mock(java.util.logging.Logger.class));when(bot.alive(0)).thenReturn(true);var channel=mock(TextChannel.class);var send=mock(net.dv8tion.jda.api.requests.restaction.MessageCreateAction.class,RETURNS_SELF);when(bot.channel("323456789012345678")).thenReturn(channel);when(channel.sendMessage(any(net.dv8tion.jda.api.utils.messages.MessageCreateData.class))).thenReturn(send);flush.invoke(bridge);verify(channel).sendMessage(argThat(TestSupport.v2("test log")));verify(bot).validateChannel("323456789012345678",true);verify(plugin,never()).submit(any());}
    }
    @ParameterizedTest @ValueSource(strings={"say hi\nsay bad","say\rhi","dy!help","```say hello```","/","//stop","say\u0000hi","say\u202ehi",""," "})void malformedCommandsRejected(String input){assertThrows(IllegalArgumentException.class,()->ConsolePolicy.command(input));}
    @Test void optionalSlashAndArgumentsPreserved(){assertEquals("say Şçİ &a hello",ConsolePolicy.command("/say Şçİ &a hello"));}
    @Test void longCommandRejected(){assertThrows(IllegalArgumentException.class,()->ConsolePolicy.command("a".repeat(513)));}
    @Test void administratorAndRoleOrPolicy()throws Exception{Configuration c=TestSupport.config(folder);c.change("bot.yml","console.commands.allowed-role-ids",List.of("123456789012345678"));Settings s=c.active().settings();assertTrue(ConsolePolicy.authorized(true,Set.of(),s));assertTrue(ConsolePolicy.authorized(false,s.roles(),s));assertFalse(ConsolePolicy.authorized(false,Set.of("Admin"),s));c.change("bot.yml","console.commands.allow-administrator",false);assertFalse(ConsolePolicy.authorized(true,Set.of(),c.active().settings()));}
    @Test void wrongSourceVariantsFailClosed()throws Exception{Configuration c=TestSupport.config(folder);c.change("bot.yml","guild-id","123456789012345678");c.change("bot.yml","console.channel-id","223456789012345678");c.change("bot.yml","console.enabled",true);c.change("bot.yml","console.commands.enabled",true);Settings s=c.active().settings();ConsolePolicy.Source valid=new ConsolePolicy.Source(s.guild(),s.consoleChannel(),true,false,false,false,false);assertTrue(ConsolePolicy.source(valid,s));for(ConsolePolicy.Source bad:List.of(new ConsolePolicy.Source("wrong",s.consoleChannel(),true,false,false,false,false),new ConsolePolicy.Source(s.guild(),"wrong",true,false,false,false,false),new ConsolePolicy.Source(s.guild(),s.consoleChannel(),false,false,false,false,false),new ConsolePolicy.Source(s.guild(),s.consoleChannel(),true,true,false,false,false),new ConsolePolicy.Source(s.guild(),s.consoleChannel(),true,false,true,false,false),new ConsolePolicy.Source(s.guild(),s.consoleChannel(),true,false,false,true,false),new ConsolePolicy.Source(s.guild(),s.consoleChannel(),true,false,false,false,true)))assertFalse(ConsolePolicy.source(bad,s));}
    @Test void logRedactionMentionFenceAndUnicode(){LogBuffer logs=new LogBuffer();logs.token("test-secret-value");logs.offer("§cERROR \u001b[31m test-secret-value Authorization: Bot secret\nABCDEFGH ``` @everyone 😀");String text=logs.drain();assertFalse(text.contains("test-secret-value"));assertFalse(text.contains("Bot secret"));assertFalse(text.contains("ABCDEFGH"));assertFalse(text.contains("@everyone"));assertFalse(text.contains("§"));assertTrue(text.contains("😀"));assertTrue(text.length()<2000);}
    @Test void queuesBoundedAndDropsSummarized(){LogBuffer logs=new LogBuffer();for(int i=0;i<10000;i++)logs.offer("x".repeat(1400));assertTrue(logs.size()<=2000);String out=logs.drain();assertTrue(out.contains("lines dropped"));assertTrue(out.length()<2000);}
    @Test void splittingPreservesSurrogatePairs(){String text="😀".repeat(2000);assertEquals(text,String.join("",LogBuffer.split(text,1501)));for(String part:LogBuffer.split(text,1501))assertFalse(Character.isHighSurrogate(part.charAt(part.length()-1)));}
    @Test void currentRestPermissionsBlockPublicAndUnknownRoles()throws Exception{
        ObjectMapper json=new ObjectMapper();String guild="123456789012345678",outsider="223456789012345678";long view=net.dv8tion.jda.api.Permission.VIEW_CHANNEL.getRawValue();
        var roles=json.readTree("[{\"id\":\""+guild+"\",\"permissions\":\""+view+"\"},{\"id\":\""+outsider+"\",\"permissions\":\"0\"}]");
        var open=json.readTree("{\"permission_overwrites\":[]}");var closed=json.readTree("{\"permission_overwrites\":[{\"id\":\""+guild+"\",\"type\":0,\"deny\":\""+view+"\",\"allow\":\"0\"}]}");
        Settings s=TestSupport.settings();assertFalse(new DiscordAccess.Channel(open,roles,guild).privateTo(s,"bot",Set.of()));assertTrue(new DiscordAccess.Channel(closed,roles,guild).privateTo(s,"bot",Set.of()));
        var leak=json.readTree("{\"permission_overwrites\":[{\"id\":\""+guild+"\",\"type\":0,\"deny\":\""+view+"\",\"allow\":\"0\"},{\"id\":\""+outsider+"\",\"type\":0,\"allow\":\""+view+"\",\"deny\":\"0\"}]}");assertFalse(new DiscordAccess.Channel(leak,roles,guild).privateTo(s,"bot",Set.of()));
    }
    @Test void auditFileHasTimeAndNoCommandArguments()throws Exception{Audit audit=new Audit(folder,new TestSupport.Time());audit.write("actor","console","message","root=say args=omitted");try(var files=Files.list(folder)){String body=Files.readString(files.findFirst().orElseThrow());assertTrue(body.startsWith("2026-10-07T00:00:00Z"));assertTrue(body.contains("args=omitted"));}}
    @Test void currentGuildOwnerHasInherentPermissionsAndOldOwnerDoesNot()throws Exception{ObjectMapper json=new ObjectMapper();String guild="123456789012345678",owner="223456789012345678",former="323456789012345678";long view=net.dv8tion.jda.api.Permission.VIEW_CHANNEL.getRawValue();var role=json.readTree("[{\"id\":\""+guild+"\",\"permissions\":\"0\"}]");var channel=json.readTree("{\"permission_overwrites\":[{\"id\":\""+guild+"\",\"type\":0,\"allow\":\"0\",\"deny\":\""+view+"\"}]}");var current=new DiscordAccess.Channel(channel,role,guild,owner);assertEquals(-1L,current.permissions(owner,Set.of()));assertEquals(0,current.permissions(former,Set.of())&view);}
}
