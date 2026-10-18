package dev.dydiscord;

import dev.dydiscord.command.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommandSurfaceSimplificationTest {
    private Optional<Definition> find(String command){return Definition.ALL.stream().filter(d->d.match(command.split(" "))).findFirst();}
    @Test void chatUsesOnePersonalToggleAndExplicitAdministratorDirections(){
        assertEquals("chat-inbound",find("chat off").orElseThrow().key());
        assertEquals("chat-to-minecraft",find("admin chat discord-minecraft on").orElseThrow().key());
        assertEquals("chat-to-discord",find("admin chat minecraft-discord off").orElseThrow().key());
        assertEquals("chat-both",find("admin chat both on").orElseThrow().key());
        assertEquals("chat-channel",find("admin chat channel 123456789012345678").orElseThrow().key());
        assertEquals(List.of("on","off"),Definition.tabs(new String[]{"chat",""},"en",true,p->p.equals("dydiscord.chat.preferences")));
    }
    @Test void removedFeaturesDoNotMatchEvenWithFullPermissions(){
        for(String command:List.of("chat inbound on","chat outbound off","chat notify on","admin chat pause both","admin chat resume inbound","admin announce preview test","admin announce send test","admin announce set test value","admin announcements pause 5","admin announcements resume","admin maintenance","admin restartnotice","admin report","admin config validate","admin translations validate","admin setup","admin rewards preview uuid","admin console role add 123","admin console role remove 123","admin console role list","admin console administrator on","admin roles linked list","admin roles linked add 123","admin roles groups list","admin roles groups add vip 123 10")) assertTrue(find(command).isEmpty(),command);
    }
    @Test void setupChangesHaveDedicatedPermissionsAndTokenEntryIsRemoved(){
        Definition prefix=find("admin prefix &7[&9Test&7] custom text").orElseThrow();assertEquals("prefix",prefix.key());assertEquals("dydiscord.admin.prefix",prefix.permission());assertTrue(prefix.remainder());
        Definition guild=find("admin bot guild 123456789012345678").orElseThrow();assertEquals("bot-guild",guild.key());assertEquals("dydiscord.admin.bot.guild",guild.permission());
        assertTrue(find("admin bot token").isEmpty());assertTrue(find("admin bot token secret").isEmpty());
    }

    @Test void administratorShortcutUsesIdenticalLeavesAndDoesNotBypassExplicitDenial(){
        DyDiscord plugin=org.mockito.Mockito.mock(DyDiscord.class);org.mockito.Mockito.when(plugin.ready()).thenReturn(true);
        org.bukkit.entity.Player sender=org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        Router shortcut=new Router(plugin,false,true);var command=org.mockito.Mockito.mock(org.bukkit.command.Command.class);
        shortcut.onCommand(sender,command,"dyda",new String[]{"bot","guild","123456789012345678"});
        org.mockito.Mockito.verify(plugin).message(org.mockito.Mockito.eq(sender),org.mockito.Mockito.eq("no-permission"),org.mockito.Mockito.any());
        org.mockito.Mockito.verify(plugin,org.mockito.Mockito.never()).execute(org.mockito.Mockito.any(),org.mockito.Mockito.any(),org.mockito.Mockito.any());
        org.mockito.Mockito.when(sender.hasPermission("dydiscord.admin.bot.guild")).thenReturn(true);
        shortcut.onCommand(sender,command,"dyda",new String[]{"bot","guild","123456789012345678"});
        org.mockito.Mockito.verify(plugin).execute(org.mockito.Mockito.eq(sender),org.mockito.Mockito.eq(find("admin bot guild 123456789012345678").orElseThrow()),org.mockito.Mockito.eq(new String[]{"123456789012345678"}));
        org.mockito.Mockito.when(plugin.language(sender)).thenReturn("en");
        assertEquals(List.of("guild"),shortcut.onTabComplete(sender,command,"dyda",new String[]{"bot",""}));
    }
    @Test void removedTokenCommandIsUnavailableToConsoleAndNeverSuggested(){
        DyDiscord plugin=org.mockito.Mockito.mock(DyDiscord.class);org.mockito.Mockito.when(plugin.ready()).thenReturn(true);
        var sender=org.mockito.Mockito.mock(org.bukkit.command.ConsoleCommandSender.class);org.mockito.Mockito.when(sender.hasPermission("dydiscord.admin.bot.token")).thenReturn(true);
        new Router(plugin,false,true).onCommand(sender,org.mockito.Mockito.mock(org.bukkit.command.Command.class),"dyda",new String[]{"bot","token"});
        org.mockito.Mockito.verify(plugin).message(org.mockito.Mockito.eq(sender),org.mockito.Mockito.eq("usage"),org.mockito.Mockito.any());
        org.mockito.Mockito.verify(plugin,org.mockito.Mockito.never()).execute(org.mockito.Mockito.any(),org.mockito.Mockito.any(),org.mockito.Mockito.any());
        assertTrue(Definition.tabs(new String[]{"admin","bot","token",""},"en",true,p->true).isEmpty());
    }

    @Test void descriptorDeclaresShortcutAndEveryCurrentLeafWithoutRemovedPermissions()throws Exception{
        Map<String,Object> descriptor;
        try(var input=getClass().getResourceAsStream("/plugin.yml")){descriptor=dev.dydiscord.config.Configuration.parse(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        Map<?,?> commands=(Map<?,?>)descriptor.get("commands"),permissions=(Map<?,?>)descriptor.get("permissions");
        assertTrue(commands.containsKey("dyda"));assertTrue(((List<?>)((Map<?,?>)commands.get("dydiscord")).get("aliases")).contains("dyd"));assertFalse(permissions.containsKey("dydiscord.admin.bot.token"));assertFalse(((Map<?,?>)((Map<?,?>)permissions.get("dydiscord.admin")).get("children")).containsKey("dydiscord.admin.bot.token"));
        for(Definition definition:Definition.ALL)assertTrue(permissions.containsKey(definition.permission()),definition.permission());
        assertTrue(permissions.containsKey("dydiscord.admin.alerts"));
        for(Object permission:permissions.keySet())assertFalse(permission.toString().contains("announce")||permission.toString().contains(".roles.linked.")||permission.toString().contains(".roles.groups.")||permission.toString().equals("dydiscord.admin.console.roles")||permission.toString().equals("dydiscord.admin.console.administrator"),permission.toString());
    }
}
