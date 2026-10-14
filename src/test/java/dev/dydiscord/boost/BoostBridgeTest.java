package dev.dydiscord.boost;

import dev.dydiscord.config.Settings;
import dev.dydiscord.storage.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class BoostBridgeTest {
    @TempDir Path dir;
    final Instant now=Instant.parse("2026-10-12T12:00:00Z");
    final String discord="123456789012345678",guild="123456789012345679";
    class Host implements BoostBridge.Host {
        Settings settings=make(true,List.of("money give {player} 100","credit add {uuid} 10"),List.of("rank remove {player} booster"));
        Store.Link link=new Store.Link(UUID.randomUUID(),discord,"Player",now,"version");
        long generation=1,botGeneration=1;boolean unknown,throwCommand,changePolicy;
        Runnable dispatchHook=()->{};
        BoostBridge.Membership member=new BoostBridge.Membership(true,false,now.minusSeconds(3600));
        List<String> commands=new ArrayList<>();
        public Settings settings(){return settings;}
        public long botGeneration(){return botGeneration;}
        public boolean ready(){return true;}
        public BoostBridge.Source link(String id){return new BoostBridge.Source(link,generation);}
        public long linkGeneration(){return generation;}
        public List<Store.Link> linkedAfter(String cursor,int limit){return link!=null&&link.uuid().toString().compareTo(cursor)>0?List.of(link):List.of();}
        public BoostBridge.Membership freshMembership(String id)throws Exception{if(unknown)throw new Exception("unavailable");return member;}
        public CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard){
            dispatchHook.run();
            if(changePolicy)generation++;
            if(!guard.getAsBoolean())return CompletableFuture.completedFuture(false);
            commands.add(command);
            return throwCommand?CompletableFuture.failedFuture(new Exception("uncertain")):CompletableFuture.completedFuture(true);
        }
        public void warning(String message){}
    }
    Settings make(boolean enabled,List<String> commands,List<String> removal){return Settings.read(Map.of(),Map.of("guild-id",guild,"boost-rewards",Map.of("enabled",enabled,"commands",commands,"remove-commands",removal)),null);}
    @Test void existingBoosterEligibleOnceAndUsesOnlyLinkedMinecraftValues()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            b.reconcile(discord);b.reconcile(discord);assertEquals(List.of("money give Player 100","credit add "+h.link.uuid()+" 10"),h.commands);
        }
    }
    @Test void unavailableMembershipDoesNotMeanUnboostOrConsumeReceipt()throws Exception{
        var h=new Host();h.unknown=true;
        try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            b.reconcile(discord);assertTrue(s.receipts(guild,discord).isEmpty());h.unknown=false;b.reconcile(discord);h.unknown=true;b.reconcile(discord);assertEquals(2,h.commands.size());
        }
    }
    @Test void unlinkRemovalUsesPersistedMinecraftIdentityOnce()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            UUID uuid=h.link.uuid();
            b.reconcile(discord);h.link=null;h.generation++;b.reconcile(discord);b.reconcile(discord);
            assertEquals(List.of("money give Player 100","credit add "+uuid+" 10","rank remove Player booster"),h.commands);
        }
    }
    @Test void unboostRemovesOptionalRankOnce()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            b.reconcile(discord);h.member=new BoostBridge.Membership(true,false,null);b.reconcile(discord);b.reconcile(discord);assertEquals(3,h.commands.size());
        }
    }
    @Test void partiallyFailedBatchNeverReplaysAnyCommand()throws Exception{
        var h=new Host();h.throwCommand=true;
        try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            b.reconcile(discord);h.throwCommand=false;b.reconcile(discord);assertEquals(1,h.commands.size());assertEquals("UNKNOWN",s.receipts(guild,discord).get(0).status());
        }
    }
    @Test void mainThreadLinkGenerationGuardRejectsStaleDelivery()throws Exception{
        var h=new Host();h.changePolicy=true;try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){b.reconcile(discord);assertTrue(h.commands.isEmpty());}
    }
    @Test void disabledOrEmptyConfigurationDoesNotDispatchOrClaim()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            h.settings=make(false,List.of("money give {player} 1"),List.of());b.reconcile(discord);
            h.settings=make(true,List.of(),List.of());b.reconcile(discord);assertTrue(h.commands.isEmpty());assertTrue(s.receipts(guild,discord).isEmpty());
        }
    }
    @Test void botsUnlinkedMembersAndUnsafeNamesAreIneligible()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            h.member=new BoostBridge.Membership(true,true,now);b.reconcile(discord);
            h.member=new BoostBridge.Membership(true,false,now);h.link=new Store.Link(UUID.randomUUID(),discord,"Player op Other",now,"v");b.reconcile(discord);
            assertTrue(h.commands.isEmpty());assertTrue(s.receipts(guild,discord).isEmpty());
        }
    }
    @Test void signalQueueCoalescesAndBoundsMemory()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            for(int i=0;i<1000;i++)b.signal(String.valueOf(123456789012345678L+i));
            assertEquals(256,b.pending());b.signal(discord);assertEquals(256,b.pending());
        }
    }
    @Test void memberEventDuringMainSchedulingInvalidatesFreshLookup()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            h.dispatchHook=()->b.signal(discord);b.reconcile(discord);assertTrue(h.commands.isEmpty());
        }
    }
    @Test void unrelatedMemberEventCannotConsumeCurrentActorsRewardWithoutDispatch()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            h.dispatchHook=()->b.signal("123456789012345680");b.reconcile(discord);
            assertEquals(List.of("money give Player 100","credit add "+h.link.uuid()+" 10"),h.commands);
            assertEquals("DISPATCHED",s.receipts(guild,discord).get(0).status());
        }
    }
    @Test void unrelatedLinkGenerationCannotConsumeCurrentActorsRewardWithoutDispatch()throws Exception{
        var h=new Host(){public long linkGeneration(String id){return 1;}};
        try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            h.dispatchHook=()->h.generation++;b.reconcile(discord);
            assertEquals(List.of("money give Player 100","credit add "+h.link.uuid()+" 10"),h.commands);
            assertEquals("DISPATCHED",s.receipts(guild,discord).get(0).status());
        }
    }
    @Test void restartAfterPartialDispatchNeverRepeatsGrant()throws Exception{
        var h=new Host();h.throwCommand=true;Path file=dir.resolve("boosts.db");
        try(var s=new BoostStore(file);var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){b.reconcile(discord);}
        h.throwCommand=false;
        try(var s=new BoostStore(file);var b=new BoostBridge(h,s,Clock.fixed(now.plusSeconds(700000),ZoneOffset.UTC))){b.reconcile(discord);assertEquals(1,h.commands.size());}
    }
    @Test void relinkingDifferentUuidDoesNotFarmSamePeriod()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            UUID uuid=h.link.uuid();
            b.reconcile(discord);h.link=new Store.Link(UUID.randomUUID(),discord,"Other",now,"other");h.generation++;
            b.reconcile(discord);assertEquals(List.of("money give Player 100","credit add "+uuid+" 10","rank remove Player booster"),h.commands);
        }
    }
    @Test void periodicSweepIncludesUnlinkedReceiptTombstones()throws Exception{
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){
            b.reconcile(discord);h.link=null;h.generation++;b.tick();assertEquals(3,h.commands.size());
        }
    }
    @Test void failedReceiptDatabaseCannotDispatch()throws Exception{
        var h=new Host();var s=new BoostStore(dir.resolve("boosts.db"));s.close();
        try(var b=new BoostBridge(h,s,Clock.fixed(now,ZoneOffset.UTC))){b.tick();assertTrue(h.commands.isEmpty());assertFalse(b.healthy());}
    }
    @Test void expiredFreshMembershipCannotDeliverQueuedMainCommand()throws Exception{
        var time=new AtomicReference<>(now);
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time.get();}};
        var h=new Host();try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,clock)){
            h.dispatchHook=()->time.set(now.plusSeconds(6));b.reconcile(discord);assertTrue(h.commands.isEmpty());assertEquals("UNKNOWN",s.receipts(guild,discord).get(0).status());
        }
    }
    @Test void enablingRemovalLaterCannotRemoveCurrentBoosterBenefitsFromAnOldPeriod()throws Exception{
        var time=new AtomicReference<>(now);
        Clock clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return time.get();}};
        var h=new Host();h.settings=make(true,List.of("rank add {player} booster"),List.of());
        try(var s=new BoostStore(dir.resolve("boosts.db"));var b=new BoostBridge(h,s,clock)){
            b.reconcile(discord);time.set(now.plusSeconds(604800));h.member=new BoostBridge.Membership(true,false,time.get());b.reconcile(discord);
            h.settings=make(true,List.of("rank add {player} booster"),List.of("rank remove {player} booster"));b.reconcile(discord);
            assertEquals(List.of("rank add Player booster","rank add Player booster"),h.commands);
        }
    }
}
