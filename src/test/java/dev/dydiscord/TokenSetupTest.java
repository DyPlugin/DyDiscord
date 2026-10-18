package dev.dydiscord;

import dev.dydiscord.command.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TokenSetupTest {
    @Test void removedTokenCommandNeverExecutesForAnyRootOrArgumentShape(){
        for(String root:List.of("discord","dydiscord","dyd","dyda"))for(boolean value:List.of(false,true)){
            DyDiscord plugin=mock(DyDiscord.class);when(plugin.ready()).thenReturn(true);
            Player player=mock(Player.class);when(player.hasPermission(anyString())).thenReturn(true);
            List<String> args=new ArrayList<>();if(!root.equals("dyda"))args.add("admin");args.addAll(List.of("bot","token"));if(value)args.add("synthetic_"+"a".repeat(40));
            new Router(plugin,false,root.equals("dyda")).onCommand(player,mock(Command.class),root,args.toArray(String[]::new));
            verify(plugin,never()).execute(any(),any(),any());verify(plugin).message(eq(player),eq("usage"),any());
        }
    }
    @Test void fullPermissionsDoNotSuggestToken(){
        assertFalse(Definition.tabs(new String[]{"admin","bot",""},"en",true,p->true).contains("token"));
        assertFalse(Definition.tabs(new String[]{"admin","bot",""},"tr",true,p->true).contains("token"));
        assertTrue(Definition.tabs(new String[]{"admin","bot","token",""},"en",true,p->true).isEmpty());
    }
}
