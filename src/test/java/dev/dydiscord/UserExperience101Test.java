package dev.dydiscord;

import dev.dydiscord.config.Configuration;
import dev.dydiscord.linking.Linking;
import dev.dydiscord.storage.Store;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserExperience101Test {
    @TempDir Path folder;
    @Test void sixCharacterCodeIsRedeemedOnceAndRemainsRateLimited()throws Exception{
        var config=TestSupport.config(folder);config.change("config.yml","linking.code-length",6);
        try(var store=new Store(folder.resolve("test.db"))){
            var linking=new Linking(store,new TestSupport.Time(),new SecureRandom());
            var id=UUID.randomUUID();String code=linking.issue(id,"TheClaww",1,config.active().settings(),true).code();
            assertEquals(6,code.length());assertTrue(linking.inspect(code).isPresent());
            for(int i=0;i<5;i++)assertThrows(Linking.Rejected.class,()->linking.redeem("000000","223456789012345678",config.active().settings(),true,c->true));
            assertEquals("rate-limit",assertThrows(Linking.Rejected.class,()->linking.redeem(code,"223456789012345678",config.active().settings(),true,c->true)).key);
            assertEquals(id,linking.redeem(code,"123456789012345678",config.active().settings(),true,c->true).uuid());
            assertTrue(linking.inspect(code).isEmpty());
        }
    }
    @Test void alreadyLinkedShowsOnlyOwnDiscordNameAndEscapesIt()throws Exception{
        var config=TestSupport.config(folder);var time=new TestSupport.Time();UUID id=UUID.randomUUID();
        try(var store=new Store(folder.resolve("existing.db"))){
            store.link(id,"123456789012345678","TheClaww","Claw*name",time.instant(),()->true);
            var linking=new Linking(store,time,new SecureRandom());var failure=assertThrows(Linking.Rejected.class,()->linking.issue(id,"TheClaww",1,config.active().settings(),true));
            assertEquals("already-linked",failure.key);assertEquals(Map.of("discord","Claw*name"),failure.values);
            String shown=config.active().messages().render(true,"tr",failure.key,failure.values);assertTrue(shown.contains("@Claw\\*name hesabıyla eşli!"));assertFalse(shown.contains("123456789012345678"));
        }
    }
    @Test void defaultCodeAndActorBoundConfirmationAreShort()throws Exception{
        var config=TestSupport.config(folder);assertEquals(6,config.active().settings().codeLength());
        try(var store=new Store(folder.resolve("test.db"))){
            var linking=new Linking(store,new TestSupport.Time(),new SecureRandom());
            var link=store.link(UUID.randomUUID(),"123456789012345678","TheClaww",new TestSupport.Time().instant());
            String token=linking.confirm("owner",link,"permission","",30);
            assertEquals(6,token.length());assertTrue(linking.pending(token,"stranger").isEmpty());
            assertThrows(Linking.Rejected.class,()->linking.remove(token,"stranger",p->true,c->{}));
            assertTrue(linking.remove(token,"owner",p->true,c->{}));
        }
    }
    @Test void unsetGameLanguageFollowsBotButExplicitGameLanguageWins()throws Exception{
        var config=TestSupport.config(folder);config.change("bot.yml","bot.language","tr");
        var host=mock(DyDiscord.class);when(host.settings()).thenAnswer(c->config.active().settings());
        doCallRealMethod().when(host).language(any());
        assertEquals("tr",host.language(mock(CommandSender.class)));
        config.change("config.yml","lang","en");assertEquals("en",host.language(mock(CommandSender.class)));
        config.change("bot.yml","bot.language","auto");assertEquals("en",config.active().settings().botLanguage());
    }
    @Test void successIsOneNaturalSentenceAndNoAccountHasClearMeaning()throws Exception{
        var messages=TestSupport.config(folder).active().messages();
        assertEquals("**TheClaww** adlı Minecraft hesabınız Discord hesabınızla eşleştirildi!",messages.render(true,"tr","linked",Map.of("name","TheClaww","discord","Claw")));
        assertEquals("Hesabınız bir Minecraft hesabıyla eşleştirilmemiş.",messages.get(true,"tr","not-linked"));
    }
    @Test void administratorHelpUsesNumberedPagesAndRejectsTopics()throws Exception{
        var config=TestSupport.config(folder);var host=mock(DyDiscord.class);
        when(host.ready()).thenReturn(true);when(host.language(any())).thenReturn("tr");when(host.messages()).thenReturn(config.active().messages());
        var sender=mock(CommandSender.class);when(sender.hasPermission(anyString())).thenReturn(true);
        var router=new dev.dydiscord.command.Router(host,false);
        router.onCommand(sender,mock(org.bukkit.command.Command.class),"dydiscord",new String[]{"admin","yardım","konsol"});
        verify(host).message(eq(sender),eq("help-page-invalid"),any());verify(sender,never()).sendMessage(anyString());
        router.onCommand(sender,mock(org.bukkit.command.Command.class),"dydiscord",new String[]{"admin","yardım","1"});
        var lines=org.mockito.ArgumentCaptor.forClass(String.class);verify(sender,atLeastOnce()).sendMessage(lines.capture());
        assertEquals(8,lines.getAllValues().stream().filter(line->line.startsWith("§6/dydiscord")).count());
        assertTrue(router.onTabComplete(sender,mock(org.bukkit.command.Command.class),"dydiscord",new String[]{"admin","yardım",""}).stream().allMatch(value->value.matches("[1-9][0-9]*")));
    }
    @Test void automaticInvitationCannotOverwriteAnOwnersPendingDiskEdit()throws Exception{
        var config=TestSupport.config(folder);config.change("bot.yml","guild-id","123456789012345678");
        Path path=folder.resolve("config.yml");String original=java.nio.file.Files.readString(path);
        java.nio.file.Files.writeString(path,original.replace("invite-url: \"\"","invite-url: \"https://discord.gg/owners-choice\""));
        assertFalse(config.inviteIfMissing("123456789012345678","https://discord.gg/generated",next->{}));
        assertTrue(java.nio.file.Files.readString(path).contains("https://discord.gg/owners-choice"));
    }
    @Test void inviteValidationRaceKeepsNewManualValueAndDoesNotPublishGeneratedOne()throws Exception{
        var config=TestSupport.config(folder);config.change("bot.yml","guild-id","123456789012345678");Path file=folder.resolve("config.yml");String before=java.nio.file.Files.readString(file);
        assertThrows(java.io.IOException.class,()->config.inviteIfMissing("123456789012345678","https://discord.gg/generated",next->java.nio.file.Files.writeString(file,before.replace("invite-url: \"\"","invite-url: \"https://discord.gg/manual\""))));
        assertTrue(java.nio.file.Files.readString(file).contains("https://discord.gg/manual"));assertTrue(config.active().settings().invite().isEmpty());
    }
    @Test void obsoleteShippedCommentsAreUpdatedOnceAndOwnerCommentsSurvive()throws Exception{
        var config=TestSupport.config(folder);Path file=folder.resolve("config.yml");
        java.nio.file.Files.writeString(file,java.nio.file.Files.readString(file).replace("Random code length (6-16); existing explicit values are preserved on upgrade.","Random code length (8-16); existing explicit values are preserved on upgrade.")+"\n# owner's special note\n");
        config.load();String updated=java.nio.file.Files.readString(file);assertFalse(updated.contains("Random code length (8-16)"));assertTrue(updated.contains("owner's special note"));
        config.load();assertEquals(updated,java.nio.file.Files.readString(file));
    }
    @Test void deliberatelyConfiguredEightCharacterLengthSurvivesReload()throws Exception{
        var config=TestSupport.config(folder);config.change("config.yml","linking.code-length",8);config.load();
        assertEquals(8,config.active().settings().codeLength());
    }
    @Test void previousShippedEightCharacterDefaultMigratesWithItsComment()throws Exception{
        var config=TestSupport.config(folder);Path file=folder.resolve("config.yml");
        java.nio.file.Files.writeString(file,java.nio.file.Files.readString(file).replace("code-length: 6","code-length: 8").replace("Random code length (6-16); existing explicit values are preserved on upgrade.","Random code length (8-16); existing explicit values are preserved on upgrade."));
        config.load();assertEquals(6,config.active().settings().codeLength());
    }
}
