package dev.dydiscord;
import dev.dydiscord.community.*;
import dev.dydiscord.config.*;
import dev.dydiscord.discord.Bot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CommunityToolsTest {
 @TempDir Path folder;
 @Test void validatesWithoutWritingOrActivating()throws Exception{
  Configuration c=TestSupport.config(folder);var active=c.active();Map<Path,byte[]> before=new HashMap<>();try(var walk=Files.walk(folder)){walk.filter(Files::isRegularFile).forEach(path->{try{before.put(path,Files.readAllBytes(path));}catch(Exception e){throw new RuntimeException(e);}});}
  assertTrue(Support.validate(c).isEmpty());assertSame(active,c.active());for(var entry:before.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(entry.getKey()));
 }
 @Test void invalidTranslationIsRejectedWithoutActivation()throws Exception{
  Configuration c=TestSupport.config(folder);var active=c.active();Files.writeString(folder.resolve("messages_en.yml"),Files.readString(folder.resolve("messages_en.yml")).replace("{invite_url}","{wrong}"));assertThrows(Exception.class,()->Support.validate(c));assertSame(active,c.active());
 }
 @Test void diagnosticReportNeverIncludesConfiguredSecretsOrAccountIdentifiers()throws Exception{
  Configuration c=TestSupport.config(folder);c.change("bot.yml","token","FAKE_SECRET_NEVER_INCLUDE");c.change("bot.yml","guild-id","1557418524983566447");var p=mock(DyDiscord.class);when(p.settings()).thenReturn(c.active().settings());when(p.bot()).thenReturn(mock(Bot.class));when(p.store()).thenReturn(mock(dev.dydiscord.storage.Store.class));var description=new org.bukkit.plugin.PluginDescriptionFile("DyDiscord","1.0.0","dev.dydiscord.DyDiscord");when(p.getDescription()).thenReturn(description);String report=Support.report(p);assertFalse(report.contains("FAKE_SECRET"));assertFalse(report.contains("1557418524983566447"));
 }
 @Test void retiredNoticeSettingsCannotActivateFeatures(){
  var settings=Settings.read(Map.of("updates",Map.of("notify","players","include-beta",true)),Map.of("community-tools",Map.of("startup-notice",true)),null);
  assertEquals("players",settings.tools().updateAudience());assertTrue(settings.tools().updateBeta());
 }
}
