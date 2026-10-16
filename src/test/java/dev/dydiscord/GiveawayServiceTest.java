package dev.dydiscord;
import dev.dydiscord.giveaway.*;
import dev.dydiscord.config.GiveawaySettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class GiveawayServiceTest {
 @TempDir Path dir;
 static final String G="12345678901234567",C="22345678901234567",M="32345678901234567",U="42345678901234567",V="52345678901234567";
 static class Host implements GiveawayHost {
  Context context=new Context(true,1,G,new GiveawaySettings(true,C,false,Set.of())); boolean unknown,failUpdate,changeSession; int edits; List<GiveawayStore.Winner> result;
  public Context context(){return context;}
  public Member member(String guild,String discord)throws Exception {if(unknown)throw new java.io.IOException("network");if(changeSession)context=new Context(true,2,G,context.settings());return new Member(true,false,"@everyone *Alex*",Set.of());}
  public boolean linked(String id){return true;}
  public String publish(GiveawayStore.Giveaway g,java.util.function.BooleanSupplier check){assertTrue(check.getAsBoolean());return M;}
  public void update(GiveawayStore.Giveaway g,List<GiveawayStore.Winner> winners,java.util.function.BooleanSupplier check)throws Exception{assertTrue(check.getAsBoolean());edits++;if(failUpdate)throw new java.io.IOException("network");result=winners;}
  public void warning(){}
 }
 @Test void unknownMembershipDelaysDrawAndAnnouncementFailureNeverRerolls()throws Exception {
  var host=new Host();var store=new GiveawayStore(dir.resolve("g.db"));var clock=Clock.fixed(Instant.ofEpochMilli(1000),ZoneOffset.UTC);
  try(var service=new GiveawayService(store,host,clock,new SecureRandom())){
   String id=service.start(60,1,"Prize").get();
   assertEquals(GiveawayService.EntryResult.JOINED,service.enter(id,G,C,M,U,1).get());
   assertTrue(service.end(id).get());host.unknown=true;service.poll().get();assertEquals("ACTIVE",store.find(id).orElseThrow().state());
   host.unknown=false;host.failUpdate=true;service.refresh();service.poll().get();assertEquals("DRAWN",store.find(id).orElseThrow().state());var saved=store.winners(id);
   assertEquals(1,saved.size());host.failUpdate=false;service.refresh();service.poll().get();assertEquals(saved,host.result);assertTrue(store.find(id).orElseThrow().announced());
  }
 }
 @Test void forgedMessageAndSessionChangedDuringRestCannotEnter()throws Exception {
  var host=new Host();var store=new GiveawayStore(dir.resolve("g.db"));
  try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   String id=service.start(60,1,"Prize").get();
   assertEquals(GiveawayService.EntryResult.UNAVAILABLE,service.enter(id,G,C,"62345678901234567",U,1).get());
   host.changeSession=true;assertEquals(GiveawayService.EntryResult.UNAVAILABLE,service.enter(id,G,C,M,U,1).get());assertTrue(store.entrants(id).isEmpty());
  }
 }
 @Test void botsAndRoleOrLinkFailuresAreExcludedWithoutPublicAccountDetails()throws Exception {
  var host=new Host(){public Member member(String g,String u){return new Member(true,true,"Bot",Set.of());}};
  var store=new GiveawayStore(dir.resolve("g.db"));try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   String id=service.start(60,1,"Prize").get();assertEquals(GiveawayService.EntryResult.INELIGIBLE,service.enter(id,G,C,M,U,1).get());
  }
 }
 @Test void roleAndLinkAreRecheckedForWinnersAndNoLongerEligibleCannotWin()throws Exception {
  var host=new Host(){int checks;public Member member(String guild,String discord){checks++;return new Member(true,false,"Alex",checks==1?Set.of("72345678901234567"):Set.of());}};
  host.context=new GiveawayHost.Context(true,1,G,new GiveawaySettings(true,C,true,Set.of("72345678901234567")));
  var store=new GiveawayStore(dir.resolve("recheck.db"));try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   String id=service.start(60,1,"Prize").get();assertEquals(GiveawayService.EntryResult.JOINED,service.enter(id,G,C,M,U,1).get());service.end(id).get();service.poll().get();assertTrue(store.winners(id).isEmpty());assertEquals("DRAWN",store.find(id).orElseThrow().state());
  }
 }
 @Test void restartResumesTimedDrawAndPendingResultWithoutNewPublish()throws Exception {
  Path db=dir.resolve("resume.db");String id;try(var store=new GiveawayStore(db)){var g=store.create(G,C,60,1,"Prize",false,Set.of(),0);id=g.id();store.activate(id,M);store.enter(id,U,1);}
  var host=new Host(){public String publish(GiveawayStore.Giveaway g,java.util.function.BooleanSupplier c){fail("restart must not publish a second giveaway");return "";}};
  try(var service=new GiveawayService(new GiveawayStore(db),host,Clock.fixed(Instant.ofEpochMilli(61000),ZoneOffset.UTC),new SecureRandom())){service.poll().get();assertEquals(1,host.result.size());assertEquals(U,host.result.get(0).discord());}
  var second=new Host();try(var service=new GiveawayService(new GiveawayStore(db),second,Clock.systemUTC(),new SecureRandom())){service.poll().get();assertEquals(0,second.edits);}
 }
 @Test void sessionChangeDuringPublicationNeverActivatesOrResendsDraft()throws Exception {
  var host=new Host(){public String publish(GiveawayStore.Giveaway g,java.util.function.BooleanSupplier c){context=new Context(true,2,G,context.settings());return M;}};
  var store=new GiveawayStore(dir.resolve("draft.db"));try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   assertThrows(ExecutionException.class,()->service.start(60,1,"Prize").get());assertEquals("DRAFT",store.list().get(0).state());service.poll().get();assertEquals(0,host.edits);
  }
 }
 @Test void winnerRemovedAfterSelectionMustNotBeCommitted()throws Exception {
  var host=new Host(){int checks;public Member member(String guild,String discord){checks++;return new Member(checks<3,false,"Alex",Set.of());}};
  var store=new GiveawayStore(dir.resolve("leave.db"));try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   String id=service.start(60,1,"Prize").get();service.enter(id,G,C,M,U,1).get();service.end(id).get();service.poll().get();assertTrue(store.winners(id).isEmpty());
  }
 } @Test void oldChannelPendingResultsCannotStarveCurrentResult()throws Exception {
  var store=new GiveawayStore(dir.resolve("channels.db"));
  for(int i=0;i<60;i++){var old=store.create(G,"62345678901234567",60,1,"Old",false,Set.of(),0);store.activate(old.id(),M);store.commitDraw(old.id(),List.of());}
  var current=store.create(G,C,60,1,"Current",false,Set.of(),1000);store.activate(current.id(),M);store.commitDraw(current.id(),List.of(new GiveawayStore.Winner(U,"Alex")));
  var host=new Host();try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){service.poll().get();assertEquals(1,host.edits);assertTrue(store.find(current.id()).orElseThrow().announced());}
 } @Test void queuedStartCannotMoveToAReplacementBotSession()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  var host=new Host(){public Member member(String guild,String discord)throws Exception{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("test deadline");return super.member(guild,discord);}};
  var store=new GiveawayStore(dir.resolve("queued.db"));try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   String first=service.start(60,1,"First").get(5,TimeUnit.SECONDS);var busy=service.enter(first,G,C,M,U,1);assertTrue(entered.await(5,TimeUnit.SECONDS));var queued=service.start(60,1,"Old request");host.context=new GiveawayHost.Context(true,2,G,host.context.settings());release.countDown();busy.get(5,TimeUnit.SECONDS);assertThrows(ExecutionException.class,()->queued.get(5,TimeUnit.SECONDS));assertEquals(1,store.list().size());
  }finally{release.countDown();}
 } @Test void repeatedOldDeliveryFailuresCannotHideNewResultsInTheSameChannel()throws Exception{
  Path file=dir.resolve("delivery-pages.db");var store=new GiveawayStore(file);
  try(var db=java.sql.DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var change=db.prepareStatement("UPDATE giveaways SET id=? WHERE id=?")){
   for(int i=1;i<=60;i++){var old=store.create(G,C,60,1,"Old",false,Set.of(),0);store.activate(old.id(),M);store.commitDraw(old.id(),List.of());change.setString(1,String.format("%032x",i));change.setString(2,old.id());change.executeUpdate();}
   var latest=store.create(G,C,60,1,"Current",false,Set.of(),0);store.activate(latest.id(),M);change.setString(1,"f".repeat(32));change.setString(2,latest.id());change.executeUpdate();store.commitDraw("f".repeat(32),List.of(new GiveawayStore.Winner(U,"Alex")));
  }
  var host=new Host(){public void update(GiveawayStore.Giveaway g,List<GiveawayStore.Winner> winners,java.util.function.BooleanSupplier check)throws Exception{if(g.prize().equals("Old"))throw new java.io.IOException("missing old message");super.update(g,winners,check);}};
  try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){service.poll().get();service.poll().get();assertEquals(1,host.edits);assertTrue(store.find("f".repeat(32)).orElseThrow().announced());}
 }}
