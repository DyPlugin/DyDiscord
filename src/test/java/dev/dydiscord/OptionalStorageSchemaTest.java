package dev.dydiscord;
import dev.dydiscord.giveaway.GiveawayStore;
import dev.dydiscord.chat.ChatPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
class OptionalStorageSchemaTest {
 @TempDir Path folder;
 private void unknown(Path file,String sql,int version)throws Exception{try(var db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var s=db.createStatement()){s.execute(sql);s.execute("PRAGMA user_version="+version);}}
 private List<String> shape(Path file)throws Exception {var result=new ArrayList<String>();try(var db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var s=db.createStatement()){try(var r=s.executeQuery("PRAGMA user_version")){r.next();result.add("version="+r.getInt(1));}try(var r=s.executeQuery("SELECT type,name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name")){while(r.next())result.add(r.getString(1)+r.getString(2)+r.getString(3));}}return result;}
 @Test void unknownGiveawaySchemaIsNeverCreatedOrStamped()throws Exception{
  Path file=folder.resolve("g.db");unknown(file,"CREATE TABLE giveaways(unrelated TEXT)",0);var before=shape(file);assertThrows(Exception.class,()->new GiveawayStore(file));assertEquals(before,shape(file));
 }
 @Test void futureGiveawaySchemaIsPreserved()throws Exception{
  Path file=folder.resolve("g.db");unknown(file,"CREATE TABLE foreign_data(secret TEXT)",7);var before=shape(file);assertThrows(Exception.class,()->new GiveawayStore(file));assertEquals(before,shape(file));
 }
 @Test void malformedPreferenceSchemaFailsClosedWithoutMutation()throws Exception{
  Path file=folder.resolve("p.db");unknown(file,"CREATE TABLE foreign_data(secret TEXT)",0);var before=shape(file);try(var p=new ChatPreferences(file)){assertThrows(Exception.class,()->p.get(UUID.randomUUID()).get(5,TimeUnit.SECONDS));}assertEquals(before,shape(file));
 }
 @Test void futurePreferenceSchemaCannotActivateDefaults()throws Exception{
  Path file=folder.resolve("p.db");unknown(file,"CREATE TABLE chat_preferences(uuid TEXT PRIMARY KEY,inbound INTEGER,outbound INTEGER,notify INTEGER)",7);var before=shape(file);try(var p=new ChatPreferences(file)){UUID id=UUID.randomUUID();assertThrows(Exception.class,()->p.get(id).get(5,TimeUnit.SECONDS));assertNull(p.peek(id));}assertEquals(before,shape(file));
 }
}
