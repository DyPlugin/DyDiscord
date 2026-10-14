package dev.dydiscord;
import dev.dydiscord.config.Settings;
import dev.dydiscord.command.Definition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PanelModeTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder;
    @Test void commandPromptsUseSlashPathsWithoutPanelLinks()throws Exception{
        var config=TestSupport.config(folder);var host=org.mockito.Mockito.mock(DyDiscord.class);org.mockito.Mockito.when(host.messages()).thenReturn(config.active().messages());var store=org.mockito.Mockito.mock(dev.dydiscord.storage.Store.class);org.mockito.Mockito.when(host.store()).thenReturn(store);org.mockito.Mockito.when(store.panel()).thenReturn(Optional.empty());
        for(String language:List.of("en","tr")){org.mockito.Mockito.when(host.settings()).thenReturn(Settings.read(Map.of(),Map.of("bot",Map.of("language",language),"panel",Map.of("enabled",false)),null));var bot=new dev.dydiscord.discord.Bot(host);String prompt=bot.commandPrompt("link");assertTrue(prompt.contains(language.equals("tr")?"/eşle":"/link"));assertFalse(prompt.contains("https://"));}
    }
    @Test void panelDefaultsOnAndCanChangeWithoutRestart(){var before=Settings.read(Map.of(),Map.of(),null);var after=Settings.read(Map.of(),Map.of("panel",Map.of("enabled",false)),null);assertTrue(before.panelEnabled());assertFalse(after.panelEnabled());assertFalse(before.needsRestart(after));assertTrue(after.linking());}
    @Test void invalidFlagRejectedEvenWithoutPanelChannel(){assertThrows(IllegalArgumentException.class,()->Settings.read(Map.of(),Map.of("panel",Map.of("enabled","false")),null));}
    @Test void panelToggleUsesOwnLeafForBothLanguages(){for(String action:List.of("enable","disable")){var d=Definition.ALL.stream().filter(v->v.path().equals("admin panel "+action)).findFirst().orElseThrow();assertEquals("dydiscord.admin.panel.toggle",d.permission());}assertEquals(List.of(),Definition.tabs(new String[]{"admin","panel",""},"en",true,p->false));assertTrue(Definition.tabs(new String[]{"admin","panel",""},"tr",true,p->p.equals("dydiscord.admin.panel.toggle")).contains("kapat"));}
}
