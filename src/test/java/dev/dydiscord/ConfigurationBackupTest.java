package dev.dydiscord;

import dev.dydiscord.config.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationBackupTest {
    @TempDir Path folder;
    @Test void oldTurkishPackReceivesNewServerKeysWithoutChangingCustomMessages()throws Exception{
        Configuration c=TestSupport.config(folder);Path file=folder.resolve("discord/messages_tr.yml");String old=Files.readString(file).replaceAll("(?m)^(server-info-[^:]+|slash-server|help-server):[^\\r\\n]*\\r?\\n","");
        Files.writeString(file,old+"\n# Owner custom text stays.\nowner-message: keep\n");c.load();
        assertTrue(Configuration.read(file).containsKey("server-info-players"));assertEquals("keep",Configuration.read(file).get("owner-message"));assertTrue(Files.readString(file).contains("# Owner custom text stays."));
        assertFalse(c.active().messages().missing().contains("discord/messages_tr.yml:server-info-players"));long count=backups().size();c.load();assertEquals(count,backups().size());
    }

    private List<Path> backups()throws Exception {
        try(var files=Files.walk(folder)) {
            return files.filter(p->p.getFileName().toString().contains(".backup-")).toList();
        }
    }

    @Test void currentFilesKeepOwnerFormattingWithoutMigrationBackups()throws Exception {
        Configuration c=TestSupport.config(folder);
        Map<Path,String> originals=new LinkedHashMap<>();
        for(String resource:List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml")) {
            Path file=folder.resolve(resource);
            String ownerText=Files.readString(file).replace("\n","\r\n")+"\r\n# Owner keeps Windows line endings.\r\n";
            Files.writeString(file,ownerText);originals.put(file,ownerText);
        }
        long count=backups().size();
        for(int i=0;i<10;i++)c.load();
        for(var entry:originals.entrySet())assertEquals(entry.getValue(),Files.readString(entry.getKey()));
        assertEquals(count,backups().size());
    }

    @Test void identicalLegacyContentReusesItsBackupAcrossRepeatedMigrations()throws Exception {
        Configuration c=TestSupport.config(folder);long count=backups().size();
        String legacy="config-version: 1\n# Owner settings\nlang: tr\nserver-name: Owner server\nowner-setting: keep\n";
        Path file=folder.resolve("config.yml");
        for(int i=0;i<10;i++) {
            Files.writeString(file,legacy);c.load();
            assertEquals("tr",c.active().settings().language());
            assertEquals("keep",Configuration.read(file).get("owner-setting"));
        }
        assertEquals(count+1,backups().size());
        assertTrue(backups().stream().anyMatch(p->{try{return Files.readString(p).equals(legacy);}catch(Exception failure){return false;}}));
        String changed=legacy.replace("Owner server","Changed server");Files.writeString(file,changed);c.load();
        assertEquals(count+2,backups().size());
        assertTrue(backups().stream().anyMatch(p->{try{return Files.readString(p).equals(changed);}catch(Exception failure){return false;}}));
    }

    @Test void failedMigrationRetriesReuseBackupsAndKeepOriginals()throws Exception {
        Configuration c=TestSupport.config(folder);var active=c.active();long count=backups().size();
        Path file=folder.resolve("config.yml");String legacy="config-version: 1\nlang: tr\n";Files.writeString(file,legacy);
        try(var mocked=org.mockito.Mockito.mockStatic(Configuration.class,org.mockito.Mockito.CALLS_REAL_METHODS)) {
            mocked.when(()->Configuration.atomicWrite(org.mockito.ArgumentMatchers.any(Path.class),org.mockito.ArgumentMatchers.anyString())).thenThrow(new java.io.IOException("Synthetic write failure"));
            for(int i=0;i<10;i++) {
                assertThrows(java.io.IOException.class,c::load);
                assertEquals(legacy,Files.readString(file));assertSame(active,c.active());
            }
        }
        assertEquals(count+1,backups().size());
    }

    @Test void changedExistingBackupStopsMigrationBeforeOverwritingOriginal()throws Exception {
        Configuration c=TestSupport.config(folder);
        Path file=folder.resolve("config.yml");String legacy="config-version: 1\nlang: tr\n";
        Files.writeString(file,legacy);c.load();var active=c.active();
        Path backup=backups().stream().filter(p->{try{return Files.readString(p).equals(legacy);}catch(Exception failure){return false;}}).findFirst().orElseThrow();
        String damaged="Owner retained a different backup.\n";Files.writeString(backup,damaged);Files.writeString(file,legacy);
        assertThrows(java.io.IOException.class,c::load);
        assertEquals(legacy,Files.readString(file));assertEquals(damaged,Files.readString(backup));assertSame(active,c.active());
    }
}
