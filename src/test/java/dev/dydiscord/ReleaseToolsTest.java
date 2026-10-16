package dev.dydiscord;
import dev.dydiscord.config.Settings;
import dev.dydiscord.command.Definition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReleaseToolsTest {
 @Test void legacyInviteAliasSurvivesAnnouncementEditing(){assertTrue(Definition.ALL.stream().filter(d->d.key().equals("invite")).anyMatch(d->d.match(new String[]{"ayarla","https://discord.gg/example"})));}
 @Test void announcementsRejectUnboundedIntervals(){
  Map<String,Object> bot=Map.of("community-tools",Map.of("announcements",Map.of("enabled",true,"schedules",List.of(Map.of("template","maintenance","every-seconds",1,"target","both")))));
  assertThrows(IllegalArgumentException.class,()->Settings.read(Map.of(),bot,null));
 }
 @Test void newAdminCommandsHaveSeparateLeafPermissions(){
  var send=Definition.ALL.stream().filter(d->d.key().equals("announce-send")).findFirst();
  assertTrue(send.isPresent());assertEquals("dydiscord.admin.announce.send",send.get().permission());
  assertTrue(Definition.ALL.stream().anyMatch(d->d.key().equals("announce-set")&&!d.permission().equals(send.get().permission())));
  assertTrue(Definition.ALL.stream().anyMatch(d->d.key().equals("config-validate")));
 }
}
