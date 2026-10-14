package dev.dydiscord;

import dev.dydiscord.storage.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoleStoreTest {
    @Test void rewardIdentityGuardIgnoresUnrelatedAccountChangesButRejectsRelink()throws Exception{
        try(Store store=new Store(folder.resolve("reward-identities.db"))){var now=Instant.now();String first="823456789012345678",second="923456789012345678";UUID uuid=UUID.randomUUID();var original=store.link(uuid,first,"Alice",now);
            var observed=store.rewardSnapshot(first);store.link(UUID.randomUUID(),second,"Bob",now);assertEquals(observed.generation(),store.rewardGeneration(first));
            store.unlink(original);assertNotEquals(observed.generation(),store.rewardGeneration(first));
            store.link(uuid,first,"Alice",now);assertNotEquals(observed.generation(),store.rewardGeneration(first));
        }
    }
    @TempDir Path folder;
    final String guild="123456789012345678",dc="223456789012345678",role="323456789012345678";
    final Instant now=Instant.parse("2026-10-12T00:00:00Z");
    @Test void unlinkDurablyInvalidatesPendingRoleAddAndPreservesRemovalTarget() throws Exception {
        Store.Link link;Store.RoleTarget queued;
        Path file=folder.resolve("data.db");
        try(Store s=new Store(file)){
            link=s.link(UUID.randomUUID(),dc,"Alice",now);s.roleRemember(guild,Set.of(role));s.roleQueue(guild,dc,now,false,7);
            queued=s.roleNext(now,true).orElseThrow();assertTrue(s.unlink(link));
            assertFalse(s.roleFinish(queued,"SUCCESS",now,false));
        }
        try(Store s=new Store(file)){
            assertTrue(s.byDiscord(dc).isEmpty());assertEquals(Set.of(role),s.roleHistory(guild));
            var cleanup=s.roleNext(now,true).orElseThrow();assertEquals(dc,cleanup.discord());assertTrue(cleanup.revision()>queued.revision());
        }
    }
    @Test void dirtyRevisionRejectsObsoleteCompletionAndDisabledOnlyAllowsExplicitCleanup() throws Exception {
        try(Store s=new Store(folder.resolve("data.db"))){
            s.roleQueue(guild,dc,now,false,1);var first=s.roleNext(now,true).orElseThrow();assertTrue(s.roleNext(now,false).isEmpty());
            s.roleQueue(guild,dc,now,true,1);assertFalse(s.roleFinish(first,"SUCCESS",now,false));
            var explicit=s.roleNext(now,false).orElseThrow();assertTrue(explicit.cleanup());assertTrue(s.roleFinish(explicit,"SUCCESS",now,false));assertTrue(s.roleNext(now,true).isEmpty());
        }
    }
    @Test void keysetPagesAndProgressStayBounded() throws Exception {
        try(Store s=new Store(folder.resolve("data.db"))){
            for(int i=0;i<8;i++){String id="2234567890123456"+String.format("%02d",i);s.link(new UUID(0,i+1),id,"Player",now);s.roleQueue(guild,id,now,false,9);}
            var page=s.linksPage("",3);assertEquals(3,page.size());assertEquals(3,s.linksPage(page.get(2).uuid().toString(),3).size());
            assertEquals(8,s.roleCounts(guild,9).getOrDefault("QUEUED",0L));assertEquals(2,s.roleTargetsPage(guild,"",2).size());
        }
    }
    @Test void relinkAtomicallyRevokesAnExistingRemovalDecision()throws Exception{
        try(Store s=new Store(folder.resolve("relink.db"))){
            var first=s.link(UUID.randomUUID(),dc,"Alice",now);s.roleQueue(guild,dc,now,false,0);assertTrue(s.unlink(first));
            var removal=s.roleNext(now,true).orElseThrow();s.link(UUID.randomUUID(),dc,"Bob",now);
            assertFalse(s.roleFinish(removal,"SUCCESS",now,false));assertTrue(s.roleNext(now,true).isPresent());
        }
    }
    @Test void linkingSnapshotsInvalidateRewardsOnlyAfterCommittedIdentityChanges()throws Exception{
        try(Store s=new Store(folder.resolve("snapshot.db"))){
            long initial=s.snapshotByDiscord(dc).generation();UUID uuid=UUID.randomUUID();var linked=s.link(uuid,dc,"Alice",now);
            var snapshot=s.snapshotByDiscord(dc);assertEquals(linked,snapshot.link().orElseThrow());assertTrue(snapshot.generation()>initial);
            assertThrows(Store.Revoked.class,()->s.link(UUID.randomUUID(),"923456789012345678","Bob",now,()->false));assertEquals(snapshot.generation(),s.linkGeneration());
            s.updateName(uuid,"NewAlice");assertTrue(s.linkGeneration()>snapshot.generation());long renamed=s.linkGeneration();
            assertTrue(s.unlink(linked));assertTrue(s.linkGeneration()>renamed);long removed=s.linkGeneration();assertFalse(s.unlink(linked));assertEquals(removed,s.linkGeneration());assertTrue(s.snapshotByDiscord(dc).link().isEmpty());
        }
    }
    @Test void damagedCurrentRoleHistoryCannotBeSilentlyRecreated()throws Exception{
        Path file=folder.resolve("damaged.db");try(Store s=new Store(file)){s.roleRemember(guild,Set.of(role));}
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+file);Statement s=c.createStatement()){s.execute("DROP TABLE role_history");}
        assertThrows(SQLException.class,()->new Store(file));
    }
}
