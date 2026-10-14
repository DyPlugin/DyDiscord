package dev.dydiscord.roles;

import dev.dydiscord.config.*;
import dev.dydiscord.storage.Store;
import dev.dydiscord.util.PluginThreads;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** One member at a time; bounded RAM signals and durable keyset-paged reconciliation. */
public final class RoleEngine implements AutoCloseable {
    public record CleanupPreview(long accounts,Set<String> roles){public CleanupPreview{roles=Set.copyOf(roles);}}
    private final Store store;private final Supplier<Settings> settings;private volatile GroupProvider provider;
    private final RoleTransport transport;private final Clock clock;private final Consumer<Throwable> errors;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),PluginThreads.factory("DyDiscord-roles"),new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(PluginThreads.factory("DyDiscord-role-timer"));
    private final Set<String> signals=new LinkedHashSet<>();private final AtomicBoolean requested=new AtomicBoolean(true),tickQueued=new AtomicBoolean();
    private final Set<CompletableFuture<?>> pendingWork=ConcurrentHashMap.newKeySet();
    private final AtomicLong epoch=new AtomicLong();private volatile boolean closed,scanning;private Settings scanSettings;
    private String linkCursor="",targetCursor="";private boolean targetPhase,cleanupScan;private long batchCounter,currentBatch;private Instant nextSweep=Instant.MIN;
    private volatile String failure="";private Settings registered;
    public RoleEngine(Store store,Supplier<Settings> settings,GroupProvider provider,RoleTransport transport,Clock clock,Consumer<Throwable> errors){this.store=store;this.settings=settings;this.provider=provider;this.transport=transport;this.clock=clock;this.errors=errors;}
    public void start(){startProvider(provider);timer.scheduleWithFixedDelay(()->{
        if(!closed&&tickQueued.compareAndSet(false,true))work(()->{try{runOnce();}finally{tickQueued.set(false);}return null;}).exceptionally(e->{tickQueued.set(false);if(!closed)errors.accept(e);return null;});
    },0,1000,TimeUnit.MILLISECONDS);}
    public <T> CompletableFuture<T> work(Callable<T> action){
        CompletableFuture<T> result=new CompletableFuture<>();if(closed){result.completeExceptionally(new IllegalStateException("Role worker closed"));return result;}
        pendingWork.add(result);result.whenComplete((value,error)->pendingWork.remove(result));
        if(closed){result.completeExceptionally(new IllegalStateException("Role worker closed"));return result;}
        try{worker.execute(()->{try{if(closed)throw new IllegalStateException("Role worker closed");result.complete(action.call());}catch(Throwable e){result.completeExceptionally(e);}});}catch(RejectedExecutionException e){result.completeExceptionally(e);}return result;
    }
    public void provider(GroupProvider next){GroupProvider previous=provider;provider=next;closeProvider(previous);startProvider(next);refresh();}
    private void startProvider(GroupProvider value){try{value.start(this::enqueue,this::requestSweep);}catch(RuntimeException error){failure="provider-events-unavailable";errors.accept(error);}}
    private void closeProvider(GroupProvider value){try{value.close();}catch(RuntimeException error){errors.accept(error);}}
    public boolean providerAvailable(){return provider.available();}public long epoch(){return epoch.get();}
    public void refresh(){epoch.incrementAndGet();requested.set(true);}
    public void requestSweep(){epoch.incrementAndGet();requested.set(true);}
    public void enqueue(UUID uuid){signal("u:"+uuid);}
    public void enqueueMember(String discord){if(Settings.snowflake(discord))signal("d:"+discord);}
    private void signal(String value){epoch.incrementAndGet();synchronized(signals){if(signals.size()<256)signals.add(value);else requested.set(true);}}
    public int queuedSignals(){synchronized(signals){return signals.size();}}
    public boolean scanning(){return scanning;}
    public String failure(){return failure;}
    public Map<String,Long> counts()throws Exception{return store.roleCounts(settings.get().guild(),currentBatch);}
    public void validateSettings(Settings next)throws Exception{
        Set<String> managed=new HashSet<>(store.roleHistory(next.guild()));managed.addAll(next.roleSync().managedIds());
        if(!Collections.disjoint(managed,next.roles()))throw new RolePolicy.Blocked("console-role-conflict");
        if(!next.roleSync().managedIds().isEmpty()&&transport.available()&&next.guild().equals(transport.guild()))transport.validate(next);
    }
    public void syncAll()throws Exception{if(!settings.get().roleSync().enabled())throw new IllegalStateException("Roles disabled");begin(false,true);}
    public void sync(UUID uuid)throws Exception{store.byUuid(uuid).orElseThrow(()->new IllegalArgumentException("Account not linked"));enqueue(uuid);}
    public CleanupPreview cleanupPreview()throws Exception{
        Settings s=settings.get();Set<String> roles=new HashSet<>(store.roleHistory(s.guild()));roles.addAll(s.roleSync().managedIds());
        return new CleanupPreview(store.roleCleanupCount(s.guild()),roles);
    }
    public void startCleanup()throws Exception{if(settings.get().roleSync().enabled())throw new IllegalStateException("Disable role sync before cleanup");begin(true,true);}
    private void begin(boolean cleanup,boolean batch)throws Exception{
        scanSettings=settings.get();store.roleRemember(scanSettings.guild(),scanSettings.roleSync().managedIds());
        linkCursor="";targetCursor="";targetPhase=false;cleanupScan=cleanup;scanning=true;currentBatch=batch?(batchCounter=Math.max(clock.instant().toEpochMilli(),batchCounter+1)):0;
        store.roleSweepStart(scanSettings.guild(),cleanup,currentBatch,store.roleHistory(scanSettings.guild()));
    }
    public RolePolicy.Plan preview(UUID uuid)throws Exception{
        Settings s=settings.get();Store.Link link=store.byUuid(uuid).orElseThrow(()->new IllegalArgumentException("Account not linked"));
        var member=transport.inspect(s.guild(),link.discord());if(!member.present())return new RolePolicy.Plan(Set.of(),Set.of(),false);
        GroupProvider.Result groups=membership(s,link);Set<String> history=new HashSet<>(store.roleHistory(s.guild()));history.addAll(s.roleSync().managedIds());
        var plan=RolePolicy.plan(s.roleSync(),true,groups,member.roles(),history);Set<String> validate=new HashSet<>(s.roleSync().managedIds());validate.addAll(plan.remove());RolePolicy.validate(member,validate,s.roles(),s.guild());return plan;
    }
    private GroupProvider.Result membership(Settings s,Store.Link link){
        if(s.roleSync().rules().isEmpty())return GroupProvider.Result.known(Set.of(),Instant.MAX);
        try{return provider.groups(link.uuid(),s.roleSync().context());}catch(Exception e){return GroupProvider.Result.unknown("provider-error");}
    }
    /** Deterministic unit-test seam; production invokes this only on the dedicated worker. */
    public void runOnce()throws Exception{
        if(closed)return;Settings s=settings.get();Instant now=clock.instant();
        if(scanSettings!=null&&scanSettings!=s){scanning=false;scanSettings=null;requested.set(true);}
        if(s.roleSync().enabled()){
            if(registered!=s){store.roleCancelCleanup(s.guild());store.roleRemember(s.guild(),s.roleSync().managedIds());registered=s;}
            List<String> pending=new ArrayList<>();synchronized(signals){var it=signals.iterator();while(it.hasNext()&&pending.size()<8){pending.add(it.next());it.remove();}}
            for(String signal:pending){Optional<Store.Link> link=signal.startsWith("u:")?store.byUuid(UUID.fromString(signal.substring(2))):store.byDiscord(signal.substring(2));
                String dc=link.map(Store.Link::discord).orElse(signal.startsWith("d:")?signal.substring(2):"");if(!dc.isEmpty())store.roleQueue(s.guild(),dc,now,false,0);
            }
            resume(s);
            if((scanSettings==null||targetPhase&&targetCursor==null)&&!scanning&&(requested.getAndSet(false)||!now.isBefore(nextSweep))){begin(false,false);nextSweep=now.plusSeconds(s.roleSync().recheckSeconds());}
        }else synchronized(signals){signals.clear();}
        if(!s.roleSync().enabled())resume(s);
        if(scanning&&scanSettings!=null)page(now);
        Optional<Store.RoleTarget> next=store.roleNext(now,s.roleSync().enabled(),s.guild());if(next.isPresent())reconcile(next.get(),s,now);
        if(scanSettings!=null&&targetPhase&&targetCursor==null){
            Map<String,Long> counts=store.roleCounts(scanSettings.guild(),currentBatch);scanning=counts.getOrDefault("QUEUED",0L)>0;
            if(!scanning&&counts.getOrDefault("PENDING",0L)==0&&counts.getOrDefault("FAILED",0L)==0){store.roleSweepDone(scanSettings.guild(),cleanupScan);cleanupScan=false;scanSettings=null;}
        }
    }
    private void resume(Settings s)throws Exception{
        if(scanSettings!=null)return;var saved=store.roleSweep(s.guild());if(saved.isEmpty())return;var sweep=saved.get();
        if(sweep.cleanup()==s.roleSync().enabled())return;scanSettings=s;cleanupScan=sweep.cleanup();linkCursor=sweep.linkCursor();targetCursor=sweep.phase()==2?null:sweep.targetCursor();targetPhase=sweep.phase()>0;currentBatch=sweep.batch();scanning=sweep.phase()<2;
    }
    private void page(Instant now)throws Exception{
        if(targetCursor==null&&targetPhase)return;
        if(!targetPhase){var page=store.linksPage(linkCursor,20);for(var link:page)store.roleQueue(scanSettings.guild(),link.discord(),now,cleanupScan,currentBatch);
            if(page.size()<20)targetPhase=true;else linkCursor=page.get(page.size()-1).uuid().toString();
        }else{var page=store.roleTargetsPage(scanSettings.guild(),targetCursor,20);for(var target:page)if(store.byDiscord(target.discord()).isEmpty())store.roleQueue(target.guild(),target.discord(),now,cleanupScan,currentBatch);
            targetCursor=page.size()<20?null:page.get(page.size()-1).discord();
        }
        store.roleSweepProgress(new Store.RoleSweep(scanSettings.guild(),cleanupScan,linkCursor,targetCursor==null?"":targetCursor,targetPhase?(targetCursor==null?2:1):0,currentBatch));
    }
    private void reconcile(Store.RoleTarget target,Settings s,Instant now)throws Exception{
        long e=epoch.get(),session=transport.session();Optional<Store.Link> source=store.byDiscord(target.discord());
        if(!transport.available()||!target.guild().equals(transport.guild())||!target.guild().equals(s.guild())){store.roleFinish(target,"PENDING",now.plusSeconds(30),true);return;}
        try{
            var member=transport.inspect(target.guild(),target.discord());if(!guard(target,s,source,e,session,Instant.MAX)){store.roleFinish(target,"PENDING",now.plusSeconds(1),true);return;}
            if(!member.present()){store.roleFinish(target,"ABSENT",now,false);return;}if(member.bot())throw new RolePolicy.Blocked("bot-account");
            var groups=source.isPresent()&&!target.cleanup()?membership(s,source.get()):GroupProvider.Result.known(Set.of(),Instant.MAX);
            Set<String> history=target.cleanup()?store.roleCleanupRoles(target.guild()):store.roleHistory(target.guild());var plan=RolePolicy.plan(s.roleSync(),source.isPresent()&&!target.cleanup(),groups,member.roles(),history);
            Set<String> desired=new HashSet<>(s.roleSync().managedIds());if(target.cleanup()||source.isEmpty())desired.clear();RolePolicy.validate(member,desired,s.roles(),s.guild());
            Instant expires=groups.known()?groups.validUntil():Instant.MAX;
            BooleanSupplier current=()->guard(target,s,source,e,session,expires);int operations=0;
            for(String id:new TreeSet<>(plan.remove())){if(operations>=2||!current.getAsBoolean())break;RolePolicy.validate(member,Set.of(id),s.roles(),s.guild());transport.change(target.guild(),target.discord(),id,false,current);operations++;}
            for(String id:new TreeSet<>(plan.add())){if(operations>=2||!current.getAsBoolean())break;transport.change(target.guild(),target.discord(),id,true,current);operations++;}
            if(!current.getAsBoolean()){store.roleFinish(target,"PENDING",now.plusSeconds(1),true);return;}
            boolean partial=plan.add().size()+plan.remove().size()>operations;
            if(partial||plan.unknown())store.roleFinish(target,"PENDING",now.plusSeconds(partial?1:30),true);
            else{boolean expiry=!expires.equals(Instant.MAX);store.roleFinish(target,"SUCCESS",expiry?expires:now,expiry);failure="";}
        }catch(RolePolicy.Blocked e1){failure=e1.getMessage();store.roleFinish(target,"FAILED",now.plusSeconds(60),true);}
        catch(Exception e1){failure=e1.getClass().getSimpleName();store.roleFinish(target,"PENDING",now.plusSeconds(30),true);}
    }
    private boolean guard(Store.RoleTarget target,Settings captured,Optional<Store.Link> source,long e,long session,Instant expiry){
        if(closed||settings.get()!=captured||epoch.get()!=e||transport.session()!=session||!transport.available()||!clock.instant().isBefore(expiry)||target.cleanup()==captured.roleSync().enabled())return false;
        try{return store.roleCurrent(target)&&store.byDiscord(target.discord()).map(Store.Link::version).equals(source.map(Store.Link::version));}catch(Exception error){return false;}
    }
    @Override public void close(){if(closed)return;closed=true;epoch.incrementAndGet();timer.shutdownNow();try{closeProvider(provider);}finally{worker.shutdownNow();pendingWork.forEach(future->future.completeExceptionally(new IllegalStateException("Role worker closed")));synchronized(signals){signals.clear();}}try{worker.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
