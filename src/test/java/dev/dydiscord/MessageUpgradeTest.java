package dev.dydiscord;

import dev.dydiscord.config.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageUpgradeTest {
    @Test void shippedTurkishMessagesUpgradeInMemoryWhileCustomTranslationsAndCommentsSurvive()throws Exception{
        Configuration c=TestSupport.config(folder);
        for(String surface:List.of("","discord/"))try(var in=getClass().getResourceAsStream("/legacy/"+surface+"messages_tr.yml")){
            String old=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)+"\n# owner comment\n";Path file=folder.resolve(surface+"messages_tr.yml");Files.writeString(file,old);c.load();
            assertTrue(c.active().messages().get(!surface.isEmpty(),"tr","invalid-code").startsWith("Kod yanlış")||c.active().messages().get(!surface.isEmpty(),"tr","invalid-code").startsWith("&cKod yanlış"));
            assertEquals("Bağlı",c.active().messages().get(!surface.isEmpty(),"tr","state-connected"));assertTrue(Files.readString(file).contains("# owner comment"));assertTrue(Configuration.read(file).entrySet().containsAll(Configuration.parse(old).entrySet()));
            Files.writeString(file,old.replace("Bu eşleme kodu geçersiz veya süresi dolmuş.","Özel hata metnim.").replace("Bu kod geçersiz veya süresi dolmuş. Minecraft içinde yeni kod üretin.","Özel hata metnim."));c.load();assertTrue(c.active().messages().get(!surface.isEmpty(),"tr","invalid-code").contains("Özel hata metnim."));
        }
        assertFalse(c.active().messages().get(false,"tr","link-code").contains("minutes"));assertTrue(c.active().messages().get(false,"tr","confirm").contains("/dydiscord onayla"));
    }
    @TempDir Path folder;
    @Test void oldTurkishSetupHintUsesAdminWithoutRewritingOwnerText()throws Exception{
        Configuration c=TestSupport.config(folder);Path file=folder.resolve("messages_tr.yml");
        String old="&eBotu açmadan önce bot.yml içinde token ve guild-id ayarlayın, sonra /dydiscord yönetim yenile çalıştırın.";
        String yaml="bot-setup-incomplete: \""+old+"\"\n# Owner note\n";Files.writeString(file,yaml);c.load();
        assertTrue(c.active().messages().get(false,"tr","bot-setup-incomplete").contains("/dydiscord admin yenile"));assertEquals(old,Configuration.read(file).get("bot-setup-incomplete"));assertTrue(Files.readString(file).contains("# Owner note"));
        Files.writeString(file,"bot-setup-incomplete: 'Özel kurulum açıklamam.'\n");c.load();assertEquals("Özel kurulum açıklamam.",c.active().messages().get(false,"tr","bot-setup-incomplete"));
    }
    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void previousNumberedPanelDefaultsBecomeSpacedExplanationsWhileCustomDescriptionsSurvive(String lang)throws Exception{
        Configuration c=TestSupport.config(folder);boolean tr=lang.equals("tr");
        String old=tr?"Minecraft ve Discord hesaplarınızı üç adımda bağlayın.\n\n1. Oyunda **/discord eşle** yazın.\n2. Aşağıdaki **Hesap Eşle** butonuna basın.\n3. Özel kodunuzu açılan forma girin.":"Connect your Minecraft and Discord accounts in three steps.\n\n1. Run **/discord link** in Minecraft.\n2. Select **Link Account** below.\n3. Enter your private code in the form.";
        Path file=folder.resolve("discord/messages_"+lang+".yml");String yaml="panel-description: \""+old.replace("\n","\\n")+"\"\n# owner's comment\n";Files.writeString(file,yaml);c.load();
        String body=c.active().messages().get(true,lang,"panel-description");assertTrue(body.contains(tr?"**Hesabım:**":"**My Account:**"));assertEquals(4,body.split("\n\n",-1).length);assertFalse(body.contains("\n\n\n"));assertFalse(body.contains("1."));assertFalse(body.contains("Onayınızla"));assertFalse(body.contains("after confirmation"));assertTrue(Files.readString(file).contains("# owner's comment"));assertTrue(Configuration.read(file).entrySet().containsAll(Configuration.parse(yaml).entrySet()));
        Files.writeString(file,"panel-description: 'My custom instructions'");c.load();assertEquals("My custom instructions",c.active().messages().get(true,lang,"panel-description"));
    }
    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void previousCompactPanelDefaultsGainParagraphSpacingWithoutChangingOwnerFiles(String lang)throws Exception{
        Configuration c=TestSupport.config(folder);boolean tr=lang.equals("tr");
        String compact=tr?"Minecraft hesabınızı Discord hesabınızla eşleştirmek için oyunda `/discord eşle` komutuyla kodunuzu alın ve aşağıdaki **Hesap Eşle** butonundan kodunuzu girin.\n**Hesap Eşle:** Oyun içindeki kodla hesaplarınızı bağlar.\n**Hesabım:** Eşlenen Minecraft ve Discord hesaplarınızı gösterir.\n**Eşlemeyi Kaldır:** Onayınızla mevcut bağlantıyı kaldırır.":"To connect your Minecraft and Discord accounts, get your code in-game with `/discord link`, then select **Link Account** below and enter your code.\n**Link Account:** Connects your accounts using your in-game code.\n**My Account:** Shows your linked Minecraft and Discord accounts.\n**Unlink:** Removes your existing link after confirmation.";
        Path file=folder.resolve("discord/messages_"+lang+".yml");String yaml="panel-description: \""+compact.replace("\n","\\n")+"\"\n# owner's comment\n";Files.writeString(file,yaml);c.load();
        String expected=compact.replace("\n","\n\n").replace(tr?"Onayınızla mevcut bağlantıyı kaldırır.":"Removes your existing link after confirmation.",tr?"Mevcut bağlantıyı kaldırır.":"Removes your existing link.");
        if(tr)assertTrue(c.active().messages().get(true,lang,"panel-description").contains("Oyundaki kodunuzla hesaplarınızı eşleştirir."));else assertEquals(expected,c.active().messages().get(true,lang,"panel-description"));assertTrue(Files.readString(file).contains("# owner's comment"));assertTrue(Configuration.read(file).entrySet().containsAll(Configuration.parse(yaml).entrySet()));
    }
    @ParameterizedTest @ValueSource(strings={"en","tr"})
    void installedDefaultTemplatesUpgradeWithoutIdsDatesOrLosingComments(String lang)throws Exception{
        Configuration c=TestSupport.config(folder);boolean tr=lang.equals("tr");
        String mc="account: \"&7Minecraft: &f{name} &7| Discord: &f{discord} &7| "+(tr?"Eşleme":"Linked")+" (UTC): &f{date}\"\nlinked: \""+(tr?"&aMinecraft ve Discord hesaplarınız eşlendi!":"&aYour Minecraft and Discord accounts are now linked!")+"\"\n# owner's translation comment\n";
        String dc="account: \"Minecraft: {name}\\nDiscord: {discord}\\n"+(tr?"Eşleme":"Linked")+": {date}\"\nlinked: \""+(tr?"Hesaplarınız eşlendi.":"Your accounts are now linked.")+"\"\n";
        Files.writeString(folder.resolve("messages_"+lang+".yml"),mc);Files.writeString(folder.resolve("discord/messages_"+lang+".yml"),dc);c.load();
        for(boolean discord:List.of(false,true))for(String key:List.of("account","linked")){
            String rendered=c.active().messages().render(discord,lang,key,Map.of("name","Alice","discord","the_claww"));assertTrue(rendered.contains("Alice"));if(!discord)assertTrue(rendered.contains("the_claww"));else assertFalse(rendered.contains("the_claww"));assertFalse(rendered.contains("{date}"));assertFalse(rendered.contains("UTC"));
        }
        assertTrue(Files.readString(folder.resolve("messages_"+lang+".yml")).contains("# owner's translation comment"));assertTrue(Configuration.read(folder.resolve("messages_"+lang+".yml")).entrySet().containsAll(Configuration.parse(mc).entrySet()));
    }
    @Test void validCustomTemplatesRemainWhileUnknownPlaceholdersRejectReload()throws Exception{
        Configuration c=TestSupport.config(folder);Files.writeString(folder.resolve("discord/messages_en.yml"),"linked: 'Welcome {name}, paired with {discord}!'");c.load();assertEquals("Welcome Alice, paired with Claww!",c.active().messages().render(true,"en","linked",Map.of("name","Alice","discord","Claww")));
        var active=c.active();Files.writeString(folder.resolve("discord/messages_en.yml"),"account: 'Custom {name}, {discord}, secret {token}'");assertThrows(java.io.IOException.class,c::load);assertSame(active,c.active());
    }
    @Test void invalidContainerTextRetainsPreviousSnapshot()throws Exception{
        Configuration c=TestSupport.config(folder);var active=c.active();Files.writeString(folder.resolve("discord/messages_en.yml"),"panel-description: ''");assertThrows(IllegalArgumentException.class,c::load);assertSame(active,c.active());
        Files.writeString(folder.resolve("discord/messages_en.yml"),"panel-description: '"+"a".repeat(3500)+"'\npanel-privacy: '"+"b".repeat(500)+"'");assertThrows(IllegalArgumentException.class,c::load);assertSame(active,c.active());
    }
    @Test void shorterDefaultPreservesExplicitExistingLength()throws Exception{
        Configuration c=TestSupport.config(folder);assertEquals(6,c.active().settings().codeLength());c.change("config.yml","linking.code-length",12);c.load();assertEquals(12,c.active().settings().codeLength());assertEquals(12,((Map<?,?>)Configuration.read(folder.resolve("config.yml")).get("linking")).get("code-length"));
    }
}
