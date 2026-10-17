package dev.dydiscord;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;

class AdminWarningsTest {
    @TempDir Path folder;
    @Test void warningsAreWhitelistedThrottledAndPermissionFiltered()throws Exception{
        var plugin=mock(DyDiscord.class);var clock=new TestSupport.Time();when(plugin.clock()).thenReturn(clock);when(plugin.ready()).thenReturn(true);
        var config=TestSupport.config(folder);when(plugin.configuration()).thenReturn(config);when(plugin.messages()).thenReturn(config.active().messages());when(plugin.language(any())).thenReturn("en");var logger=mock(java.util.logging.Logger.class);when(plugin.getLogger()).thenReturn(logger);
        var server=mock(org.bukkit.Server.class);when(plugin.getServer()).thenReturn(server);var console=mock(org.bukkit.command.ConsoleCommandSender.class);when(server.getConsoleSender()).thenReturn(console);
        var admin=mock(Player.class);var ordinary=mock(Player.class);when(admin.isOnline()).thenReturn(true);when(ordinary.isOnline()).thenReturn(true);when(admin.hasPermission("dydiscord.admin.alerts")).thenReturn(true);doReturn(List.of(admin,ordinary)).when(server).getOnlinePlayers();
        doAnswer(c->{((Runnable)c.getArgument(0)).run();return null;}).when(plugin).onMain(any());
        var warnings=new AdminWarnings(plugin);warnings.warn("setup-channel-permissions");warnings.warn("setup-channel-permissions");warnings.warn("untrusted-secret-value");
        verify(plugin).message(eq(admin),eq("setup-channel-permissions"),eq(Map.of()));verify(logger).warning(contains("Allow the bot"));verify(plugin,never()).message(eq(ordinary),anyString(),any());verify(plugin,never()).message(any(),eq("untrusted-secret-value"),any());
        clock.advance(60);warnings.warn("setup-channel-permissions");verify(plugin,times(2)).message(eq(admin),eq("setup-channel-permissions"),eq(Map.of()));
        warnings.close();warnings.warn("setup-channel-missing");verify(plugin,never()).message(any(),eq("setup-channel-missing"),any());
    }
}
