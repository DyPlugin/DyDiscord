package dev.dydiscord;

import dev.dydiscord.discord.ServerInfo;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityPlayersTest {
    @Test void snapshotNeverReadsHiddenNamesAndKeepsAnImmutableBoundedPublicList()throws Exception {
        DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(server.isPrimaryThread()).thenReturn(true);
        Player shown=mock(Player.class),hidden=mock(Player.class);when(shown.getUniqueId()).thenReturn(UUID.randomUUID());when(hidden.getUniqueId()).thenReturn(UUID.randomUUID());when(shown.canSee(hidden)).thenReturn(false);when(hidden.canSee(shown)).thenReturn(true);when(shown.getName()).thenReturn("Alice");
        doReturn(List.of(shown,hidden)).when(server).getOnlinePlayers();
        Object result=ServerInfo.class.getMethod("capturePlayers",DyDiscord.class).invoke(null,plugin);
        List<?> names=(List<?>)result.getClass().getMethod("names").invoke(result);assertEquals(List.of("Alice"),names);verify(hidden,never()).getName();assertThrows(UnsupportedOperationException.class,names::clear);
    }
    @Test void publicPlayerSnapshotRequiresMainThread()throws Exception {
        DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);
        var method=ServerInfo.class.getMethod("capturePlayers",DyDiscord.class);
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,plugin));assertInstanceOf(IllegalStateException.class,failure.getCause());verify(server,never()).getOnlinePlayers();
    }
}
