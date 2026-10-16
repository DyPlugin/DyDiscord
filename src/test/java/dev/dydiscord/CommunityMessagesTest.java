package dev.dydiscord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class CommunityMessagesTest {
 @TempDir Path folder;
 @Test void overlongGiveawayButtonRejectedBeforeReload()throws Exception{
  var c=TestSupport.config(folder);var active=c.active();Path file=folder.resolve("discord/messages_en.yml");Files.writeString(file,Files.readString(file).replace("giveaway-enter: \"Enter\"","giveaway-enter: \""+"x".repeat(81)+"\""));assertThrows(Exception.class,c::preview);assertSame(active,c.active());
 }
 @Test void repeatedWinnerPlaceholdersCannotOverflowResultCard()throws Exception{
  var c=TestSupport.config(folder);Path file=folder.resolve("discord/messages_en.yml");Files.writeString(file,Files.readString(file).replace("giveaway-drawn: \"Prize: {prize}\\nWinners: {winners}\"","giveaway-drawn: \"{prize} "+"{winners} ".repeat(10)+"\""));assertThrows(Exception.class,c::preview);
 }
}
