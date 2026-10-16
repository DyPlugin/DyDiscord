package dev.dydiscord.reward;
import dev.dydiscord.config.LinkRewardSettings;
import dev.dydiscord.storage.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
class LinkRewardServiceTest {
 @TempDir Path temp;
 private final Store.Link link=new Store.Link(UUID.randomUUID(),"123456789012345678","Alice",Instant.now(),"version");
 private class Host implements LinkRewardService.Host {
   LinkRewardSettings settings=new LinkRewardSettings(true,List.of("money give {player} 10"),false,false);
   long generation=1;int calls;boolean invalidate,fail;
   public LinkRewardSettings settings(){return settings;}public boolean ready(){return true;}public boolean mainThread(){return false;}
   public long session(){return 1;}public long linkGeneration(String discord){return generation;}
   public Store.LinkSnapshot snapshot(String discord){return new Store.LinkSnapshot(Optional.of(link),generation);}
   public CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard){calls++;if(invalidate)generation++;return CompletableFuture.completedFuture(!fail&&guard.getAsBoolean());}
   public void warning(String safe){}
 }
 @Test void dispatchesOnlyAfterDurableClaimAndNeverAgain()throws Exception{
   try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){Host host=new Host(){public CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard){try{assertEquals("CLAIMED",store.status(link.uuid()));}catch(Exception e){throw new RuntimeException(e);}return super.dispatch(command,guard);}};
    try(var service=new LinkRewardService(host,store,Clock.systemUTC())){
     assertEquals(LinkRewardService.Result.DISPATCHED,service.afterLinked(link).get(3,TimeUnit.SECONDS));
     assertEquals(LinkRewardService.Result.SKIPPED,service.afterLinked(link).get());assertEquals(1,host.calls);
    }
   }
 }
 @Test void staleMainThreadGuardConsumesClaimWithoutRetry()throws Exception{
  try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){Host host=new Host();host.invalidate=true;
   try(var service=new LinkRewardService(host,store,Clock.systemUTC())){
    assertEquals(LinkRewardService.Result.UNKNOWN,service.afterLinked(link).get(3,TimeUnit.SECONDS));assertEquals("UNKNOWN",store.status(link.uuid()));
    host.invalidate=false;assertEquals(LinkRewardService.Result.SKIPPED,service.afterLinked(link).get());
   }
  }
 }
 @Test void unsafePlayerNameNeverBecomesConsoleInput()throws Exception{
  try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){Host host=new Host();try(var service=new LinkRewardService(host,store,Clock.systemUTC())){
   var unsafe=new Store.Link(link.uuid(),link.discord(),"Alice\nstop",link.linkedAt(),link.version());
   assertEquals(LinkRewardService.Result.SKIPPED,service.afterLinked(unsafe).get());assertEquals(0,host.calls);
  }}
 }
 @Test void snapshotFailureAfterClaimIsUnknownImmediatelyAndNeverReplayed()throws Exception{
  try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){Host host=new Host(){public Store.LinkSnapshot snapshot(String discord){throw new IllegalStateException("lookup unavailable");}};
   try(var service=new LinkRewardService(host,store,Clock.systemUTC())){
    assertEquals(LinkRewardService.Result.UNKNOWN,service.afterLinked(link).get());assertEquals("UNKNOWN",store.status(link.uuid()));assertEquals(0,host.calls);
   }
  }
 }
 @Test void configAndSessionChangesInvalidateTheActualDispatchGuard()throws Exception{
  for(boolean changeSettings:List.of(false,true)){
   try(var store=new LinkRewardStore(temp.resolve("guard-"+changeSettings+".db"))){
    Host host=new Host(){long session=1;public long session(){return session;}public CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard){
     calls++;if(changeSettings)settings=new LinkRewardSettings(true,List.of("money give {player} 99"),false,false);else session++;
     return CompletableFuture.completedFuture(guard.getAsBoolean());
    }};
    try(var service=new LinkRewardService(host,store,Clock.systemUTC())){assertEquals(LinkRewardService.Result.UNKNOWN,service.afterLinked(link).get(3,TimeUnit.SECONDS));assertEquals(1,host.calls);}
   }
  }
 }
 @Test void rejectsDatabaseOperationsOnMinecraftMainThread()throws Exception{
  try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){Host host=new Host(){public boolean mainThread(){return true;}};
   try(var service=new LinkRewardService(host,store,Clock.systemUTC())){
    assertThrows(IllegalStateException.class,()->service.afterLinked(link));assertThrows(IllegalStateException.class,()->service.preview(link));assertTrue(store.eligible(link));
   }
  }
 }
 @Test void failedFirstCommandDoesNotRunLaterCommandsOrRetry()throws Exception{
  try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){Host host=new Host();host.fail=true;host.settings=new LinkRewardSettings(true,List.of("money give {player} 10","money give {uuid} 20"),false,false);
   try(var service=new LinkRewardService(host,store,Clock.systemUTC())){
    assertEquals(LinkRewardService.Result.UNKNOWN,service.afterLinked(link).get(3,TimeUnit.SECONDS));assertEquals(1,host.calls);
    assertEquals(LinkRewardService.Result.SKIPPED,service.afterLinked(link).get());assertEquals(1,host.calls);
   }
  }
 } @Test void primaryOriginRejectionStillReservesBothOptionalLedgerIdentities()throws Exception{
  try(var store=new LinkRewardStore(temp.resolve("rewards.db"))){Host host=new Host(){public boolean firstRewardEligible(Store.Link ignored){return false;}};
   try(var service=new LinkRewardService(host,store,Clock.systemUTC())){
    assertFalse(service.preview(link).eligible());assertEquals(LinkRewardService.Result.SKIPPED,service.afterLinked(link).get());
    assertFalse(store.eligible(link));assertEquals(0,host.calls);
    var differentCounterpart=new Store.Link(link.uuid(),"223456789012345678",link.name(),link.linkedAt(),"another-version");
    assertFalse(store.claim(differentCounterpart,true));
   }
  }
 }}
