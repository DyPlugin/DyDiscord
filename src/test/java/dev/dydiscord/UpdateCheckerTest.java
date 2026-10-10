package dev.dydiscord;

import dev.dydiscord.config.Configuration;
import dev.dydiscord.update.*;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;

class UpdateCheckerTest {
    @TempDir Path folder;
    DyDiscord plugin;UpdateChecker.Source source;UpdateChecker checker;Player player;Configuration config;
    @BeforeEach void setup()throws Exception{
        config=TestSupport.config(folder);plugin=mock(DyDiscord.class);when(plugin.settings()).thenAnswer(c->config.active().settings());when(plugin.ready()).thenReturn(true);when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("update-tests"));Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);
        player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isOnline()).thenReturn(true);when(player.hasPermission("dydiscord.admin.update")).thenReturn(true);doReturn(List.of(player)).when(server).getOnlinePlayers();doAnswer(c->{((Runnable)c.getArgument(0)).run();return null;}).when(plugin).onMain(any());
        source=mock(UpdateChecker.Source.class);when(source.fetch(anyString(),anyString(),anyString())).thenReturn(new Modrinth.Response(200,"[{\"id\":\"stable\",\"version_number\":\"1.0.0\",\"version_type\":\"release\",\"status\":\"listed\",\"game_versions\":[\"26.3\"],\"loaders\":[\"paper\"]}]",900));checker=new UpdateChecker(plugin,source,"1.0.0-BETA","26.3","Paper");
    }
    @AfterEach void close(){checker.close();}
    @Test void compatibleReleaseNotifiesOnceAndExplicitLeafDenyIsRespected()throws Exception{
        checker.check(checker.generation());checker.notify(player);verify(plugin,times(1)).message(eq(player),eq("update-available"),any());clearInvocations(plugin);checker.leave(player.getUniqueId());when(player.hasPermission("dydiscord.admin.update")).thenReturn(false);when(player.isOp()).thenReturn(true);checker.notify(player);verify(plugin,never()).message(any(),anyString(),any());
    }
    @Test void disabledCheckerNeverCallsNetworkOrSendsNotification()throws Exception{config.change("config.yml","updates.enabled",false);checker.refresh();checker.check(checker.generation());verifyNoInteractions(source);verify(plugin,never()).message(any(),anyString(),any());}
    @Test void missingProjectDoesNotReportAnUpdate()throws Exception{when(source.fetch(anyString(),anyString(),anyString())).thenReturn(new Modrinth.Response(404,"",900));checker.check(checker.generation());checker.notify(player);verify(plugin,never()).message(any(),anyString(),any());}
    @Test void staleResponseAfterReloadCannotNotify()throws Exception{when(source.fetch(anyString(),anyString(),anyString())).thenAnswer(c->{checker.refresh();return new Modrinth.Response(200,"[]",900);});checker.check(checker.generation());verify(plugin,never()).onMain(any());verify(plugin,never()).message(any(),anyString(),any());}
    @Test void reloadDuringVersionParsingCannotRestoreStaleJoinNotification()throws Exception{
        try(var parser=mockStatic(Modrinth.class,CALLS_REAL_METHODS)){
            parser.when(()->Modrinth.newer(anyString(),anyString(),anyString(),anySet())).thenAnswer(call->{Object release=call.callRealMethod();checker.refresh();return release;});
            checker.check(checker.generation());checker.notify(player);verify(plugin,never()).message(any(),anyString(),any());
        }
    }
    @Test void closeCancelsSourceAndPreventsFurtherChecks()throws Exception{checker.close();checker.check(checker.generation());checker.notify(player);verify(source).close();verify(source,never()).fetch(anyString(),anyString(),anyString());verify(plugin,never()).message(any(),anyString(),any());}
}
