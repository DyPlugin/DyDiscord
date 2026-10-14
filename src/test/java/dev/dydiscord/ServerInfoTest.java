package dev.dydiscord;

import dev.dydiscord.chat.ChatBridge;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.discord.ServerInfo;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerInfoTest {
    @Test void reloadRejectsOversizedServerFactsAndCombinedHelpBeforePublication()throws Exception{
        Configuration config=TestSupport.config(folder);
        for(String key:List.of("server-info-title","server-info-players","server-info-version")){
            String original=Files.readString(folder.resolve("discord/messages_en.yml"));String value="a".repeat(257)+(key.equals("server-info-title")?"{server_name}":"");
            Files.writeString(folder.resolve("discord/messages_en.yml"),original.replaceAll("(?m)^"+key+":.*$",key+": \""+value+"\""));assertThrows(Exception.class,config::load);Files.writeString(folder.resolve("discord/messages_en.yml"),original);
        }
        String original=Files.readString(folder.resolve("discord/messages_en.yml"));Files.writeString(folder.resolve("discord/messages_en.yml"),original.replaceAll("(?m)^help:.*$","help: \""+"a".repeat(1600)+"\"").replaceAll("(?m)^help-server:.*$","help-server: \""+"b".repeat(500)+"\""));assertThrows(Exception.class,config::load);Files.writeString(folder.resolve("discord/messages_en.yml"),original);
        var previous=config.active();Files.writeString(folder.resolve("discord/messages_en.yml"),original.replaceAll("(?m)^server-info-title:.*$","server-info-title: \""+"a".repeat(250)+"{server_name}\""));assertThrows(Exception.class,config::load);assertSame(previous,config.active());
    }
    @TempDir Path folder;

    @Test void localizedEmbedHasOnlyPublicCountsAndVersionAndEscapesExternalText()throws Exception{
        var messages=TestSupport.config(folder).active().messages();
        var snapshot=new ServerInfo.Snapshot("**@everyone** <@123>","1.20.4",2,50);
        var english=ServerInfo.render(messages,"en",snapshot);
        assertEquals("\\*\\*@\u200beveryone\\*\\* ‹@\u200b123>",english.getTitle());
        assertEquals(0x5865F2,english.getColorRaw());
        assertEquals(List.of("Players","Minecraft version"),english.getFields().stream().map(f->f.getName()).toList());
        assertEquals(List.of("2/50","1.20.4"),english.getFields().stream().map(f->f.getValue()).toList());
        assertNull(english.getFooter());assertNull(english.getTimestamp());assertNull(english.getUrl());assertNull(english.getDescription());
        var turkish=ServerInfo.render(messages,"tr",snapshot);
        assertEquals(List.of("Oyuncular","Minecraft sürümü"),turkish.getFields().stream().map(f->f.getName()).toList());
        assertEquals(english.getTitle(),turkish.getTitle());
        var escapedVersion=ServerInfo.render(messages,"en",new ServerInfo.Snapshot("Server","**@here**",0,50));
        assertEquals("\\*\\*@\u200bhere\\*\\*",escapedVersion.getFields().get(1).getValue());
    }

    @Test void addedDiscordKeysHaveMatchingLocalesAndPlaceholders()throws Exception{
        Map<String,Object> en,tr;
        try(var source=getClass().getResourceAsStream("/discord/messages_en.yml")){en=Configuration.parse(new String(source.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        try(var source=getClass().getResourceAsStream("/discord/messages_tr.yml")){tr=Configuration.parse(new String(source.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        for(String key:List.of("server-info-title","server-info-players","server-info-version","slash-server","help-server")){
            assertInstanceOf(String.class,en.get(key));assertInstanceOf(String.class,tr.get(key));
            assertEquals(dev.dydiscord.language.Messages.placeholders((String)en.get(key)),dev.dydiscord.language.Messages.placeholders((String)tr.get(key)));
        }
    }

    @Test void captureUsesActualMinecraftVersionAndExistingVisibilityPolicy()throws Exception{
        var plugin=host();var server=plugin.getServer();Player visible=player(),hidden=player();
        doReturn(List.of(visible,hidden)).when(server).getOnlinePlayers();
        var bridge=mock(ChatBridge.class);when(plugin.chat()).thenReturn(bridge);when(bridge.visible(visible)).thenReturn(true);
        var snapshot=ServerInfo.capture(plugin);
        assertEquals(plugin.settings().serverName(),snapshot.serverName());assertEquals("1.20.4",snapshot.version());assertEquals(1,snapshot.online());assertEquals(100,snapshot.maximum());
        verify(bridge).visible(visible);verify(bridge).visible(hidden);verify(visible,never()).getName();verify(hidden,never()).getName();
    }

    @Test void fallbackExcludesAllHiddenMetadataAndPlayersInvisibleToAnyViewer()throws Exception{
        var plugin=host();var server=plugin.getServer();Player visible=player(),vanished=player(),hidden=player(),privatePlayer=player(),unseen=player();
        var flag=mock(MetadataValue.class);when(flag.asBoolean()).thenReturn(true);
        when(vanished.getMetadata("vanished")).thenReturn(List.of(flag));when(hidden.getMetadata("hidden")).thenReturn(List.of(flag));when(privatePlayer.getMetadata("dydiscord-hidden")).thenReturn(List.of(flag));when(visible.canSee(unseen)).thenReturn(false);
        doReturn(List.of(visible,vanished,hidden,privatePlayer,unseen)).when(server).getOnlinePlayers();
        assertEquals(1,ServerInfo.capture(plugin).online());
    }

    @Test void captureRejectsBackgroundThreadBeforeReadingPlayers()throws Exception{
        var plugin=host();when(plugin.getServer().isPrimaryThread()).thenReturn(false);
        assertThrows(IllegalStateException.class,()->ServerInfo.capture(plugin));verify(plugin.getServer(),never()).getOnlinePlayers();
    }

    @Test void emptyServerHasZeroPlayersAndDoesNotInventVersionDetails()throws Exception{
        var plugin=host();var server=plugin.getServer();doReturn(List.of()).when(server).getOnlinePlayers();when(server.getBukkitVersion()).thenReturn("26.3");
        var snapshot=ServerInfo.capture(plugin);assertEquals(0,snapshot.online());assertEquals("26.3",snapshot.version());
    }

    private DyDiscord host()throws Exception{
        var plugin=mock(DyDiscord.class);var server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(plugin.settings()).thenReturn(TestSupport.settings());when(server.isPrimaryThread()).thenReturn(true);when(server.getBukkitVersion()).thenReturn("1.20.4-R0.1-SNAPSHOT");when(server.getMaxPlayers()).thenReturn(100);return plugin;
    }
    private Player player(){var player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.getMetadata(anyString())).thenReturn(List.of());when(player.canSee(any(Player.class))).thenReturn(true);return player;}
}
