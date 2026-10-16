package dev.dydiscord;
import dev.dydiscord.giveaway.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GiveawayTest {
 @TempDir Path dir;
 @Test void duplicateEntryAndRestartDoNotChangeCommittedWinners() throws Exception {
  Path file=dir.resolve("giveaways.db"); String id;
  try(var store=new GiveawayStore(file)){
   var g=store.create("12345678901234567","22345678901234567",60,2,"Prize",false,Set.of(),1000);
   id=g.id(); assertTrue(store.activate(id,"32345678901234567"));
   assertEquals(GiveawayStore.Entry.JOINED,store.enter(id,"42345678901234567",1001));
   assertEquals(GiveawayStore.Entry.ALREADY,store.enter(id,"42345678901234567",1002));
   assertTrue(store.commitDraw(id,List.of(new GiveawayStore.Winner("42345678901234567","Alex"))));
   assertFalse(store.commitDraw(id,List.of(new GiveawayStore.Winner("52345678901234567","Other"))));
  }
  try(var store=new GiveawayStore(file)){
   assertEquals(List.of(new GiveawayStore.Winner("42345678901234567","Alex")),store.winners(id));
   assertEquals("DRAWN",store.find(id).orElseThrow().state());
   assertEquals(1,store.pending().size());
  }
 }
 @Test void lateAndCancelledEntriesAreRefused() throws Exception {
  try(var store=new GiveawayStore(dir.resolve("g.db"))){
   var g=store.create("12345678901234567","22345678901234567",60,1,"Prize",false,Set.of(),1000);
   store.activate(g.id(),"32345678901234567");
   assertEquals(GiveawayStore.Entry.CLOSED,store.enter(g.id(),"42345678901234567",61000));
   assertTrue(store.cancel(g.id())); assertFalse(store.commitDraw(g.id(),List.of()));
   assertEquals(GiveawayStore.Entry.CLOSED,store.enter(g.id(),"42345678901234567",1001));
  }
 }
 @Test void requestBoundsAndChannelSeparationFailClosed() {
  assertThrows(IllegalArgumentException.class,()->GiveawayStore.validate(59,1,"Prize"));
  assertThrows(IllegalArgumentException.class,()->GiveawayStore.validate(2592001,1,"Prize"));
  assertThrows(IllegalArgumentException.class,()->GiveawayStore.validate(60,21,"Prize"));
  assertThrows(IllegalArgumentException.class,()->GiveawayStore.validate(60,1,"x".repeat(201)));
  assertThrows(IllegalArgumentException.class,()->GiveawayStore.validate(60,1,"Prize\nInjected"));
  assertThrows(IllegalArgumentException.class,()->dev.dydiscord.config.GiveawaySettings.read(Map.of("giveaways",Map.of("enabled",true))));
  assertThrows(IllegalArgumentException.class,()->dev.dydiscord.config.GiveawaySettings.read(Map.of("giveaways",Map.of("channel-id","12345678901234567"),"console",Map.of("channel-id","12345678901234567"))));
  assertFalse(dev.dydiscord.config.GiveawaySettings.read(Map.of()).enabled());
 }
 @Test void activeAndEntryLimitsSurviveRestartAndDuplicatesDoNotConsumeCapacity()throws Exception {
  Path file=dir.resolve("limits.db");String id;
  try(var store=new GiveawayStore(file)){
   var g=store.create("12345678901234567","22345678901234567",60,1,"Prize",false,Set.of(),1000);id=g.id();store.activate(id,"32345678901234567");
   for(int i=1;i<=10000;i++)assertEquals(GiveawayStore.Entry.JOINED,store.enter(id,Long.toString(40000000000000000L+i),1001));
   assertEquals(GiveawayStore.Entry.ALREADY,store.enter(id,"40000000000000001",1001));
   assertEquals(GiveawayStore.Entry.FULL,store.enter(id,"50000000000000001",1001));
   for(int i=1;i<20;i++)store.create("12345678901234567","22345678901234567",60,1,"Prize",false,Set.of(),1000);
   assertThrows(java.sql.SQLException.class,()->store.create("12345678901234567","22345678901234567",60,1,"Extra",false,Set.of(),1000));
  }
  try(var store=new GiveawayStore(file)){assertEquals(10000,store.entrants(id).size());assertTrue(store.cancel(id));assertDoesNotThrow(()->store.create("12345678901234567","22345678901234567",60,1,"Replacement",false,Set.of(),1000));}
 }
 @Test void duplicateWinnerRollsBackEntireDrawAndOriginalRemainsDrawable()throws Exception {
  try(var store=new GiveawayStore(dir.resolve("rollback.db"))){var g=store.create("12345678901234567","22345678901234567",60,2,"Prize",false,Set.of(),1000);store.activate(g.id(),"32345678901234567");var winner=new GiveawayStore.Winner("42345678901234567","Alex");
   assertThrows(java.sql.SQLException.class,()->store.commitDraw(g.id(),List.of(winner,winner)));assertEquals("ACTIVE",store.find(g.id()).orElseThrow().state());assertTrue(store.winners(g.id()).isEmpty());assertTrue(store.commitDraw(g.id(),List.of(winner)));
  }
 } @Test void adminListAlwaysIncludesOldActiveGiveawayBeforeRecentHistory()throws Exception {
  try(var store=new GiveawayStore(dir.resolve("history.db"))){var active=store.create("12345678901234567","22345678901234567",60,1,"Active",false,Set.of(),1000);store.activate(active.id(),"32345678901234567");
   for(int i=0;i<105;i++){var g=store.create("12345678901234567","22345678901234567",60,1,"History",false,Set.of(),1000);store.cancel(g.id());}
   assertEquals(active.id(),store.list().get(0).id());
  }
 } @Test void unknownOrIncompleteDatabaseIsRejectedWithoutChangingBytes()throws Exception {
  for(String ddl:List.of("CREATE TABLE other(secret TEXT)","CREATE TABLE giveaways(id TEXT PRIMARY KEY)","CREATE TABLE entries(giveaway TEXT,discord TEXT)")){
   Path db=dir.resolve(UUID.randomUUID()+".db");try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+db);var st=c.createStatement()){st.execute(ddl);}
   byte[] before=Files.readAllBytes(db);assertThrows(java.sql.SQLException.class,()->new GiveawayStore(db));assertArrayEquals(before,Files.readAllBytes(db));
  }
 }
 @Test void futureSchemaAndVersionedMissingColumnsAreRejectedWithoutWrites()throws Exception {
  Path future=dir.resolve("future.db");try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+future);var st=c.createStatement()){st.execute("PRAGMA user_version=2");st.execute("CREATE TABLE future(data TEXT)");}
  byte[] before=Files.readAllBytes(future);assertThrows(java.sql.SQLException.class,()->new GiveawayStore(future));assertArrayEquals(before,Files.readAllBytes(future));
  Path bad=dir.resolve("incomplete.db");try(var c=java.sql.DriverManager.getConnection("jdbc:sqlite:"+bad);var st=c.createStatement()){st.execute("PRAGMA user_version=1");st.execute("CREATE TABLE giveaways(id TEXT PRIMARY KEY)");st.execute("CREATE TABLE entries(giveaway TEXT,discord TEXT)");st.execute("CREATE TABLE winners(giveaway TEXT,position INTEGER,discord TEXT,name TEXT)");}
  before=Files.readAllBytes(bad);assertThrows(java.sql.SQLException.class,()->new GiveawayStore(bad));assertArrayEquals(before,Files.readAllBytes(bad));
 }}
