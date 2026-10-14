package dev.dydiscord.boost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BoostStoreTest {
    @TempDir Path dir;
    final Instant now=Instant.parse("2026-10-12T12:00:00Z");
    final UUID uuid=UUID.fromString("01234567-1234-1234-1234-012345678901");
    @Test void periodCannotBeClaimedTwiceEvenForAnotherLinkedAccount() throws Exception {
        try(var store=new BoostStore(dir.resolve("boosts.db"))){
            assertTrue(store.claim("guild","discord",now,uuid,"Player",now,168));
            assertFalse(store.claim("guild","discord",now,UUID.randomUUID(),"Other",now.plusSeconds(700000),168));
        }
    }
    @Test void cooldownAppliesAcrossDifferentPeriods() throws Exception {
        try(var store=new BoostStore(dir.resolve("boosts.db"))){
            assertTrue(store.claim("guild","discord",now,uuid,"Player",now,168));
            assertFalse(store.claim("guild","discord",now.plusSeconds(1),uuid,"Player",now.plusSeconds(604799),168));
            assertTrue(store.claim("guild","discord",now.plusSeconds(1),uuid,"Player",now.plusSeconds(604800),168));
        }
    }
    @Test void crashAfterClaimBecomesUnknownWithoutReplay() throws Exception {
        Path db=dir.resolve("boosts.db");
        try(var store=new BoostStore(db)){assertTrue(store.claim("g","d",now,uuid,"Player",now,168));}
        try(var store=new BoostStore(db)){
            assertEquals("UNKNOWN",store.receipts("g","d").get(0).status());
            assertFalse(store.claim("g","d",now,uuid,"Player",now.plusSeconds(999999),168));
        }
    }
    @Test void removalIsDurableAndCanOnlyBeAttemptedOnce() throws Exception {
        try(var store=new BoostStore(dir.resolve("boosts.db"))){
            store.claim("g","d",now,uuid,"Player",now,168);
            var receipt=store.receipts("g","d").get(0);
            assertTrue(store.claimRemoval(receipt));assertFalse(store.claimRemoval(receipt));
        }
    }
    @Test void futureSchemaRefusalPreservesDatabase() throws Exception {
        Path file=dir.resolve("boosts.db");
        try(var store=new BoostStore(file)){}
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var st=db.createStatement()){st.execute("UPDATE schema_version SET version=99");}
        assertThrows(SQLException.class,()->new BoostStore(file));
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var st=db.createStatement();var rs=st.executeQuery("SELECT version FROM schema_version")){assertTrue(rs.next());assertEquals(99,rs.getInt(1));}
    }
    @Test void closedDatabaseCannotClaimAnyReward() throws Exception {
        var store=new BoostStore(dir.resolve("boosts.db"));store.close();
        assertThrows(SQLException.class,()->store.claim("g","d",now,uuid,"Player",now,168));
    }
    @Test void receiptTombstonesArePagedForUnlinkedAccounts() throws Exception {
        try(var store=new BoostStore(dir.resolve("boosts.db"))){
            for(int i=0;i<25;i++)store.claim("g",String.format("%03d",i),now,uuid,"Player",now,168);
            var page=store.discordAfter("g","",20);assertEquals(20,page.size());
            assertEquals(5,store.discordAfter("g",page.get(19),20).size());
        }
    }
}
