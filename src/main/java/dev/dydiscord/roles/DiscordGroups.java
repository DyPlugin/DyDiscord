package dev.dydiscord.roles;

import dev.dydiscord.config.*;
import dev.dydiscord.storage.*;
import dev.dydiscord.util.PluginThreads;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * Discord -> Minecraft: configured Discord roles grant LuckPerms groups to linked players. Only groups DyDiscord added
 * itself are ever removed; a group the player already had is left alone. Staff-like groups and groups holding the
 * * permission are refused unless listed in staff-groups. One player at a time on its own thread, a few per second.
 */
public final class DiscordGroups implements AutoCloseable {
    /** Fresh Discord role IDs of a member; null when the user is not in the guild. Throws when Discord cannot answer. */
    public interface Members {boolean available();Set<String> roles(String discordId)throws Exception;}
    /** LuckPerms group edits; add and remove return true when the player's groups changed. */
    public interface Writer {
        enum Check {OK,MISSING,WILDCARD}
        boolean available();boolean add(UUID player,String group)throws Exception;boolean remove(UUID player,String group)throws Exception;Check check(String group)throws Exception;
    }
    static final int PER_TICK=4,MAX_QUEUE=512;
    private final Supplier<Settings> settings;private final Store links;private final FeatureStore grants;private final Members members;private volatile Writer writer;
    private final Supplier<Collection<UUID>> online;private final Clock clock;private final Consumer<String> warnings;private final Consumer<Throwable> errors;
    private final Set<UUID> queue=new LinkedHashSet<>();private final Set<String> warned=ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(PluginThreads.factory("DyDiscord-groups"));
    private volatile Instant nextRecheck=Instant.MIN;private volatile boolean closed;
    public DiscordGroups(Supplier<Settings> settings,Store links,FeatureStore grants,Members members,Writer writer,Supplier<Collection<UUID>> online,Clock clock,Consumer<String> warnings,Consumer<Throwable> errors){
        this.settings=settings;this.links=links;this.grants=grants;this.members=members;this.writer=writer;this.online=online;this.clock=clock;this.warnings=warnings;this.errors=errors;
    }
    public void start(){worker.scheduleWithFixedDelay(()->{try{tick();}catch(Throwable failure){if(!closed)errors.accept(failure);}},1,1,TimeUnit.SECONDS);}
    public void writer(Writer next){writer=next;warned.clear();}
    /** After a reload: recheck online players now and log skipped rules again. */
    public void refresh(){warned.clear();nextRecheck=Instant.MIN;}
    public boolean writerAvailable(){Writer current=writer;return current!=null&&current.available();}
    /** Checks a player soon: on join, link and unlink. Ignored while the feature is off. */
    public void enqueue(UUID uuid){if(closed||!settings.get().features().groups().enabled())return;synchronized(queue){if(queue.size()<MAX_QUEUE)queue.add(uuid);}}
    public int queued(){synchronized(queue){return queue.size();}}
    /** One timer step: the periodic recheck of online linked players, then a few queued players. */
    void tick()throws Exception{
        FeatureSettings.Groups groups=settings.get().features().groups();if(closed)return;
        if(!groups.enabled()){synchronized(queue){queue.clear();}return;}
        Instant now=clock.instant();if(!now.isBefore(nextRecheck)){nextRecheck=now.plusSeconds(groups.recheckSeconds());for(UUID uuid:online.get())enqueue(uuid);}
        List<UUID> batch=new ArrayList<>();synchronized(queue){var it=queue.iterator();while(it.hasNext()&&batch.size()<PER_TICK){batch.add(it.next());it.remove();}}
        for(UUID uuid:batch)try{reconcile(uuid);}catch(Exception failure){errors.accept(failure);}
    }
    /** Brings one player's DyDiscord-granted groups in line with their Discord roles. Deterministic test seam. */
    public void reconcile(UUID uuid)throws Exception{
        FeatureSettings.Groups groups=settings.get().features().groups();Writer current=writer;
        if(!groups.enabled()||current==null||!current.available())return;
        Optional<Store.Link> link=links.byUuid(uuid);Set<String> desired;
        if(link.isEmpty())desired=Set.of();
        else{if(!members.available())return;Set<String> roles=members.roles(link.get().discord());desired=roles==null?Set.of():groups.desired(roles);}
        Set<String> granted=grants.grants(uuid);
        for(String group:granted)if(!desired.contains(group)){current.remove(uuid,group);grants.revoke(uuid,group);}
        for(String group:desired){
            if(granted.contains(group))continue;
            if(!groups.trusted(group)){warn("staff:"+group);continue;}
            Writer.Check check=current.check(group);
            if(check==Writer.Check.MISSING){warn("missing:"+group);continue;}
            if(check==Writer.Check.WILDCARD&&!groups.staffGroups().contains(group)){warn("wildcard:"+group);continue;}
            // A group the player already holds was given by someone else; it is not recorded, so it is never removed.
            if(current.add(uuid,group))grants.grant(uuid,group);
        }
    }
    private void warn(String key){if(warned.add(key))warnings.accept(key);}
    @Override public void close(){closed=true;worker.shutdownNow();synchronized(queue){queue.clear();}}
}
