package dev.dydiscord;

import dev.dydiscord.command.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RootCommandVisibilityTest {
    private Set<String> suggestions(Player player)throws Exception{
        Set<String> commands=new LinkedHashSet<>(List.of("discord","dydiscord","dyd","dyda","dydiscord:dyda","other:dyda","other"));
        PlayerCommandSendEvent event=new PlayerCommandSendEvent(player,commands);
        Router router=new Router(mock(DyDiscord.class),false);
        router.commandSuggestions(event);
        return commands;
    }
    @Test void ordinaryPlayerDoesNotSeeAdminShortcutAndOtherPluginsStayVisible()throws Exception{
        assertEquals(Set.of("discord","dydiscord","dyd","other:dyda","other"),suggestions(mock(Player.class)));
    }
    @Test void operatorWithEveryAdminLeafDeniedDoesNotSeeShortcut()throws Exception{
        Player player=mock(Player.class);when(player.isOp()).thenReturn(true);when(player.hasPermission("dydiscord.admin")).thenReturn(true);
        assertFalse(suggestions(player).contains("dyda"));
    }
    @Test void isolatedLeafShowsShortcutWithoutAdminGroupAndRevocationHidesIt()throws Exception{
        Player player=mock(Player.class);when(player.hasPermission("dydiscord.admin.reload")).thenReturn(true);
        assertTrue(suggestions(player).containsAll(List.of("dyda","dydiscord:dyda")));
        when(player.hasPermission("dydiscord.admin.reload")).thenReturn(false);assertFalse(suggestions(player).contains("dyda"));
    }
    @Test void allRegularRootsFilterAdminArgumentsAndHonorLeafDenial(){
        for(String root:List.of("discord","dydiscord","dyd")){
            DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);when(plugin.language(any())).thenReturn("en");
            Player player=mock(Player.class);when(player.isOp()).thenReturn(true);when(player.hasPermission("dydiscord.admin")).thenReturn(true);Router router=new Router(plugin,false);Command command=mock(Command.class);
            assertFalse(router.onTabComplete(player,command,root,new String[]{""}).contains("admin"));assertTrue(router.onTabComplete(player,command,root,new String[]{"admin",""}).isEmpty());
            router.onCommand(player,command,root,new String[]{"admin","reload"});verify(plugin,never()).execute(any(),any(),any());
            when(player.hasPermission("dydiscord.admin.reload")).thenReturn(true);
            assertTrue(router.onTabComplete(player,command,root,new String[]{""}).contains("admin"));assertEquals(List.of("reload","help"),router.onTabComplete(player,command,root,new String[]{"admin",""}));
            router.onCommand(player,command,root,new String[]{"admin","reload"});verify(plugin).execute(eq(player),argThat(d->d.key().equals("reload")),eq(new String[0]));
        }
    }
}
