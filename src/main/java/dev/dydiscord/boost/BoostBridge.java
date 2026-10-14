package dev.dydiscord.boost;

import dev.dydiscord.config.Settings;
import dev.dydiscord.storage.Store;
import dev.dydiscord.util.PluginThreads;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Dedicated, bounded reconciliation; there is deliberately no economy success response. */
public final class BoostBridge implements AutoCloseable {
    public record Membership(boolean present,boolean bot,Instant premiumSince) {}
    public record Source(Store.Link link,long generation) {}
    public interface Host {
        Settings settings();long botGeneration();boolean ready();
        Source link(String discord)throws Exception;long linkGeneration();
        default long linkGeneration(String discord){return linkGeneration();}
        List<Store.Link> linkedAfter(String uuidCursor,int limit)throws Exception;
        /** Must use fresh REST data; a failed/unknown lookup throws, never reports absent. */
        Membership freshMembership(String discord)throws Exception;
        /** Runs on the Minecraft main thread, evaluating guard immediately before dispatch. */
        CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard);
        void warning(String safeMessage);
    }
    private final Host host;private final BoostStore store;private final Clock clock;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(PluginThreads.factory("DyDiscord-boost"));
    private final LinkedHashSet<String> signals=new LinkedHashSet<>();
    private record Flight(String discord,AtomicBoolean valid) {}
    private final AtomicReference<Flight> active=new AtomicReference<>();
    private volatile boolean closed,failed;private boolean started,sweeping=true;private String linkCursor="",receiptCursor="",scanGuild="";
    private boolean linksDone,receiptsDone;private volatile Instant nextSweep=Instant.EPOCH;private Instant lastWarning=Instant.EPOCH;
    public BoostBridge(Host host,BoostStore store,Clock clock){this.host=Objects.requireNonNull(host);this.store=Objects.requireNonNull(store);this.clock=Objects.requireNonNull(clock);}
    public synchronized void start(){if(started||closed)return;started=true;worker.scheduleWithFixedDelay(this::tick,0,1,TimeUnit.SECONDS);}
    public void signal(String discord){if(closed||discord==null||!Settings.snowflake(discord))return;Flight flight=active.get();if(flight!=null&&flight.discord().equals(discord))flight.valid().set(false);synchronized(signals){if(signals.contains(discord))return;if(signals.size()<256)signals.add(discord);else nextSweep=Instant.EPOCH;}}
    public void enqueue(String discord){signal(discord);}
    public void refresh(){synchronized(signals){nextSweep=Instant.EPOCH;}}
    public int pending(){synchronized(signals){return signals.size();}}
    public boolean healthy(){return !failed&&store.writable();}
    void tick(){
        if(closed||failed)return;
        try{
            Settings settings=host.settings();if(!settings.boostRewards().enabled()||!host.ready())return;
            if(!settings.guild().equals(scanGuild)){scanGuild=settings.guild();resetSweep();synchronized(signals){signals.clear();}}
            if(!sweeping&&!clock.instant().isBefore(nextSweep))resetSweep();
            if(sweeping&&pending()<=216){
                if(!linksDone){var links=host.linkedAfter(linkCursor,20);for(var link:links)signal(link.discord());if(!links.isEmpty())linkCursor=links.get(links.size()-1).uuid().toString();linksDone=links.size()<20;}
                if(!receiptsDone){var ids=store.discordAfter(scanGuild,receiptCursor,20);ids.forEach(this::signal);if(!ids.isEmpty())receiptCursor=ids.get(ids.size()-1);receiptsDone=ids.size()<20;}
                if(linksDone&&receiptsDone){sweeping=false;nextSweep=clock.instant().plusSeconds(settings.boostRewards().recheckSeconds());}
            }
            String id=null;synchronized(signals){var iterator=signals.iterator();if(iterator.hasNext()){id=iterator.next();iterator.remove();}}
            if(id!=null)reconcile(id);
        }catch(Exception e){if(e instanceof java.sql.SQLException||!store.writable())failed=true;warn();}
    }
    private void resetSweep(){sweeping=true;linksDone=false;receiptsDone=false;linkCursor="";receiptCursor="";}
    synchronized void reconcile(String discord)throws Exception{
        if(closed||failed)return;
        Settings settings=host.settings();var config=settings.boostRewards();
        if(!config.enabled()||settings.guild().isEmpty()||!host.ready())return;
        Flight flight=new Flight(discord,new AtomicBoolean(true));active.set(flight);
        try{reconcileActive(discord,settings,flight);}
        finally{flight.valid().set(false);active.compareAndSet(flight,null);}
    }
    private void reconcileActive(String discord,Settings settings,Flight flight)throws Exception{
        var config=settings.boostRewards();
        long bot=host.botGeneration();Source source=host.link(discord);
        Membership member;
        try{member=host.freshMembership(discord);}catch(Exception e){warn();return;}
        if(member==null){warn();return;}
        Instant observed=clock.instant();
        BooleanSupplier guard=()->!closed&&!failed&&host.ready()&&host.settings()==settings&&host.botGeneration()==bot&&host.linkGeneration(discord)==source.generation()&&flight.valid().get()&&clock.instant().isBefore(observed.plusSeconds(5));
        if(!guard.getAsBoolean())return;
        var old=store.pendingRemoval(settings.guild(),discord);
        for(var receipt:old){
            // A missed end/restart can leave an older receipt while the same player is
            // boosting again. Revoking that old period must not remove current benefits.
            boolean ended=!member.present()||member.bot()||member.premiumSince()==null;
            boolean unlinked=source.link()==null||!receipt.uuid().equals(source.link().uuid());
            if((ended||unlinked)&&receipt.removal().equals("NONE")&&!config.removeCommands().isEmpty()&&safeName(receipt.name())&&guard.getAsBoolean()){
                if(store.claimRemoval(receipt))dispatch(receipt,true,config.removeCommands(),guard);
            }
        }
        Store.Link link=source.link();
        if(link==null||!member.present()||member.bot()||member.premiumSince()==null||member.premiumSince().isAfter(observed)||config.commands().isEmpty()||!safeName(link.name())||!guard.getAsBoolean())return;
        if(store.claim(settings.guild(),discord,member.premiumSince(),link.uuid(),link.name(),clock.instant(),config.cooldownHours())){
            BoostStore.Receipt receipt=new BoostStore.Receipt(settings.guild(),discord,member.premiumSince(),link.uuid(),link.name(),clock.instant(),"CLAIMED","NONE");
            dispatch(receipt,false,config.commands(),guard);
        }
    }
    private void dispatch(BoostStore.Receipt receipt,boolean removal,List<String> templates,BooleanSupplier guard)throws Exception{
        boolean dispatched=true;
        for(String template:templates){
            if(!guard.getAsBoolean()){dispatched=false;break;}
            String command=template.replace("{player}",receipt.name()).replace("{uuid}",receipt.uuid().toString());
            AtomicBoolean attempt=new AtomicBoolean(true);
            try{if(!Boolean.TRUE.equals(host.dispatch(command,()->attempt.get()&&guard.getAsBoolean()).get(2,TimeUnit.SECONDS))){dispatched=false;break;}}
            catch(Exception e){dispatched=false;warn();break;}
            finally{attempt.set(false);}
        }
        store.finish(receipt,removal,dispatched);
    }
    private static boolean safeName(String name){return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}
    private void warn(){Instant now=clock.instant();if(!now.isBefore(lastWarning.plusSeconds(30))){lastWarning=now;host.warning("Boost reward reconciliation is incomplete; uncertain command attempts are never replayed (values withheld).");}}
    @Override public void close(){closed=true;synchronized(signals){signals.clear();}worker.shutdownNow();}
}
