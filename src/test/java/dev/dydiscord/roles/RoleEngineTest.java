package dev.dydiscord.roles;

import dev.dydiscord.config.*;
import dev.dydiscord.storage.Store;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class RoleEngineTest {
    @TempDir Path folder;
    static final String G="123456789012345678",D="223456789012345678",D2="423456789012345678",R="323456789012345678",V="523456789012345678",OTHER="623456789012345678";
    static class Time extends Clock{Instant now=Instant.parse("2026-10-12T00:00:00Z");public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}}
    Store store;RoleEngine engine;Time clock=new Time();
    AtomicReference<Settings> settings=new AtomicReference<>();Fake transport=new Fake();Set<String> groups=new HashSet<>();boolean known=true;Instant expiry=Instant.MAX;
    UUID uuid=UUID.randomUUID();
    Settings cfg(boolean enabled,Set<String> linked,List<Map<String,Object>> rules){return Settings.read(Map.of(),Map.of("guild-id",G,"roles",Map.of("enabled",enabled,"linked-role-ids",new ArrayList<>(linked),"groups",rules)),null);}
    Map<String,Object> rule(String group,String role,int p){return Map.of("group",group,"role-ids",List.of(role),"priority",p);}
    @BeforeEach void setup()throws Exception{
        store=new Store(folder.resolve("db"));store.link(uuid,D,"Alice",clock.instant());settings.set(cfg(true,Set.of(R),List.of()));
        GroupProvider provider=new GroupProvider(){public Result groups(UUID id,Map<String,String> c){return known?Result.known(Set.copyOf(groups),expiry):Result.unknown("unavailable");}public boolean available(){return known;}};
        engine=new RoleEngine(store,settings::get,provider,transport,clock,e->{throw new AssertionError(e);});
    }
    @AfterEach void close()throws Exception{engine.close();store.close();}
    void run()throws Exception{for(int i=0;i<4;i++)engine.runOnce();}
    @Test void linkedRoleWorksWithoutLuckPermsAndKeepsUnmanagedRoles()throws Exception{known=false;transport.roles.put(D,new HashSet<>(Set.of(OTHER)));run();assertEquals(Set.of(R,OTHER),transport.roles.get(D));int n=transport.changes;engine.enqueue(uuid);run();assertEquals(n,transport.changes);}
    @Test void groupRolesUseUnionAndSharedRoleStaysUntilLastRuleStopsMatching()throws Exception{
        settings.set(cfg(true,Set.of(R),List.of(rule("vip",V,1),rule("mvp",V,2))));groups.addAll(Set.of("vip","mvp"));run();assertEquals(Set.of(R,V),transport.roles.get(D));
        groups.remove("vip");engine.enqueue(uuid);run();assertTrue(transport.roles.get(D).contains(V));groups.clear();engine.enqueue(uuid);run();assertEquals(Set.of(R),transport.roles.get(D));
    }
    @Test void failedMembershipQueryPreservesRankRolesWhileStillGrantingLinkedRole()throws Exception{settings.set(cfg(true,Set.of(R),List.of(rule("vip",V,1))));known=false;transport.roles.put(D,new HashSet<>(Set.of(V)));run();assertEquals(Set.of(R,V),transport.roles.get(D));assertEquals(1L,engine.counts().get("PENDING"));}
    @Test void unlinkAndRelinkKeepOldDiscordCleanupDurable()throws Exception{
        run();var old=store.byUuid(uuid).orElseThrow();assertTrue(store.unlink(old));store.link(uuid,D2,"Alice",clock.instant());engine.enqueue(uuid);run();assertFalse(transport.roles.get(D).contains(R));assertTrue(transport.roles.get(D2).contains(R));
    }
    @Test void aDelayedTransportRequestCannotGrantAfterUnlink()throws Exception{
        transport.beforeChange=()->{try{store.unlink(store.byUuid(uuid).orElseThrow());}catch(Exception e){throw new RuntimeException(e);}};run();assertFalse(transport.roles.getOrDefault(D,Set.of()).contains(R));
    }
    @Test void ruleRemovalRetainsHistoryForRestartCleanup()throws Exception{
        settings.set(cfg(true,Set.of(R),List.of(rule("vip",V,1))));groups.add("vip");run();assertTrue(transport.roles.get(D).contains(V));settings.set(cfg(true,Set.of(R),List.of()));engine.refresh();run();assertEquals(Set.of(R),transport.roles.get(D));assertTrue(store.roleHistory(G).contains(V));
    }
    @Test void disabledDoesNotRemoveRolesButExplicitCleanupDoes()throws Exception{
        run();settings.set(cfg(false,Set.of(R),List.of()));engine.refresh();run();assertTrue(transport.roles.get(D).contains(R));assertEquals(1,engine.cleanupPreview().accounts());engine.startCleanup();run();assertFalse(transport.roles.get(D).contains(R));
    }
    @Test void memberCacheAbsenceIsNotAConfirmedDeparture()throws Exception{transport.unavailable=true;run();assertTrue(transport.roles.isEmpty());assertEquals(1L,engine.counts().get("PENDING"));}
    @Test void dangerousRoleAndConsoleRoleNeverReceiveGrants()throws Exception{
        transport.permission=1L<<3;run();assertEquals(0,transport.changes);assertEquals(1L,engine.counts().get("FAILED"));
        var bad=Settings.read(Map.of(),Map.of("guild-id",G,"console",Map.of("commands",Map.of("allowed-role-ids",List.of(R)))),null);assertThrows(RolePolicy.Blocked.class,()->RolePolicy.validate(transport.inspect(G,D),Set.of(R),bad.roles(),G));
    }
    @Test void policyChangeDuringInspectionStopsOldJob()throws Exception{transport.beforeInspect=()->settings.set(cfg(false,Set.of(R),List.of()));run();assertEquals(0,transport.changes);}
    @Test void permissionEventInvalidatesMembershipAlreadyCalculatedBeforeRequest()throws Exception{
        settings.set(cfg(true,Set.of(R),List.of(rule("vip",V,1))));groups.add("vip");
        transport.beforeChange=()->{groups.clear();engine.enqueue(uuid);};engine.runOnce();assertFalse(transport.roles.getOrDefault(D,Set.of()).contains(V));
    }
    @Test void enabledSyncSupersedesOldCleanupOutsideFirstPage()throws Exception{
        for(int i=0;i<25;i++)store.link(new UUID(0,i+1),"723456789012345"+String.format("%03d",i),"Other",clock.instant());
        store.roleRemember(G,Set.of(R));store.roleQueue(G,D,clock.instant(),true,0);transport.roles.put(D,new HashSet<>(Set.of(R)));
        engine.runOnce();assertTrue(transport.roles.get(D).contains(R));
    }
    @Test void cleanupIntentAndAllRemainingPagesSurviveRestart()throws Exception{
        for(int i=0;i<25;i++){String dc="723456789012345"+String.format("%03d",i);store.link(new UUID(0,i+1),dc,"Other",clock.instant());transport.roles.put(dc,new HashSet<>(Set.of(R)));}
        store.roleRemember(G,Set.of(R));transport.roles.put(D,new HashSet<>(Set.of(R)));settings.set(cfg(false,Set.of(R),List.of()));engine.startCleanup();engine.close();
        engine=new RoleEngine(store,settings::get,new GroupProvider(){public Result groups(UUID id,Map<String,String> c){return Result.unknown("absent");}public boolean available(){return false;}},transport,clock,e->{});
        for(int i=0;i<80;i++)engine.runOnce();assertTrue(transport.roles.values().stream().noneMatch(r->r.contains(R)));assertTrue(store.roleHistory(G).isEmpty());
    }
    @Test void previewAndRemovalBothRejectRetiredCriticalRole()throws Exception{
        store.roleRemember(G,Set.of(V));settings.set(cfg(true,Set.of(),List.of()));transport.roles.put(D,new HashSet<>(Set.of(V)));transport.permission=1L<<3;
        assertThrows(RolePolicy.Blocked.class,()->engine.preview(uuid));engine.runOnce();assertTrue(transport.roles.get(D).contains(V));
    }
    @Test void temporaryRankExpiresWhileAccountRemainsOfflineWithoutNewEvents()throws Exception{
        settings.set(cfg(true,Set.of(R),List.of(rule("vip",V,1))));groups.add("vip");expiry=clock.instant().plusSeconds(5);run();assertTrue(transport.roles.get(D).contains(V));
        clock.now=expiry.plusSeconds(1);groups.clear();expiry=Instant.MAX;engine.runOnce();assertEquals(Set.of(R),transport.roles.get(D));
    }
    @Test void highestUsesExplicitRulePriorityRatherThanDiscordRoleOrder()throws Exception{
        settings.set(Settings.read(Map.of(),Map.of("guild-id",G,"roles",Map.of("enabled",true,"linked-role-ids",List.of(R),"selection","highest","groups",List.of(rule("vip",V,1),rule("mvp",OTHER,10)))),null));groups.addAll(Set.of("vip","mvp"));run();assertEquals(Set.of(R,OTHER),transport.roles.get(D));
    }
    @Test void queueSignalsAreCappedAndUseOneCoalescedFallbackSweep()throws Exception{for(int i=0;i<1000;i++)engine.enqueue(new UUID(0,i));assertEquals(256,engine.queuedSignals());run();assertTrue(engine.queuedSignals()<=256);}
    @Test void globalInheritanceChangeIsNotStarvedBehindOneUnknownAccount()throws Exception{
        settings.set(cfg(true,Set.of(R),List.of(rule("vip",V,1))));known=false;run();assertEquals(1L,engine.counts().get("PENDING"));
        known=true;groups.add("vip");engine.requestSweep();run();assertTrue(transport.roles.get(D).contains(V));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"22,false","23,false","24,false","27,false","22,true","23,true","24,true","27,true"})
    void moderationAndNicknameManagementRolesAreBlockedForGrantAndRemoval(int bit,boolean remove)throws Exception{
        transport.permission=1L<<bit;
        if(remove){store.roleRemember(G,Set.of(R));settings.set(cfg(true,Set.of(),List.of()));transport.roles.put(D,new HashSet<>(Set.of(R)));}
        run();assertEquals(0,transport.changes);assertEquals(1L,engine.counts().get("FAILED"));assertEquals(remove,transport.roles.getOrDefault(D,Set.of()).contains(R));
    }
    @Test void shutdownCompletesDiscardedQueuedWorkRatherThanLeavingItsFuturePending()throws Exception{
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        engine.work(()->{entered.countDown();try{release.await();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}return null;});
        assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));var queued=engine.work(()->7);
        try{engine.close();assertTrue(queued.isCompletedExceptionally(),"Shutdown must resolve work removed from the executor queue");}finally{release.countDown();}
    }
    @Test void oldGuildTargetsCannotStarveCurrentGuildRoleOperations()throws Exception{
        for(int i=0;i<12;i++)store.roleQueue("923456789012345678","723456789012345"+String.format("%03d",i),clock.instant().minusSeconds(10),false,0);
        run();assertTrue(transport.roles.getOrDefault(D,Set.of()).contains(R));assertEquals(12L,store.roleCounts("923456789012345678",0).get("QUEUED"));
    }
    @Test void providerSubscriptionFailureDoesNotPreventRoleWorkerOperations()throws Exception{
        engine.close();engine=new RoleEngine(store,settings::get,new GroupProvider(){public Result groups(UUID id,Map<String,String> context){return Result.unknown("offline");}public boolean available(){return false;}public void start(java.util.function.Consumer<UUID> dirty,Runnable sweep){throw new IllegalStateException("subscription failed");}},transport,clock,e->{});
        assertDoesNotThrow(engine::start);assertEquals(7,engine.work(()->7).get(2,java.util.concurrent.TimeUnit.SECONDS));
    }
    @Test void providerDisposalFailureStillClosesWorker()throws Exception{
        engine.close();engine=new RoleEngine(store,settings::get,new GroupProvider(){public Result groups(UUID id,Map<String,String> context){return Result.unknown("offline");}public boolean available(){return false;}public void close(){throw new IllegalStateException("dispose failed");}},transport,clock,e->{});
        assertDoesNotThrow(engine::close);assertThrows(java.util.concurrent.ExecutionException.class,()->engine.work(()->7).get(2,java.util.concurrent.TimeUnit.SECONDS));
    }
    static class Fake implements RoleTransport {
        Map<String,Set<String>> roles=new HashMap<>();int changes;long permission;boolean unavailable;Runnable beforeChange=()->{},beforeInspect=()->{};
        public boolean available(){return true;}public long session(){return 1;}public String guild(){return G;}
        public Member inspect(String guild,String dc)throws Exception{
            beforeInspect.run();if(unavailable)throw new IllegalStateException("Unknown member");
            Map<String,RolePolicy.Role> catalog=new HashMap<>();for(String id:List.of(R,V,OTHER))catalog.put(id,new RolePolicy.Role(id,G,1,permission,false));
            return new Member(true,false,Set.copyOf(roles.getOrDefault(dc,Set.of())),catalog,10,1L<<28);
        }
        public void change(String guild,String dc,String role,boolean add,BooleanSupplier valid){beforeChange.run();if(valid.getAsBoolean()){changes++;if(add)roles.computeIfAbsent(dc,k->new HashSet<>()).add(role);else roles.computeIfAbsent(dc,k->new HashSet<>()).remove(role);}}
        public void validate(Settings settings)throws Exception{RolePolicy.validate(inspect(G,D),settings.roleSync().managedIds(),settings.roles(),G);}
    }
}
