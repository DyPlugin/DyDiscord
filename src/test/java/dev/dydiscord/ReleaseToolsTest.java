package dev.dydiscord;
import dev.dydiscord.config.Settings;
import dev.dydiscord.command.Definition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReleaseToolsTest {
 @Test void legacyInviteAliasSurvivesAnnouncementEditing(){assertTrue(Definition.ALL.stream().filter(d->d.key().equals("invite")).anyMatch(d->d.match(new String[]{"ayarla","https://discord.gg/example"})));}
 @Test void retiredAnnouncementSettingsAreIgnoredUntilMigrationRemovesThem(){
  Map<String,Object> bot=Map.of("community-tools",Map.of("announcements",Map.of("enabled",true,"schedules",List.of(Map.of("template","maintenance","every-seconds",1,"target","both")))));
  assertDoesNotThrow(()->Settings.read(Map.of(),bot,null));
 }
 @Test void removedAnnouncementAndAdvancedEditingCommandsAreAbsent(){
  assertTrue(Definition.ALL.stream().noneMatch(d->d.key().startsWith("announce")||d.key().equals("config-validate")));
 }
}
