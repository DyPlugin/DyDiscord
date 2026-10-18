package dev.dydiscord;

import dev.dydiscord.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Release102ConfigurationTest {
    @TempDir Path folder;

    @Test void freshAndMissingDefaultsEnableChatBehindAnEmptyChannelAndKeepConsoleRelayOff() throws Exception {
        Settings fresh=TestSupport.config(folder).active().settings();
        for(Settings settings:List.of(fresh,Settings.read(Map.of(),Map.of(),null))){
            assertFalse(settings.consoleEnabled());
            assertTrue(settings.commandsEnabled());
            assertTrue(settings.panelEnabled());
            assertEquals("",settings.chat().channel());
            assertTrue(settings.chat().toDiscord());
            assertTrue(settings.chat().toMinecraft());
            assertTrue(settings.chat().join());assertTrue(settings.chat().quit());assertTrue(settings.chat().death());
        }
    }

    @Test void blankStringListExamplesAreEmptyWithoutWeakeningNonblankValidation() throws Exception {
        var bot=Configuration.parse("""
            console:
              commands:
                allowed-role-ids: [""]
            roles:
              linked-role-ids: [""]
            boost-rewards:
              commands: [""]
              remove-commands: [""]
            link-rewards:
              commands: [""]
            community:
              information-channel-ids: [""]
            giveaways:
              allowed-role-ids: [""]
            """);
        Settings settings=Settings.read(Map.of(),bot,null);
        assertTrue(settings.roles().isEmpty());assertTrue(settings.roleSync().linkedRoles().isEmpty());
        assertTrue(settings.boostRewards().commands().isEmpty());assertTrue(settings.boostRewards().removeCommands().isEmpty());
        assertTrue(settings.linkRewards().commands().isEmpty());assertTrue(settings.community().informationChannels().isEmpty());
        assertTrue(settings.giveaways().allowedRoles().isEmpty());
        assertThrows(IllegalArgumentException.class,()->Settings.read(Map.of(),Map.of("roles",Map.of("linked-role-ids",List.of("invalid"))),null));
        assertThrows(IllegalArgumentException.class,()->BoostSettings.read(Map.of("boost-rewards",Map.of("commands",List.of("op {player}")))));
    }

    @Test void emptyExamplesDoNotDropRealIdsOrExecutableCommands() {
        var settings=Settings.read(Map.of(),Map.of("roles",Map.of("linked-role-ids",List.of("","123456789012345678")),
            "boost-rewards",Map.of("commands",List.of("","give {player} diamond 1",""))),null);
        assertEquals(Set.of("123456789012345678"),settings.roleSync().linkedRoles());
        assertEquals(List.of("give {player} diamond 1"),settings.boostRewards().commands());
    }

    @Test void migrationPreservesExplicitOffFlagsOwnerCommentsAndIsIdempotent() throws Exception {
        var config=TestSupport.config(folder);
        Files.writeString(folder.resolve("bot.yml"),"""
            # Owner configuration
            enabled: false
            panel:
              enabled: false
            console:
              enabled: true
              commands:
                enabled: false
            chat:
              minecraft-to-discord: false
              discord-to-minecraft: false
              events:
                join:
                  enabled: false
                quit:
                  enabled: false
                death:
                  enabled: false
            """);
        Settings settings=config.load().settings();
        assertFalse(settings.botEnabled());assertFalse(settings.panelEnabled());
        assertTrue(settings.consoleEnabled());assertFalse(settings.commandsEnabled());
        assertFalse(settings.chat().toDiscord());assertFalse(settings.chat().toMinecraft());
        assertFalse(settings.chat().join());assertFalse(settings.chat().quit());assertFalse(settings.chat().death());
        String first=Files.readString(folder.resolve("bot.yml"));assertTrue(first.contains("Owner configuration"));
        config.load();assertEquals(first,Files.readString(folder.resolve("bot.yml")));
    }
}
