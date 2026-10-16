package dev.dydiscord.update;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.util.PluginThreads;
import org.bukkit.entity.Player;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Public metadata only; no JAR download, token, player identity or server ID. */
public final class UpdateChecker implements AutoCloseable {
    public interface Source extends AutoCloseable {Modrinth.Response fetch(String version,String minecraft,String platform)throws Exception;void close();}
    private final DyDiscord plugin;private final Source source;private final String version,minecraft,platform;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(PluginThreads.factory("DyDiscord-updates"));
    private final AtomicLong generation=new AtomicLong();private final Map<UUID,String> notified=new HashMap<>();
    private volatile boolean closed;private volatile Modrinth.Release available;private ScheduledFuture<?> next;private String logged="";
    public UpdateChecker(DyDiscord plugin){this(plugin,new Source(){final Modrinth api=new Modrinth();public Modrinth.Response fetch(String version,String minecraft,String platform)throws Exception{return api.fetch(version,minecraft,platform);}public void close(){api.close();}},plugin.getDescription().getVersion(),plugin.getServer().getBukkitVersion().replaceFirst("[.-](?:R|build).*",""),plugin.getServer().getName());}
    public UpdateChecker(DyDiscord plugin,Source source,String version,String minecraft,String platform){this.plugin=plugin;this.source=source;this.version=version;this.minecraft=minecraft;this.platform=platform;}
    public synchronized void refresh(){long g=generation.incrementAndGet();if(next!=null)next.cancel(true);available=null;notified.clear();if(!closed&&plugin.settings().updateChecks())schedule(g,10);}
    private synchronized void schedule(long g,long seconds){if(!closed&&g==generation.get())next=worker.schedule(()->check(g),seconds,TimeUnit.SECONDS);}
    public void check(long g){long delay=21600;try{
        if(closed||g!=generation.get()||!plugin.ready()||!plugin.settings().updateChecks())return;
        Modrinth.Response response=source.fetch(version,minecraft,platform);if(closed||g!=generation.get()||!plugin.settings().updateChecks())return;
        if(response.status()==200){Set<String> loaders=Modrinth.loaders(platform);Modrinth.Release release=(plugin.settings().tools().updateBeta()?Modrinth.newer(response.body(),version,minecraft,loaders,true):Modrinth.newer(response.body(),version,minecraft,loaders)).orElse(null);synchronized(this){if(closed||g!=generation.get()||!plugin.settings().updateChecks())return;available=release;if(release!=null){if(!plugin.settings().tools().updateAudience().equals("players"))log("available:"+release.id(),"New DyDiscord version "+release.version()+" is available: "+release.url());plugin.onMain(()->{if(!closed&&g==generation.get()&&plugin.ready()&&plugin.settings().updateChecks())for(Player player:plugin.getServer().getOnlinePlayers())notify(player);});}}}
        else if(response.status()==404){synchronized(this){if(closed||g!=generation.get())return;available=null;log("missing","Modrinth project dydiscord is not published yet. Update checking will retry later.");}}
        else {delay=response.status()==429?Math.max(900,response.retryAfter()):900;log("unavailable","Modrinth update check is temporarily unavailable. Retrying later.");}
    }catch(Exception failure){if(!closed&&g==generation.get()){delay=900;log("unavailable","Modrinth update check is temporarily unavailable. Retrying later.");}}
    finally{schedule(g,delay);}}
    private synchronized void log(String state,String text){if(!logged.equals(state)){logged=state;plugin.getLogger().info(text);}}
    public synchronized void notify(Player player){Modrinth.Release release=available;if(closed||release==null||plugin.settings().tools().updateAudience().equals("console")||!plugin.ready()||!plugin.settings().updateChecks()||!player.isOnline()||!player.hasPermission("dydiscord.admin.update")||release.id().equals(notified.get(player.getUniqueId())))return;if(notified.size()>=4096)return;notified.put(player.getUniqueId(),release.id());plugin.message(player,"update-available",Map.of("version",release.version(),"url",release.url()));}
    public synchronized void leave(UUID player){notified.remove(player);}
    public long generation(){return generation.get();}
    @Override public synchronized void close(){if(closed)return;closed=true;generation.incrementAndGet();if(next!=null)next.cancel(true);worker.shutdownNow();source.close();available=null;notified.clear();}
}
