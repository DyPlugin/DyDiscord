package dev.dydiscord.reward;

import dev.dydiscord.config.LinkRewardSettings;
import dev.dydiscord.storage.Store;
import dev.dydiscord.util.PluginThreads;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Only post-commit link events are eligible. No scans, retries, or retroactive grants. */
public final class LinkRewardService implements AutoCloseable {
    public enum Result { SKIPPED, DISPATCHED, UNKNOWN }
    public record Preview(boolean enabled,boolean eligible,List<String> commands){
        public Preview{commands=List.copyOf(commands);}
        @Override public String toString(){return "Preview[enabled="+enabled+", eligible="+eligible+", commands=REDACTED]";}
    }
    public interface Host {
        LinkRewardSettings settings();boolean ready();boolean mainThread();long session();
        Store.LinkSnapshot snapshot(String discord)throws Exception;long linkGeneration(String discord);
        default boolean firstRewardEligible(Store.Link link)throws Exception{return true;}
        /** Evaluate guard on main immediately before dispatch, never read database there. */
        CompletableFuture<Boolean> dispatch(String command,BooleanSupplier guard);
        void warning(String safeMessage);
    }
    private final Host host;private final LinkRewardStore store;private final Clock clock;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),PluginThreads.factory("DyDiscord-link-rewards"),new ThreadPoolExecutor.AbortPolicy());
    private final Set<CompletableFuture<Result>> pending=ConcurrentHashMap.newKeySet();
    private volatile boolean closed,failed;
    private Instant lastWarning=Instant.EPOCH;
    public LinkRewardService(Host host,LinkRewardStore store,Clock clock){this.host=Objects.requireNonNull(host);this.store=Objects.requireNonNull(store);this.clock=Objects.requireNonNull(clock);}
    public boolean healthy(){return !closed&&!failed&&store.writable();}
    public int pending(){return pending.size();}
    /** Invoke on the existing link I/O worker, immediately after permanent commit. */
    public CompletableFuture<Result> afterLinked(Store.Link link){
        offMain();CompletableFuture<Result> result=new CompletableFuture<>();
        try{
            LinkRewardSettings settings=host.settings();long session=host.session();
            boolean allowed=healthy()&&settings.enabled()&&!settings.commands().isEmpty()&&safeName(link.name())&&host.ready()&&host.firstRewardEligible(link);
            if(!store.claim(link,allowed)){result.complete(Result.SKIPPED);return result;}
            Store.LinkSnapshot snapshot=host.snapshot(link.discord());
            boolean current=sameLink(snapshot.link().orElse(null),link);
            Instant deadline=clock.instant().plusSeconds(5);
            BooleanSupplier guard=()->current&&clock.instant().isBefore(deadline)&&healthy()&&host.ready()&&host.settings()==settings&&host.session()==session&&host.linkGeneration(link.discord())==snapshot.generation();
            pending.add(result);
            try{worker.execute(()->dispatch(link,settings.commands(),guard,result));}
            catch(RejectedExecutionException rejected){store.finish(link,false);pending.remove(result);result.complete(Result.UNKNOWN);warn();}
        }catch(Exception error){failed=true;
            try{store.finish(link,false);}catch(Exception finishFailure){error.addSuppressed(finishFailure);}
            pending.remove(result);warn();result.complete(Result.UNKNOWN);
        }
        return result;
    }
    public Preview preview(Store.Link link)throws Exception{
        offMain();LinkRewardSettings settings=host.settings();boolean safe=safeName(link.name());
        return new Preview(settings.enabled(),safe&&host.firstRewardEligible(link)&&store.eligible(link),safe?render(settings.commands(),link):List.of());
    }
    private void offMain(){if(host.mainThread())throw new IllegalStateException("Link reward database access requires background execution");}
    private void dispatch(Store.Link link,List<String> commands,BooleanSupplier guard,CompletableFuture<Result> result){
        boolean dispatched=true;
        try{
            for(String command:render(commands,link)){
                if(!guard.getAsBoolean()){dispatched=false;break;}
                AtomicBoolean attempt=new AtomicBoolean(true);
                try{if(!Boolean.TRUE.equals(host.dispatch(command,()->attempt.get()&&guard.getAsBoolean()).get(2,TimeUnit.SECONDS))){dispatched=false;break;}}
                finally{attempt.set(false);}
            }
        }catch(Exception error){dispatched=false;warn();if(error instanceof InterruptedException)Thread.currentThread().interrupt();}
        try{store.finish(link,dispatched);}catch(Exception error){failed=true;dispatched=false;warn();}
        finally{pending.remove(result);result.complete(dispatched?Result.DISPATCHED:Result.UNKNOWN);}
    }
    private static boolean sameLink(Store.Link current,Store.Link expected){return current!=null&&current.uuid().equals(expected.uuid())&&current.discord().equals(expected.discord())&&current.version().equals(expected.version());}
    private static boolean safeName(String name){return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}
    private static List<String> render(List<String> commands,Store.Link link){return commands.stream().map(command->command.replace("{player}",link.name()).replace("{uuid}",link.uuid().toString())).toList();}
    private synchronized void warn(){Instant now=clock.instant();if(!now.isBefore(lastWarning.plusSeconds(30))){lastWarning=now;host.warning("Link reward processing is incomplete; uncertain commands are never replayed (values withheld).");}}
    @Override public void close(){closed=true;worker.shutdownNow();for(var result:pending)result.complete(Result.UNKNOWN);pending.clear();}
}
