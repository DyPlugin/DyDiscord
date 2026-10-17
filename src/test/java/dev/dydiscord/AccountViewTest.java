package dev.dydiscord;

import dev.dydiscord.config.*;
import dev.dydiscord.discord.*;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.components.buttons.*;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountViewTest {
    @TempDir Path folder;
    @Test void privateDetailsUseReadableLocalizedDateAndOnlyPresentRoles()throws Exception{
        var config=TestSupport.config(folder);var messages=config.active().messages();
        var link=new Store.Link(UUID.randomUUID(),"123456789012345678","TheClaww",Instant.parse("2026-10-17T09:38:00Z"),"revision","Claw");
        var options=new AccountSettings(true,ZoneId.of("Europe/Istanbul"));
        String tr=AccountView.details(messages,"tr",link,"Claw",options,List.of("VIP","@everyone **fake**"));
        assertTrue(tr.contains("Minecraft hesabınız: TheClaww"));assertTrue(tr.contains("Discord hesabınız: Claw"));assertTrue(tr.contains("17 Ekim 2026, 12:38"));assertTrue(tr.contains("Entegre roller: VIP"));
        assertFalse(tr.contains(link.discord()));assertFalse(tr.contains("@everyone"));assertFalse(tr.contains("**fake**"));
        assertFalse(AccountView.details(messages,"tr",link,"Claw",options,List.of()).contains("Entegre roller:"));
        assertFalse(AccountView.details(messages,"tr",link,"Claw",new AccountSettings(false,ZoneId.of("UTC")),List.of("VIP")).contains("Entegre roller:"));
        assertTrue(AccountView.details(messages,"en",link,"Claw",options,List.of()).contains("October 17, 2026"));
    }
    @Test void confirmationHasRedRemoveGreenCancelWithoutTitle()throws Exception{
        var card=AccountView.confirmation(TestSupport.config(folder).active().messages(),"tr","ABC234",30);
        try(var data=PanelView.create(card)){
            assertTrue(data.isUsingComponentsV2());assertTrue(data.getEmbeds().isEmpty());assertTrue(data.getAllowedMentions().isEmpty());
            var buttons=data.getComponentTree().findAll(Button.class);assertEquals(2,buttons.size());
            assertEquals(ButtonStyle.DANGER,buttons.get(0).getStyle());assertEquals("Kaldır",buttons.get(0).getLabel());
            assertEquals(ButtonStyle.SUCCESS,buttons.get(1).getStyle());assertEquals("Vazgeç",buttons.get(1).getLabel());
            assertEquals("Eşlemeyi kaldırmak istiyor musunuz?",data.getComponentTree().findAll(TextDisplay.class).stream().findFirst().orElseThrow().getContent());
            assertEquals(new java.awt.Color(0xED4245),card.getAccentColor());
        }
    }
    @Test void upgradeRecognizesPreviousDefaultsAndPreservesOwnersText()throws Exception{
        var config=TestSupport.config(folder);Path file=folder.resolve("discord/messages_tr.yml");
        try(var in=getClass().getResourceAsStream("/legacy/release-101/discord/messages_tr.yml")){Files.copy(in,file,StandardCopyOption.REPLACE_EXISTING);}
        config.load();assertEquals("Hesabınız bir Minecraft hesabıyla eşleştirilmemiş.",config.active().messages().get(true,"tr","not-linked"));
        String text=Files.readString(file).replace("not-linked: \"Hesaplarınız henüz eşleştirilmemiş.\"","not-linked: \"Özel mesajım\"");Files.writeString(file,text);
        config.load();assertEquals("Özel mesajım",config.active().messages().get(true,"tr","not-linked"));
        String before=Files.readString(file);config.load();assertEquals(before,Files.readString(file));
    }
    @Test void invalidDateZonePreservesActiveConfiguration()throws Exception{
        var config=TestSupport.config(folder);var before=config.active();
        assertThrows(IllegalArgumentException.class,()->config.change("bot.yml","account.time-zone","Not/AZone"));assertSame(before,config.active());
    }
    @Test void ownersLegacyDatePlaceholderIsRenderedOnce()throws Exception{
        var config=TestSupport.config(folder);Path file=folder.resolve("discord/messages_en.yml");
        Files.writeString(file,Files.readString(file).replace("account: \"Minecraft account: {name}\\nDiscord account: {discord}\"","account: \"{name} / {discord} / {date}\""));config.load();
        var link=new Store.Link(UUID.randomUUID(),"123456789012345678","Alice",Instant.parse("2026-10-17T09:38:00Z"),"revision","Claw");
        String text=AccountView.details(config.active().messages(),"en",link,"Claw",config.active().settings().accounts(),List.of());
        assertEquals("Alice / Claw / October 17, 2026, 09:38 UTC",text);assertFalse(text.contains("{date}"));
    }
    @Test void unsupportedMinecraftDatePlaceholderCannotLoad()throws Exception{
        var config=TestSupport.config(folder);Path file=folder.resolve("messages_en.yml");
        Files.writeString(file,Files.readString(file).replace("account: \"&7Minecraft: &f{name} &7| Discord: &f{discord}\"","account: \"{name} {date}\""));
        assertThrows(java.io.IOException.class,config::load);
    }
}
