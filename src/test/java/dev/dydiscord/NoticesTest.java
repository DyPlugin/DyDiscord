package dev.dydiscord;
import dev.dydiscord.command.Definition;
import dev.dydiscord.config.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Retirement tests replace scheduling tests for the explicitly removed feature. */
class NoticesTest {
 @TempDir Path folder;
 @Test void retiredAnnouncementsHaveNoCommandOrService() {
  assertFalse(Definition.ALL.stream().anyMatch(d->d.key().startsWith("announce")||d.path().contains("maintenance")||d.path().contains("restartnotice")));
  assertThrows(ClassNotFoundException.class,()->Class.forName("dev.dydiscord.community.Notices"));
 }
 @Test void upgradeRemovesOnlyObsoleteSettingsAndDoesNotScheduleAnything()throws Exception{
  var config=TestSupport.config(folder);Path file=folder.resolve("bot.yml");
  Files.writeString(file,Files.readString(file)+"\n# Owner notes stay\ncommunity-tools:\n  startup-notice: true\n  templates: {welcome: hello}\n  announcements: {enabled: true, schedules: []}\nowner-extra: keep\n");
  config.load();assertFalse(Configuration.read(file).containsKey("community-tools"));assertEquals("keep",Configuration.read(file).get("owner-extra"));assertTrue(Files.readString(file).contains("Owner notes stay"));
  String migrated=Files.readString(file);config.load();assertEquals(migrated,Files.readString(file));
 }
}
