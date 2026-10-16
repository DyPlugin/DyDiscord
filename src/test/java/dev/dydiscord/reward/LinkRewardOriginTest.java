package dev.dydiscord.reward;

import dev.dydiscord.storage.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LinkRewardOriginTest {
 @TempDir Path temp;
 private Store.Link link(Store store,UUID uuid,String discord)throws Exception{return store.link(uuid,discord,"Alice",Instant.now());}
 @Test void ledgerOutageThenUnlinkAndRebootCannotCreateFreshOrigin()throws Exception{
  Path file=temp.resolve("accounts.db");UUID uuid=UUID.randomUUID();String discord="123456789012345678";
  try(var store=new Store(file)){
   var first=link(store,uuid,discord);assertTrue(store.firstRewardEligible(first));assertTrue(store.unlink(first));
  }
  try(var store=new Store(file)){
   var relink=link(store,uuid,discord);assertFalse(store.firstRewardEligible(relink));assertTrue(store.unlink(relink));
   var changed=link(store,UUID.randomUUID(),discord);assertFalse(store.firstRewardEligible(changed));assertTrue(store.unlink(changed));
   assertFalse(store.firstRewardEligible(link(store,changed.uuid(),"223456789012345678")));
  }
 }
 @Test void revokedCommitRollsBackIdentityFactsAndOrigin()throws Exception{
  try(var store=new Store(temp.resolve("accounts.db"))){
   UUID uuid=UUID.randomUUID();String discord="123456789012345678";
   assertThrows(Store.Revoked.class,()->store.link(uuid,discord,"Alice",Instant.now(),()->false));
   assertTrue(store.byUuid(uuid).isEmpty());assertTrue(store.firstRewardEligible(link(store,uuid,discord)));
  }
 }
 @Test void schemaThreeUpgradeSeedsExistingAccountsWithoutEligibleOrigins()throws Exception{
  Path file=temp.resolve("accounts.db");Store.Link old;
  try(var store=new Store(file)){old=link(store,UUID.randomUUID(),"123456789012345678");}
  try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=db.createStatement()){
   statement.execute("DROP TABLE link_reward_origins");statement.execute("DROP TABLE link_reward_seen");
  }
  try(var store=new Store(file)){
   assertFalse(store.firstRewardEligible(old));assertTrue(store.unlink(old));assertFalse(store.firstRewardEligible(link(store,old.uuid(),old.discord())));
  }
 }
 @Test void observationWriteFailureRollsBackPermanentLink()throws Exception{
  Path file=temp.resolve("accounts.db");try(var store=new Store(file)){
   try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=db.createStatement()){
    statement.execute("CREATE TRIGGER reject_reward_facts BEFORE INSERT ON link_reward_seen BEGIN SELECT RAISE(ABORT,'blocked'); END");
   }
   UUID uuid=UUID.randomUUID();assertThrows(SQLException.class,()->link(store,uuid,"123456789012345678"));assertTrue(store.byUuid(uuid).isEmpty());
   try(var db=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=db.createStatement()){statement.execute("DROP TRIGGER reject_reward_facts");}
   assertTrue(store.firstRewardEligible(link(store,uuid,"123456789012345678")));
  }
 }
 @Test void competingIdentitiesHaveOneOriginAndRejectedTransactionConsumesNothing()throws Exception{
  Path file=temp.resolve("accounts.db");try(var one=new Store(file);var two=new Store(file)){
   UUID a=UUID.randomUUID(),b=UUID.randomUUID();CountDownLatch start=new CountDownLatch(1);ExecutorService threads=Executors.newFixedThreadPool(2);
   try{
    Future<Store.Link> first=threads.submit(()->{start.await();try{return link(one,a,"123456789012345678");}catch(SQLException expected){return null;}});
    Future<Store.Link> second=threads.submit(()->{start.await();try{return link(two,b,"123456789012345678");}catch(SQLException expected){return null;}});
    start.countDown();Store.Link x=first.get(5,TimeUnit.SECONDS),y=second.get(5,TimeUnit.SECONDS);assertNotEquals(x==null,y==null);
    Store.Link winner=x==null?y:x;assertTrue(one.firstRewardEligible(winner));
    UUID unused=x==null?a:b;assertTrue(one.firstRewardEligible(link(one,unused,"223456789012345678")));
   }finally{threads.shutdownNow();}
  }
 }
 @Test void schemaThreeObservationUpgradeBacksUpWalOnceBeforeMutation()throws Exception{
  Path file=temp.resolve("accounts.db");Store.Link old;
  try(var store=new Store(file)){old=link(store,UUID.randomUUID(),"123456789012345678");}
  try(var writer=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=writer.createStatement()){
   statement.execute("PRAGMA journal_mode=WAL");statement.execute("PRAGMA wal_autocheckpoint=0");
   statement.execute("DROP TABLE link_reward_origins");statement.execute("DROP TABLE link_reward_seen");
   statement.execute("UPDATE links SET name='FromWal'");
   try(var upgraded=new Store(file)){assertFalse(upgraded.firstRewardEligible(old));assertEquals("FromWal",upgraded.byUuid(old.uuid()).orElseThrow().name());}
   List<Path> backups;try(var files=java.nio.file.Files.list(temp)){backups=files.filter(path->path.getFileName().toString().startsWith("accounts.db.backup-reward-observations-")).toList();}
   assertEquals(1,backups.size());
   try(var saved=DriverManager.getConnection("jdbc:sqlite:"+backups.get(0));var check=saved.createStatement()){
    try(var rows=check.executeQuery("SELECT name,version FROM links")){assertTrue(rows.next());assertEquals("FromWal",rows.getString(1));assertEquals(old.version(),rows.getString(2));}
    try(var rows=check.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'link_reward_%'")){assertFalse(rows.next());}
   }
   try(var reopened=new Store(file)){assertFalse(reopened.firstRewardEligible(old));}
   try(var files=java.nio.file.Files.list(temp)){assertEquals(1,files.filter(path->path.getFileName().toString().startsWith("accounts.db.backup-reward-observations-")).count());}
  }
 }}
