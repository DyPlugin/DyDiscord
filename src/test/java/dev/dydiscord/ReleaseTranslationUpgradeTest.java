package dev.dydiscord;
import dev.dydiscord.config.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReleaseTranslationUpgradeTest {
 @TempDir Path folder;
 @Test void oldLanguageFilesGainNewLocalizedKeysWithoutOverwritingOwnerTextOrRepeatingBackups()throws Exception{
  var c=TestSupport.config(folder);
  for(String surface:List.of("","discord/"))for(String lang:List.of("en","tr")){
   Path file=folder.resolve(surface+"messages_"+lang+".yml");var entries=new LinkedHashMap<>(Configuration.read(file));entries.remove("link-welcome");entries.remove("giveaway-title");entries.remove("server-started");entries.remove("help-chat-inbound");entries.put("no-permission","OWNER CUSTOM");Files.writeString(file,"# owner-note\n"+new Yaml().dump(entries));
  }
  c.load();assertEquals("Çekiliş",c.active().messages().get(true,"tr","giveaway-title"));assertTrue(c.active().messages().get(false,"tr","link-welcome").contains("Hoş geldiniz"));
  Map<Path,byte[]> after=new HashMap<>();try(var files=Files.walk(folder)){for(Path file:files.filter(Files::isRegularFile).toList())after.put(file,Files.readAllBytes(file));}
  for(String surface:List.of("","discord/"))for(String lang:List.of("en","tr")){
   Path file=folder.resolve(surface+"messages_"+lang+".yml");try{Configuration.read(file);}catch(Exception failure){Files.copy(file,Path.of(".local/simplify-101/broken-"+lang+".yml"),StandardCopyOption.REPLACE_EXISTING);throw failure;}assertTrue(Configuration.read(file).containsKey("link-welcome"));if(!surface.isEmpty())assertFalse(Configuration.read(file).containsKey("server-started"));assertTrue(Files.readString(file).contains("owner-note"));assertEquals("OWNER CUSTOM",c.active().messages().get(!surface.isEmpty(),lang,"no-permission"));
  }
  c.load();try(var files=Files.walk(folder)){assertEquals(after.size(),files.filter(Files::isRegularFile).count());}for(var entry:after.entrySet())assertArrayEquals(entry.getValue(),Files.readAllBytes(entry.getKey()));
 }
 @Test void everyAddedReleaseMessageKeyIsCoveredByLocalizedMigration()throws Exception{
  try(var fixture=getClass().getResourceAsStream("/pre-100-message-keys.json")){
   var previous=Configuration.parse(new String(fixture.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   for(String surface:List.of("minecraft","discord"))try(var bundle=getClass().getResourceAsStream((surface.equals("discord")?"/discord/":"/")+"messages_en.yml")){
    var current=Configuration.parse(new String(bundle.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));Set<?> old=new HashSet<>((List<?>)previous.get(surface));
    for(String key:current.keySet())if(!old.contains(key))assertTrue(dev.dydiscord.language.Messages.releaseKey(key),"Missing release migration: "+surface+":"+key);
   }
  }
 }}
