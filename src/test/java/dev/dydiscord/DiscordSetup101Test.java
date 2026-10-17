package dev.dydiscord;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dydiscord.config.*;
import dev.dydiscord.console.*;
import dev.dydiscord.discord.*;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscordSetup101Test {
    @TempDir Path folder;
    Configuration config;DyDiscord host;Bot bot;JDA jda;DiscordAccess access;Member self;
    final String guild="123456789012345678",channel="223456789012345678",botId="323456789012345678",botRole="423456789012345678";
    @BeforeEach void setup()throws Exception{
        config=TestSupport.config(folder);config.change("bot.yml","guild-id",guild);host=mock(DyDiscord.class);when(host.settings()).thenAnswer(c->config.active().settings());when(host.messages()).thenAnswer(c->config.active().messages());when(host.ready()).thenReturn(true);when(host.clock()).thenReturn(new TestSupport.Time());when(host.getLogger()).thenReturn(mock(java.util.logging.Logger.class));
        var store=mock(Store.class);when(store.panel()).thenReturn(Optional.empty());when(host.store()).thenReturn(store);bot=spy(new Bot(host));
        jda=mock(JDA.class,RETURNS_DEEP_STUBS);when(jda.getSelfUser().getId()).thenReturn(botId);Release101RegressionTest.field(bot,Bot.class,"jda",jda);Release101RegressionTest.field(bot,Bot.class,"applied",config.active().settings());
        access=mock(DiscordAccess.class);Release101RegressionTest.field(bot,Bot.class,"access",access);self=mock(Member.class);when(self.getId()).thenReturn(botId);var role=mock(Role.class);when(role.getId()).thenReturn(botRole);when(self.getRoles()).thenReturn(List.of(role));
        doReturn(mock(TextChannel.class,RETURNS_DEEP_STUBS)).when(bot).channel(channel);doReturn(self).when(bot).await(any());
    }
    void permissionFixture(boolean publicChannel,long botPermissions)throws Exception{
        var json=new ObjectMapper();String everyonePermission=Long.toString(publicChannel?Permission.VIEW_CHANNEL.getRawValue():0);
        var roles=json.readTree("[{\"id\":\""+guild+"\",\"permissions\":\""+everyonePermission+"\"},{\"id\":\""+botRole+"\",\"managed\":true,\"permissions\":\""+botPermissions+"\"}]");
        when(access.channel(any(),eq(channel))).thenReturn(new DiscordAccess.Channel(json.readTree("{\"permission_overwrites\":[]}"),roles,guild));
    }
    @Test void consoleOutputDoesNotRequireUnrelatedHistoryOrEmbedPermissions()throws Exception{
        permissionFixture(false,Permission.getRaw(Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND));assertDoesNotThrow(()->bot.validateChannel(channel,true));
        assertEquals("setup-channel-permissions",assertThrows(SetupProblem.class,()->bot.validateChannel(channel,false)).key());
    }
    @Test void publicConsoleAllowsLogRelayWhileCommandsStillRequireAuthority()throws Exception{
        permissionFixture(true,Permission.getRaw(Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND));assertDoesNotThrow(()->bot.validateChannel(channel,true));
        assertFalse(ConsolePolicy.authorized(false,Set.of(),config.active().settings()));
    }
    @Test void missingBotSendPermissionDoesNotBecomeSuccess()throws Exception{
        permissionFixture(false,Permission.VIEW_CHANNEL.getRawValue());assertEquals("setup-channel-permissions",assertThrows(SetupProblem.class,()->bot.validateChannel(channel,true)).key());
    }
    @Test void setupDiagnosticsHaveEnglishAndTurkishTextWithoutExternalExceptionValues(){
        for(var code:SetupProblem.Code.values())for(String language:List.of("en","tr")){String key=new SetupProblem(code).key(),text=host.messages().get(false,language,key);assertNotEquals(key,text);assertFalse(text.contains("synthetic-token"));}
    }
    @Test void unavailableConfiguredGuildProducesAdministratorRepairWarning()throws Exception{
        when(jda.getGuildById(guild)).thenReturn(null);
        doAnswer(call->{try{return CompletableFuture.completedFuture(((Callable<?>)call.getArgument(0)).call());}catch(Exception failure){return CompletableFuture.failedFuture(failure);}}).when(host).submit(any());
        var type=Class.forName("dev.dydiscord.discord.Bot$Events");var constructor=type.getDeclaredConstructor(Bot.class,long.class);constructor.setAccessible(true);var listener=(net.dv8tion.jda.api.hooks.ListenerAdapter)constructor.newInstance(bot,0L);
        listener.onReady(mock(net.dv8tion.jda.api.events.session.ReadyEvent.class));
        verify(host).warn("setup-connection-failed");assertFalse(bot.usable());assertEquals(Bot.State.DEGRADED,bot.state());
    }
    @Test void deniedPrivilegedIntentsReconnectWithoutThemAndExplainUnavailableFeatures()throws Exception{
        var bridge=mock(ConsoleBridge.class);when(host.console()).thenReturn(bridge);when(bridge.buffer()).thenReturn(new LogBuffer());
        Release101RegressionTest.field(bot,Bot.class,"content",true);Release101RegressionTest.field(bot,Bot.class,"members",true);doAnswer(c->bot.generation()==0).when(bot).alive(0);
        doAnswer(call->{try{return CompletableFuture.completedFuture(((Callable<?>)call.getArgument(0)).call());}catch(Exception failure){return CompletableFuture.failedFuture(failure);}}).when(host).submit(any());
        var type=Class.forName("dev.dydiscord.discord.Bot$Events");var constructor=type.getDeclaredConstructor(Bot.class,long.class);constructor.setAccessible(true);var listener=(net.dv8tion.jda.api.hooks.ListenerAdapter)constructor.newInstance(bot,0L);
        var event=mock(net.dv8tion.jda.api.events.session.ShutdownEvent.class);when(event.getCode()).thenReturn(4014);
        var builder=mock(JDABuilder.class,RETURNS_SELF);when(builder.build()).thenReturn(mock(JDA.class));
        try(var builders=mockStatic(JDABuilder.class)){
            builders.when(()->JDABuilder.createLight(anyString(),anyCollection())).thenReturn(builder);listener.onShutdown(event);
            builders.verify(()->JDABuilder.createLight(anyString(),argThat((Collection<GatewayIntent> intents)->!intents.contains(GatewayIntent.MESSAGE_CONTENT)&&!intents.contains(GatewayIntent.GUILD_MEMBERS))));
        }
        assertFalse(bot.content());assertFalse(bot.roleMemberEvents());verify(host).warn("setup-intents-denied");verify(host,never()).error(any());
        assertEquals(1,bot.generation(),"Old denied-session callbacks must expire before fallback reconnect");
        listener.onSessionResume(mock(net.dv8tion.jda.api.events.session.SessionResumeEvent.class));assertEquals(Bot.State.STARTING,bot.state());
    }
}
