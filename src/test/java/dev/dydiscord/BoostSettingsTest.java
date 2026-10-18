package dev.dydiscord;

import dev.dydiscord.config.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoostSettingsTest {
    private Settings read(Map<String,Object> boost) {
        return Settings.read(Map.of(),Map.of("boost-rewards",boost),null);
    }
    @Test void defaultsAreDisabledWithoutCommands() {
        var settings=read(Map.of()).boostRewards();
        assertFalse(settings.enabled()); assertEquals(List.of(),settings.commands());
        assertEquals(List.of(),settings.removeCommands());
        assertEquals(300,settings.recheckSeconds()); assertEquals(168,settings.cooldownHours());
    }
    @Test void rejectsCooldownBelowReceiptSafetyFloor(){assertThrows(IllegalArgumentException.class,()->read(Map.of("cooldown-hours",167)));}
    @Test void ownerCommandsRetainArgumentsAndNormalizeLeadingSlash() {
        var settings=read(Map.of("commands",List.of(" /economy give {player} 100 ","sitecredit add {uuid} 20"),
            "remove-commands",List.of("lp user {uuid} parent remove booster"))).boostRewards();
        assertEquals(List.of("economy give {player} 100","sitecredit add {uuid} 20"),settings.commands());
        assertEquals(List.of("lp user {uuid} parent remove booster"),settings.removeCommands());
        assertThrows(UnsupportedOperationException.class,()->settings.commands().clear());
        assertThrows(UnsupportedOperationException.class,()->settings.removeCommands().clear());
    }
    @Test void rejectsUnsafeUntargetedAndUnknownPlaceholderCommands() {
        for(String command:List.of(" ","money add {player} 10\nstop", "money\tadd {player} 10",
            "money add {player} 10\u0000", "money add {player} 10\u2028", "money add {discord} 10","money add {player} {amount}",
            "money add {player 10", "money add player} 10","money give Alice 10","{player} give 10", "//money give {player} 10")) {
            var error=assertThrows(IllegalArgumentException.class,()->read(Map.of("commands",List.of(command))),command);
            assertEquals("Invalid setting: boost-rewards.commands",error.getMessage());
        }
    }
    @Test void blocksAdministrativePrimitivesIncludingNamespacedCommands() {
        for(String command:List.of("op","deop","stop","restart","reload","kick","ban","ban-ip","pardon","pardon-ip","execute","sudo"))
            for(String prefix:List.of("","minecraft:","bukkit:"))
                assertThrows(IllegalArgumentException.class,()->read(Map.of("commands",List.of(prefix+command.toUpperCase(Locale.ROOT)+" {player}"))));
        assertThrows(IllegalArgumentException.class,()->read(Map.of("remove-commands",List.of("op {uuid}"))));
    }
    @Test void validatesDisabledSettingsAndBounds() {
        for(var boost:List.<Map<String,Object>>of(Map.of("enabled","true"),Map.of("commands","money give {player} 10"),
            Map.of("commands",List.of(12)),Map.of("commands",Collections.nCopies(11,"money give {player} 10")),
            Map.of("remove-commands",Collections.nCopies(11,"money take {uuid} 10")),
            Map.of("commands",List.of("money give {player} "+"a".repeat(256))),
            Map.of("recheck-seconds",29),Map.of("recheck-seconds",3601),Map.of("recheck-seconds",30.5),
            Map.of("cooldown-hours",-1),Map.of("cooldown-hours",8761),Map.of("cooldown-hours",1.5)))
            assertThrows(IllegalArgumentException.class,()->read(boost));
        assertThrows(IllegalArgumentException.class,()->Settings.read(Map.of(),Map.of("boost-rewards",List.of()),null));
        assertEquals(168,read(Map.of("recheck-seconds",30,"cooldown-hours",168)).boostRewards().cooldownHours());
        assertEquals(3600,read(Map.of("recheck-seconds",3600,"cooldown-hours",8760)).boostRewards().recheckSeconds());
    }
    @Test void intentRestartUsesUnionOfRoleAndBoostRequirements() {
        var disabled=read(Map.of()); var enabled=read(Map.of("enabled",true));
        assertFalse(disabled.memberIntentRequested()); assertTrue(enabled.memberIntentRequested());
        assertTrue(disabled.needsRestart(enabled));
        assertFalse(enabled.needsRestart(read(Map.of("enabled",true,"cooldown-hours",169))));
        var roles=Settings.read(Map.of(),Map.of("roles",Map.of("enabled",true)),null);
        var both=Settings.read(Map.of(),Map.of("roles",Map.of("enabled",true),"boost-rewards",Map.of("enabled",true)),null);
        assertFalse(roles.needsRestart(both)); assertFalse(both.needsRestart(enabled));
    }
    @Test void bundledConfigExplicitlyDocumentsDisabledDefaults() throws Exception {
        try(var stream=getClass().getResourceAsStream("/bot.yml")) {
            var bot=Configuration.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(false,Settings.get(bot,"boost-rewards.enabled",null));
            assertEquals(List.of(),BoostSettings.read(bot).commands());
            assertEquals(List.of(),BoostSettings.read(bot).removeCommands());
        }
    }
}
