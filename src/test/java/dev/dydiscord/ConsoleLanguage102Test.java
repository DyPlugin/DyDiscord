package dev.dydiscord;
import dev.dydiscord.config.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsoleLanguage102Test {
    @TempDir Path folder;
    private String logged(String language,Throwable failure)throws Exception{
        var config=TestSupport.config(folder);config.change("config.yml","lang",language);
        var host=mock(DyDiscord.class,CALLS_REAL_METHODS);var field=DyDiscord.class.getDeclaredField("configuration");field.setAccessible(true);field.set(host,config);
        var log=mock(Logger.class);doReturn(log).when(host).getLogger();host.error(failure);
        var text=org.mockito.ArgumentCaptor.forClass(String.class);verify(log).warning(text.capture());return text.getValue();
    }
    @Test void turkishFileFailureExplainsFileWithoutLeakingPrivateYaml()throws Exception{
        String text=logged("tr",new Configuration.FileFailure("bot.yml",new java.io.IOException("SECRET-TOKEN-PRIVATE")));
        assertTrue(text.contains("bot.yml"));assertTrue(text.contains("yüklenemedi"));assertFalse(text.contains("SECRET"));assertFalse(text.contains("Cannot load"));
    }
    @Test void turkishBadSettingShowsTheSettingAndHowToCorrectIt()throws Exception{
        String text=logged("tr",new IllegalArgumentException("Invalid setting: console.channel-id"));
        assertTrue(text.contains("console.channel-id"));assertTrue(text.contains("ayar"));assertFalse(text.contains("Invalid setting"));
    }
    @Test void turkishTimeoutUsesEverydayExplanation()throws Exception{
        assertTrue(logged("tr",new java.util.concurrent.TimeoutException("private endpoint SECRET")).contains("zaman"));
    }
    @Test void startupLanguageWorksEvenWhenYamlCannotBeParsed()throws Exception{
        Files.writeString(folder.resolve("config.yml"),"lang: auto\nbroken: [\n");
        Files.writeString(folder.resolve("bot.yml"),"bot:\n  language: tr\n");
        assertEquals("tr",dev.dydiscord.language.ConsoleDiagnostics.detectLanguage(folder));
        Files.writeString(folder.resolve("config.yml"),"lang: en\nbroken: [\n");
        assertEquals("en",dev.dydiscord.language.ConsoleDiagnostics.detectLanguage(folder));
    }
    @Test void yamlErrorKeepsLocationButNotSourceLine()throws Exception{
        var syntax=assertThrows(Configuration.SyntaxFailure.class,()->Configuration.parse("token: PRIVATE_SECRET\nlist: [\n"));
        assertTrue(syntax.line()>0);assertTrue(syntax.column()>0);assertNull(syntax.getCause());
        assertFalse(syntax.toString().contains("PRIVATE_SECRET"));
        String text=logged("tr",new Configuration.FileFailure("bot.yml",syntax));
        assertTrue(text.contains("satır"));assertTrue(text.contains("sütun"));assertFalse(text.contains("PRIVATE_SECRET"));
    }
    @Test void diagnosticsPreserveOwnerTranslationsAndEnglishFallback()throws Exception{
        var config=TestSupport.config(folder);
        String custom=Files.readString(folder.resolve("messages_tr.yml")).replace("İşlem zamanında tamamlanamadı. Sunucu yükünü ve Discord bağlantısını kontrol edip biraz sonra yeniden deneyin.","Özel zaman aşımı mesajı.");
        Files.writeString(folder.resolve("messages_tr.yml"),custom);config.load();
        assertEquals("Özel zaman aşımı mesajı.",dev.dydiscord.language.ConsoleDiagnostics.text(config.active().messages(),"tr","error-timeout",Map.of()));
        assertTrue(dev.dydiscord.language.ConsoleDiagnostics.text(null,"unknown","error-timeout",Map.of()).contains("time"));
    }
    @Test void compactSeparatorUpgradesOnlyKnownBundledText()throws Exception{
        TestSupport.config(folder);
        Path file=folder.resolve("discord/messages_en.yml");String bundled=Files.readString(file);
        Files.writeString(file,bundled.replace("&8»","&8>>"));
        assertTrue(dev.dydiscord.language.Messages.load(folder).get(true,"en","chat-to-minecraft").contains("&8»"));
        Files.writeString(file,bundled.replace("&9[Discord]","&9[MyChat]").replace("&8»","&8>>"));
        assertTrue(dev.dydiscord.language.Messages.load(folder).get(true,"en","chat-to-minecraft").contains("[MyChat]"));
        assertTrue(dev.dydiscord.language.Messages.load(folder).get(true,"en","chat-to-minecraft").contains(">>"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"giveaway-enter,81","giveaway-leave,81","giveaway-leave-cancel,81","giveaway-enter,0","giveaway-leave,0","giveaway-leave-cancel,0"})
    void invalidGiveawayButtonTextIsRejectedBeforeReload(String key,int length)throws Exception{
        TestSupport.config(folder);Path file=folder.resolve("discord/messages_en.yml");
        Files.writeString(file,Files.readString(file).replaceAll("(?m)^"+key+":.*$",key+": \""+"x".repeat(length)+"\""));
        assertThrows(java.io.IOException.class,()->dev.dydiscord.language.Messages.load(folder));
    }
}
