package dev.dydiscord.giveaway;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.GiveawaySettings;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded background worker owns the lottery. No DB or network work runs on Bukkit's thread. */
public final class GiveawayService implements AutoCloseable {
    public enum EntryResult { JOINED, ALREADY, CLOSED, FULL, INELIGIBLE, UNAVAILABLE }
    private final GiveawayStore store;private final GiveawayHost host;private final Clock clock;private final SecureRandom random;
    private final ThreadPoolExecutor worker;private final ScheduledExecutorService timer;private final AtomicBoolean tickQueued=new AtomicBoolean();
    private final Map<String,Draw> draws=new HashMap<>();private final Map<String,Retry> retries=new LinkedHashMap<>();private volatile boolean closed;
    private String scanCursor="";private GiveawayHost.Context scanContext;
    private final CompletableFuture<Void> closedStore=new CompletableFuture<>();private final Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();private volatile Thread workerThread; private long lastWarning=Long.MIN_VALUE;
    private static final class Draw {final GiveawayHost.Context context;final List<String> shuffled;final List<GiveawayStore.Winner> winners=new ArrayList<>();int index;Draw(GiveawayHost.Context c,List<String> shuffled){context=c;this.shuffled=shuffled;}}
    private record Retry(int failures,long due){}
    public GiveawayService(DyDiscord plugin)throws Exception{this(new GiveawayStore(plugin.getDataFolder().toPath().resolve("data/giveaways.db")),new JdaGiveawayHost(plugin),plugin.clock(),new SecureRandom());}
    public GiveawayService(GiveawayStore store,GiveawayHost host,Clock clock,SecureRandom random){
        this.store=store;this.host=host;this.clock=clock;this.random=random;
        worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{Thread t=new Thread(r,"DyDiscord-giveaways");t.setDaemon(true);workerThread=t;return t;},new ThreadPoolExecutor.AbortPolicy()){
            @Override protected void terminated(){Thread closer=new Thread(()->{try{store.close();}catch(Exception e){host.warning();}finally{closedStore.complete(null);}},"DyDiscord-giveaway-close");closer.setDaemon(true);closer.start();}
        };
        timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"DyDiscord-giveaway-timer");t.setDaemon(true);return t;});
        timer.scheduleWithFixedDelay(this::queueTick,5,5,TimeUnit.SECONDS);
    }
    public CompletableFuture<String> start(long seconds,int winnerCount,String prize){
        GiveawayHost.Context admitted=host.context();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        return submit(()->{
            GiveawayStore.validate(seconds,winnerCount,prize);GiveawayHost.Context c=admitted;require(c);
            if(System.nanoTime()>=deadline)throw new TimeoutException("Giveaway request expired");
            GiveawaySettings s=c.settings();GiveawayStore.Giveaway g=store.create(c.guild(),s.channel(),seconds,winnerCount,prize,s.requireLinked(),s.allowedRoles(),clock.millis());
            // A failed/ambiguous first send remains DRAFT. Never retry it and risk a second public message.
            String message=host.publish(g,()->current(c)&&System.nanoTime()<deadline);if(!current(c)||System.nanoTime()>=deadline)throw new IllegalStateException("Giveaway connection changed or request expired");
            if(!store.activate(g.id(),message))throw new IllegalStateException("Giveaway publication changed");return g.id();
        });
    }
    public CompletableFuture<Boolean> end(String id){return submit(()->{GiveawayHost.Context c=host.context();require(c);var g=store.find(id);return g.isPresent()&&owns(c,g.get())&&current(c)&&store.end(id,clock.millis());});}
    public CompletableFuture<Boolean> cancel(String id){return submit(()->{GiveawayHost.Context c=host.context();var g=store.find(id);if(closed||g.isEmpty()||!g.get().guild().equals(c.guild()))return false;draws.remove(id);retries.remove(id);return store.cancel(id);});}
    public CompletableFuture<List<GiveawayStore.Giveaway>> list(){return submit(store::list);}
    public CompletableFuture<EntryResult> enter(String id,String guild,String channel,String message,String actor,long session){return submit(()->{
        GiveawayHost.Context c=host.context();if(!current(c)||session!=c.session())return EntryResult.UNAVAILABLE;
        var found=store.find(id);if(found.isEmpty())return EntryResult.UNAVAILABLE;var g=found.get();
        if(!owns(c,g)||!g.guild().equals(guild)||!g.channel().equals(channel)||!g.message().equals(message))return EntryResult.UNAVAILABLE;
        if(!g.state().equals("ACTIVE")||g.endsAt()<=clock.millis())return EntryResult.CLOSED;
        GiveawayHost.Member member;try{member=host.member(g.guild(),actor);}catch(Exception e){warn();return EntryResult.UNAVAILABLE;}
        if(!current(c))return EntryResult.UNAVAILABLE;
        if(!eligible(g,actor,member))return EntryResult.INELIGIBLE;
        if(!current(c))return EntryResult.UNAVAILABLE;
        return EntryResult.valueOf(store.enter(id,actor,clock.millis()).name());
    });}
    public void button(ButtonInteractionEvent event,long session){
        GiveawayHost.Context context=host.context();if(!current(context)||context.session()!=session)return;
        String custom=event.getComponentId();if(!custom.startsWith("dd:giveaway:"))return;
        String id=custom.substring("dd:giveaway:".length());
        if(!id.matches("[0-9a-f]{32}")||!event.isFromGuild()){event.reply(text("giveaway-unavailable",Map.of())).setEphemeral(true).setAllowedMentions(List.of()).setCheck(()->current(context)).queue(null,e->warn());return;}
        event.deferReply(true).setCheck(()->current(context)).queue(hook->enter(id,event.getGuild().getId(),event.getChannel().getId(),event.getMessageId(),event.getUser().getId(),session)
            .whenComplete((result,failure)->{if(!current(context))return;if(failure!=null)warn();String key=failure==null?"giveaway-"+result.name().toLowerCase(Locale.ROOT):"giveaway-unavailable";hook.editOriginal(text(key,Map.of())).setAllowedMentions(List.of()).setCheck(()->current(context)).queue(null,e->warn());}),e->warn());
    }
    private String text(String key,Map<String,String> values){if(host instanceof JdaGiveawayHost jda)return jda.text(key,values);return key;}
    public void refresh(){submit(()->{retries.clear();scanCursor="";scanContext=null;tick();return null;}).exceptionally(e->{if(!closed)warn();return null;});}
    /** Also usable by offline acceptance tests; executes on the same bounded worker. */
    public CompletableFuture<Void> poll(){return submit(()->{tick();return null;});}
    private void queueTick(){if(closed||!tickQueued.compareAndSet(false,true))return;submit(()->{try{tick();}finally{tickQueued.set(false);}return null;}).exceptionally(e->{tickQueued.set(false);if(!closed)warn();return null;});}
    private void tick()throws Exception{
        GiveawayHost.Context c=host.context();if(!current(c)){draws.clear();scanCursor="";scanContext=null;return;}
        if(!c.equals(scanContext)){scanContext=c;scanCursor="";}
        var page=store.pending(c.guild(),c.settings().channel(),scanCursor);
        if(page.isEmpty()&&!scanCursor.isEmpty()){scanCursor="";page=store.pending(c.guild(),c.settings().channel(),scanCursor);}
        if(!page.isEmpty())scanCursor=page.get(page.size()-1).id();
        for(var g:page){
            if(!current(c))return;if(!owns(c,g))continue;Retry retry=retries.get(g.id());if(retry!=null&&retry.due()>clock.millis())continue;
            try{
                if(g.state().equals("ACTIVE")&&g.endsAt()<=clock.millis())draw(c,g);
                var latest=store.find(g.id()).orElseThrow();
                if(!latest.state().equals("ACTIVE")&&!latest.announced()){
                    if(!current(c))return;
                    if(!latest.message().isEmpty())host.update(latest,store.winners(g.id()),()->current(c));
                    if(current(c)){store.announced(g.id());retries.remove(g.id());}
                }
            }catch(Exception e){if(closed)return;Retry old=retries.get(g.id());int failures=old==null?1:Math.min(7,old.failures()+1);if(old==null&&retries.size()>=128)retries.remove(retries.keySet().iterator().next());retries.put(g.id(),new Retry(failures,clock.millis()+Math.min(300000,5000L<<(failures-1))));warn();}
        }
    }
    private void draw(GiveawayHost.Context c,GiveawayStore.Giveaway g)throws Exception{
        Draw draw=draws.get(g.id());if(draw==null||!draw.context.equals(c)){draw=new Draw(c,GiveawayHost.shuffled(store.entrants(g.id()),random));draws.put(g.id(),draw);}
        int checked=0;
        while(draw.index<draw.shuffled.size()&&draw.winners.size()<g.winnerCount()&&checked++<10){
            String actor=draw.shuffled.get(draw.index);GiveawayHost.Member member=host.member(g.guild(),actor);
            if(!current(c)){draws.remove(g.id());return;}
            if(eligible(g,actor,member)){String name=member.name().replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]"," ").strip();if(name.isBlank())name="Discord member";if(name.codePointCount(0,name.length())>32)name=name.substring(0,name.offsetByCodePoints(0,32));draw.winners.add(new GiveawayStore.Winner(actor,name));}
            draw.index++; // A transport failure never advances this candidate or silently excludes them.
        }
        if(draw.index>=draw.shuffled.size()||draw.winners.size()>=g.winnerCount()){
            if(!current(c))return;
            // Links may have changed while the REST membership checks were in flight.
            for(var iterator=draw.winners.iterator();iterator.hasNext();){var winner=iterator.next();var member=host.member(g.guild(),winner.discord());if(!current(c)){draws.remove(g.id());return;}if(!eligible(g,winner.discord(),member))iterator.remove();}
            if(draw.winners.size()<g.winnerCount()&&draw.index<draw.shuffled.size())return;
            if(!current(c))return;
            store.commitDraw(g.id(),List.copyOf(draw.winners));draws.remove(g.id());retries.remove(g.id());
        }
    }
    private boolean eligible(GiveawayStore.Giveaway g,String actor,GiveawayHost.Member member)throws Exception{return member.present()&&!member.bot()&&(!g.requireLinked()||host.linked(actor))&&(g.roles().isEmpty()||member.roles().stream().anyMatch(g.roles()::contains));}
    private boolean owns(GiveawayHost.Context c,GiveawayStore.Giveaway g){return c.guild().equals(g.guild())&&c.settings().channel().equals(g.channel());}
    private boolean current(GiveawayHost.Context c){return !closed&&c.connected()&&c.settings().enabled()&&!c.guild().isEmpty()&&!c.settings().channel().isEmpty()&&host.context().equals(c);}
    private void require(GiveawayHost.Context c){if(!current(c))throw new IllegalStateException("Giveaways disabled or unavailable");}
    private <T>CompletableFuture<T> submit(Callable<T> operation){long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);CompletableFuture<T> future=new CompletableFuture<>();pending.add(future);future.whenComplete((v,e)->pending.remove(future));if(closed){future.completeExceptionally(new RejectedExecutionException("Giveaways closed"));return future;}try{worker.execute(()->{if(future.isDone())return;if(System.nanoTime()>=deadline){future.completeExceptionally(new TimeoutException("Giveaway queue deadline reached"));return;}if(closed){future.completeExceptionally(new RejectedExecutionException("Giveaways closed"));return;}try{future.complete(operation.call());}catch(Throwable e){future.completeExceptionally(e);}});}catch(RejectedExecutionException e){future.completeExceptionally(e);}return future;}
    private synchronized void warn(){long now=clock.millis();if(lastWarning==Long.MIN_VALUE||now-lastWarning>=60000){lastWarning=now;host.warning();}}
    @Override public void close(){closed=true;timer.shutdownNow();worker.shutdownNow();pending.forEach(f->f.cancel(false));if(Thread.currentThread()!=workerThread)try{worker.awaitTermination(2,TimeUnit.SECONDS);if(worker.isTerminated())closedStore.get(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(ExecutionException|TimeoutException e){warn();}}
}
