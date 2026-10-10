package dev.dydiscord;

import dev.dydiscord.config.*;
import dev.dydiscord.update.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeAndUpdateTest {
    @TempDir Path folder;
    @Test void oldSchemasKeepOwnerValuesCommentsAndCreateBackupsOnce()throws Exception{
        Configuration c=TestSupport.config(folder);
        String old="config-version: 1 # owner schema comment\n# owner heading\nprefix: '&7[Custom]'\nlang: tr\nserver-name: 'My server'\nlinking:\n  code-length: 8\nowner-setting: keep\n";
        Files.writeString(folder.resolve("config.yml"),old);
        String bot="config-version: 1\ntoken: 'synthetic-test-token'\nguild-id: '123456789012345678'\n";Files.writeString(folder.resolve("bot.yml"),bot);c.load();
        assertEquals("tr",c.active().settings().language());assertEquals(8,c.active().settings().codeLength());assertEquals("&7[Custom]",c.active().settings().prefix());assertEquals("synthetic-test-token",c.active().settings().token());assertTrue(c.active().settings().updateChecks());assertEquals("",c.active().settings().panelImage());
        String text=Files.readString(folder.resolve("config.yml"));assertTrue(text.contains("owner heading"));assertTrue(text.contains("owner schema comment"));assertEquals("keep",Configuration.read(folder.resolve("config.yml")).get("owner-setting"));assertEquals(2,Configuration.read(folder.resolve("config.yml")).get("config-version"));
        try(var files=Files.list(folder)){assertTrue(files.filter(p->p.getFileName().toString().startsWith("config.yml.backup-")).anyMatch(p->{try{return Files.readString(p).equals(old);}catch(Exception e){return false;}}));}
        long count;try(var files=Files.walk(folder)){count=files.filter(p->p.toString().contains(".backup-")).count();}c.load();try(var files=Files.walk(folder)){assertEquals(count,files.filter(p->p.toString().contains(".backup-")).count());}
    }
    @Test void futureSchemaAndStalePreviewNeverOverwriteOwnerChanges()throws Exception{
        Configuration c=TestSupport.config(folder);var active=c.active();Path file=folder.resolve("config.yml");String future="config-version: 99\nlang: tr\n";Files.writeString(file,future);assertThrows(IllegalArgumentException.class,c::load);assertSame(active,c.active());assertEquals(future,Files.readString(file));
        Files.writeString(file,"config-version: 1\nlang: tr\n");var preview=c.preview();String changed="config-version: 1\nlang: en\n";Files.writeString(file,changed);assertThrows(java.io.IOException.class,()->c.apply(preview));assertEquals(changed,Files.readString(file));assertSame(active,c.active());
    }
    @ParameterizedTest @ValueSource(strings={"config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml"})
    void currentSchemaPreviewRejectsAnyOwnerFileChangeBeforeApply(String resource)throws Exception{
        Configuration c=TestSupport.config(folder);var active=c.active();var preview=c.preview();
        assertTrue(preview.migrations().isEmpty());
        String changed=switch(resource){
            case "config.yml"->"config-version: 2\nlang: tr\n";
            case "bot.yml"->"config-version: 2\nbot:\n  language: tr\n";
            default->"no-permission: 'Owner message changed during validation.'\n";
        };
        Path file=folder.resolve(resource);Files.writeString(file,changed);
        assertThrows(java.io.IOException.class,()->c.apply(preview));
        assertSame(active,c.active());assertEquals(changed,Files.readString(file));
    }
    @ParameterizedTest @ValueSource(strings={"config.yml","bot.yml"})
    void currentSchemaPreviewCannotActivateAfterOwnerChangesToFutureSchema(String resource)throws Exception{
        Configuration c=TestSupport.config(folder);var active=c.active();var preview=c.preview();
        Path file=folder.resolve(resource);String future="config-version: 99\n";Files.writeString(file,future);
        assertThrows(java.io.IOException.class,()->c.apply(preview));
        assertSame(active,c.active());assertEquals(future,Files.readString(file));
    }
    @Test void commandChangeCannotOverwriteOwnerEditDuringLiveValidation()throws Exception{
        Configuration c=TestSupport.config(folder);var active=c.active();Path file=folder.resolve("bot.yml");
        String changed=Files.readString(file)+"\n# Owner changed this file while live access was being checked.\n";
        assertThrows(java.io.IOException.class,()->c.change("bot.yml","console.enabled",true,next->Files.writeString(file,changed)));
        assertSame(active,c.active());assertEquals(changed,Files.readString(file));
    }
    @Test void failedSecondMigrationWriteRollsBackFilesAndKeepsActiveSnapshot()throws Exception{
        Configuration c=TestSupport.config(folder);var active=c.active();Path config=folder.resolve("config.yml"),bot=folder.resolve("bot.yml");String oldConfig="config-version: 1\nlang: tr\n",oldBot="config-version: 1\nenabled: false\n";Files.writeString(config,oldConfig);Files.writeString(bot,oldBot);var preview=c.preview();java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var mocked=org.mockito.Mockito.mockStatic(Configuration.class,org.mockito.Mockito.CALLS_REAL_METHODS)){
            mocked.when(()->Configuration.atomicWrite(org.mockito.ArgumentMatchers.any(Path.class),org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation->{if(calls.incrementAndGet()==2)throw new java.io.IOException("Synthetic write failure");invocation.callRealMethod();return null;});assertThrows(java.io.IOException.class,()->c.apply(preview));
        }
        assertSame(active,c.active());assertEquals(oldConfig,Files.readString(config));assertEquals(oldBot,Files.readString(bot));
    }
    @ParameterizedTest @CsvSource({"1.0.0-BETA,1.0.0","1.0.0-beta.2,1.0.0-beta.10","1.9.0,1.10.0","v1.0.0-BETA,1.0.0-rc.1"})
    void semanticUpdatesIncludeBetaToStable(String old,String newer){assertTrue(VersionNumber.parse(newer).orElseThrow().compareTo(VersionNumber.parse(old).orElseThrow())>0);}
    private String release(String id,String version,String channel,String status,String minecraft,String loader){return "{\"id\":\""+id+"\",\"version_number\":\""+version+"\",\"version_type\":\""+channel+"\",\"status\":\""+status+"\",\"game_versions\":[\""+minecraft+"\"],\"loaders\":[\""+loader+"\"]}";}
    @Test void updateSelectionFiltersCompatibilityVisibilityAndStableChannel()throws Exception{
        String json="["+String.join(",",release("stable","1.0.0","release","listed","26.3","paper"),release("wrongmc","9.0.0","release","listed","1.20","paper"),release("wrongloader","9.0.0","release","listed","26.3","fabric"),release("draft","9.0.0","release","draft","26.3","paper"),release("beta","2.0.0-beta","beta","listed","26.3","paper"))+"]";
        assertEquals("beta",Modrinth.newer(json,"1.0.0-BETA","26.3",Set.of("paper")).orElseThrow().id());assertTrue(Modrinth.newer(json,"1.0.0","26.3",Set.of("paper")).isEmpty());
        assertEquals("stable",Modrinth.newer("["+release("stable","1.0.0","release","listed","26.3","paper")+"]","1.0.0-BETA","26.3",Set.of("paper")).orElseThrow().id());
    }
    @ParameterizedTest @ValueSource(strings={"Paper","Spigot","Bukkit"})
    void baseServerCannotReceivePurpurOnlyUpdate(String platform)throws Exception{
        String json="["+release("purpurOnly","2.0.0","release","listed","26.3","purpur")+"]";
        assertTrue(Modrinth.newer(json,"1.0.0-BETA","26.3",Modrinth.loaders(platform)).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"paper","spigot","bukkit","purpur"})
    void purpurRetainsCompatibleBaseLoaderUpdates(String loader)throws Exception{
        String json="["+release("compatible","2.0.0","release","listed","26.3",loader)+"]";
        assertEquals("compatible",Modrinth.newer(json,"1.0.0-BETA","26.3",Modrinth.loaders("Purpur")).orElseThrow().id());
    }
    @Test void metadataDoesNotCreateAnUpdateAndMalformedResponsesAreRejected()throws Exception{
        assertEquals(0,VersionNumber.parse("1.0.0+one").orElseThrow().compareTo(VersionNumber.parse("1.0.0+two").orElseThrow()));assertTrue(VersionNumber.parse("not a version").isEmpty());assertThrows(java.io.IOException.class,()->Modrinth.newer("{}","1.0.0","26.3",Set.of("paper")));
    }
    @Test void customPanelImageIsValidatedAndHotReloadable()throws Exception{
        Configuration c=TestSupport.config(folder);var before=c.active().settings();c.change("bot.yml","panel.image-url","https://example.com/logo.png");assertEquals("https://example.com/logo.png",c.active().settings().panelImage());assertFalse(before.needsRestart(c.active().settings()));var active=c.active();assertThrows(IllegalArgumentException.class,()->c.change("bot.yml","panel.image-url","http://example.com/logo.png"));assertSame(active,c.active());
    }
}
