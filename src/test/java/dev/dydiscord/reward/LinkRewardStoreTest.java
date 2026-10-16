package dev.dydiscord.reward;

import dev.dydiscord.config.LinkRewardSettings;
import dev.dydiscord.storage.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LinkRewardStoreTest {
    @TempDir Path temp;
    private Store.Link link(UUID uuid,String discord){return new Store.Link(uuid,discord,"Alice",Instant.now(),UUID.randomUUID().toString());}
    @Test void bothIdentitiesArePermanentEvenWhenCounterpartWasAlreadySeen()throws Exception{
        try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){
            UUID a=UUID.randomUUID(),b=UUID.randomUUID();
            assertTrue(store.claim(link(a,"123"),true));
            assertFalse(store.claim(link(a,"456"),true));
            assertFalse(store.claim(link(b,"456"),true));
            assertFalse(store.claim(link(b,"789"),true));
        }
    }
    @Test void disabledEventsAndExistingAccountsCannotBecomeRetroactiveRewards()throws Exception{
        try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){
            var link=link(UUID.randomUUID(),"123");
            assertFalse(store.claim(link,false));assertFalse(store.claim(link,true));
        }
    }
    @Test void durableClaimSurvivesRestartAndBecomesUnknownWithoutReplay()throws Exception{
        Path path=temp.resolve("rewards.db");var link=link(UUID.randomUUID(),"123");
        try(var store=new LinkRewardStore(path)){assertTrue(store.claim(link,true));assertEquals("CLAIMED",store.status(link.uuid()));}
        try(var store=new LinkRewardStore(path)){assertEquals("UNKNOWN",store.status(link.uuid()));assertFalse(store.claim(link,true));}
    }
    @Test void previewNeverClaimsOrExecutes()throws Exception{
        try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){
            var link=link(UUID.randomUUID(),"123");assertTrue(store.eligible(link));assertTrue(store.eligible(link));assertTrue(store.claim(link,true));assertFalse(store.eligible(link));
        }
    }
    @Test void settingsDefaultDisabledAndRejectInjection(){
        var defaults=LinkRewardSettings.read(Map.of(),Map.of());assertFalse(defaults.enabled());assertFalse(defaults.welcomeMinecraft());assertFalse(defaults.welcomeDiscord());
        for(String command:List.of("op {player}","minecraft:op {uuid}","money give {discord} 1","money give {player}\nstop","money give {player} {x}"))
            assertThrows(IllegalArgumentException.class,()->LinkRewardSettings.read(Map.of(),Map.of("link-rewards",Map.of("commands",List.of(command)))));
    }
    @Test void startupBaselinePermanentlyExcludesExistingLinks()throws Exception{
        var original=link(UUID.randomUUID(),"123456789012345678");
        try(var accounts=new Store(temp.resolve("accounts.db"));var rewards=new LinkRewardStore(temp.resolve("rewards.db"))){
            accounts.link(original.uuid(),original.discord(),original.name(),"DiscordName",original.linkedAt(),()->true);
            rewards.seedExisting(accounts);assertFalse(rewards.eligible(original));assertFalse(rewards.claim(original,true));
            assertEquals("NONE",rewards.status(original.uuid()));
        }
    }
    @Test void unsupportedSchemaIsRejectedWithoutReset()throws Exception{
        Path path=temp.resolve("unsupported.db");Class.forName("org.sqlite.JDBC");
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path);var statement=db.createStatement()){
            statement.execute("CREATE TABLE schema_version(version INTEGER NOT NULL)");statement.execute("INSERT INTO schema_version VALUES(2)");
        }
        assertThrows(java.sql.SQLException.class,()->new LinkRewardStore(path));
        try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+path);var statement=db.createStatement();var result=statement.executeQuery("SELECT version FROM schema_version")){assertTrue(result.next());assertEquals(2,result.getInt(1));}
    }    @Test void nullCommandListIsAnInvalidSetting(){
        Map<String,Object> reward=new HashMap<>();reward.put("commands",null);
        assertThrows(IllegalArgumentException.class,()->LinkRewardSettings.read(Map.of(),Map.of("link-rewards",reward)));
    }}
