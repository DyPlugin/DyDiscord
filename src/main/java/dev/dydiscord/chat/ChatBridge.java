package dev.dydiscord.chat;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.*;
import dev.dydiscord.discord.IngressLimit;
import dev.dydiscord.util.PluginThreads;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bukkit.entity.Player;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Ephemeral queues only. No retry/replay of chat and no console execution path. */
public final class ChatBridge implements AutoCloseable {
    public record Outgoing(String text,Integer color,long policy,long session,Instant expires) {}
    record Context(Settings settings,long policy,long session,Instant expires,long epoch) {}
    private record Queued(Outgoing message,Settings settings,UUID sender,long epoch) {}
    private record Incoming(String role,int color,String name,String message,String source,long policy,long session,Instant expires,Settings settings,long epoch) {}
    private final DyDiscord plugin;private final Ranks ranks;private final Clock clock;private final IngressLimit incomingRate,outgoingRate;
    private final ArrayBlockingQueue<Queued> outbound=new ArrayBlockingQueue<>(128);private final ArrayBlockingQueue<Incoming> inbound=new ArrayBlockingQueue<>(64);
    private final Map<String,Instant> received=new LinkedHashMap<>();private final AtomicLong policy=new AtomicLong();private final AtomicBoolean drainScheduled=new AtomicBoolean();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(PluginThreads.factory("DyDiscord-chat"));
    private final ChatPreferences preferences;private final ChatNotifications notifications;
    private volatile boolean inboundPaused,outboundPaused;private volatile Instant nextSend=Instant.EPOCH;
    private final AtomicLong inboundEpoch=new AtomicLong(),outboundEpoch=new AtomicLong();
    private final AtomicReference<Instant> preferenceWarning=new AtomicReference<>(Instant.EPOCH);
    private volatile boolean closed;private volatile Instant pause=Instant.EPOCH;private Instant lastWarning=Instant.EPOCH;
    public ChatBridge(DyDiscord plugin){this.plugin=plugin;ranks=new Ranks(plugin);clock=plugin.clock();incomingRate=new IngressLimit(clock);outgoingRate=new IngressLimit(clock);preferences=new ChatPreferences(plugin.getDataFolder().toPath().resolve("chat-preferences.db"));notifications=new ChatNotifications(clock);}
    public void start(){for(Player player:plugin.getServer().getOnlinePlayers())load(player.getUniqueId());worker.scheduleWithFixedDelay(this::flush,500,500,TimeUnit.MILLISECONDS);}
    public CompletableFuture<ChatPreferences.Preference> preference(UUID id){return preferences.get(id);}
    public CompletableFuture<ChatPreferences.Preference> preference(UUID id,String key,boolean enabled){return preferences.set(id,key,enabled);}
    public ChatPreferences.Preference preferencePreview(UUID id){return preferences.peek(id);}
    public void load(UUID id){preferences.get(id).exceptionally(failure->{Instant previous=preferenceWarning.get(),now=clock.instant();if(!now.isBefore(previous.plusSeconds(60))&&preferenceWarning.compareAndSet(previous,now))plugin.getLogger().warning("Chat preferences unavailable; player chat bridging withheld.");return null;});}
    public void leave(UUID id){preferences.leave(id);notifications.leave(id);outbound.removeIf(queued->id.equals(queued.sender()));}
    public boolean paused(String direction){return switch(direction){case "inbound"->inboundPaused;case "outbound"->outboundPaused;case "both"->inboundPaused&&outboundPaused;default->false;};}
    public boolean pause(String direction,boolean value){if(!Set.of("inbound","outbound","both").contains(direction))return false;if(!direction.equals("outbound")){inboundPaused=value;inboundEpoch.incrementAndGet();inbound.clear();}if(!direction.equals("inbound")){outboundPaused=value;outboundEpoch.incrementAndGet();outbound.clear();}return true;}
    public void invalidate(){policy.incrementAndGet();outbound.clear();inbound.clear();incomingRate.clear();outgoingRate.clear();synchronized(received){received.clear();}}
    public boolean current(Outgoing message){return !closed&&plugin.ready()&&message.policy()==policy.get()&&plugin.bot().alive(message.session())&&plugin.bot().usable()&&clock.instant().isBefore(message.expires())&&!plugin.settings().chat().channel().isEmpty();}
    Context capture(){return new Context(plugin.settings(),policy.get(),plugin.bot().generation(),clock.instant().plusSeconds(5),outboundEpoch.get());}
    boolean current(Context context){return context.epoch()==outboundEpoch.get()&&context.settings().equals(plugin.settings())&&current(new Outgoing("",null,context.policy(),context.session(),context.expires()));}
    private boolean current(Queued queued){ChatPreferences.Preference pref=queued.sender()==null?null:preferences.peek(queued.sender());return !outboundPaused&&queued.epoch()==outboundEpoch.get()&&current(queued.message())&&queued.settings().equals(plugin.settings())&&(queued.sender()==null||pref!=null&&pref.outbound());}
    public static boolean publicChat(boolean cancelled,boolean hidden,boolean muted,Set<UUID> recipients,Set<UUID> publicPlayers){return !cancelled&&!hidden&&!muted&&!publicPlayers.isEmpty()&&recipients.containsAll(publicPlayers);}
    public static boolean hidden(Player player){return flag(player,"vanished")||flag(player,"hidden")||flag(player,"dydiscord-hidden");}
    public static boolean muted(Player player){return flag(player,"muted")||flag(player,"dydiscord-muted");}
    private static boolean flag(Player player,String key){return player.getMetadata(key).stream().anyMatch(value->value.asBoolean());}
    public boolean visible(Player player){if(hidden(player))return false;for(Player viewer:plugin.getServer().getOnlinePlayers())if(!viewer.getUniqueId().equals(player.getUniqueId())&&!viewer.canSee(player))return false;return true;}
    public boolean publicDeath(Player player,String death){
        if(!visible(player)||player.getKiller()!=null&&!visible(player.getKiller()))return false;
        var board=player.getScoreboard();var team=board==null?null:board.getEntryTeam(player.getName());if(team!=null&&team.getOption(org.bukkit.scoreboard.Team.Option.DEATH_MESSAGE_VISIBILITY)!=org.bukkit.scoreboard.Team.OptionStatus.ALWAYS)return false;
        String reason=ChatText.plain(death,1200);for(Player participant:plugin.getServer().getOnlinePlayers())if(!visible(participant)&&reason.contains(participant.getName()))return false;return true;
    }
    public void minecraft(Player player,String message){minecraft(player,message,capture());}
    void minecraft(Player player,String message,Context context){ChatPreferences.Preference pref=preferences.peek(player.getUniqueId());if(pref==null){load(player.getUniqueId());return;}if(outboundPaused||!pref.outbound()||!current(context)||!context.settings().chat().toDiscord()||!player.isOnline()||!visible(player)||muted(player)||!player.hasPermission("dydiscord.chat.send")||!outgoingRate.admit(player.getUniqueId().toString()))return;String content=ChatText.plain(message,800);if(content.isEmpty())return;offer(ChatText.discord(template("chat-to-discord"),ranks.rank(player),player.getName(),content),null,context,player.getUniqueId());}
    public void event(String event,Player player,String death){event(event,player,death,capture());}
    void event(String event,Player player,String death,Context context){if(!current(context))return;ChatSettings s=context.settings().chat();boolean enabled=switch(event){case "join"->s.join();case "quit"->s.quit();case "death"->s.death();default->false;};if(!enabled||!visible(player))return;int color=switch(event){case "join"->s.joinColor();case "quit"->s.quitColor();default->s.deathColor();};offer(ChatText.event(template("chat-"+event),player.getName(),death),color,context);}
    private String template(String key){String lang=plugin.bot().activeLanguage();return plugin.messages().get(true,lang,key);}
    public boolean offer(String text,Integer color){
        return offer(text,color,capture());
    }
    private boolean offer(String text,Integer color,Context context){
        return offer(text,color,context,null);
    }
    private boolean offer(String text,Integer color,Context context,UUID sender){
        long admittedPolicy=context.policy();Settings admittedSettings=context.settings();
        if(closed||outboundPaused||!plugin.ready()||!plugin.bot().usable()||admittedSettings.chat().channel().isEmpty()||clock.instant().isBefore(pause)){outbound.clear();return false;}
        if(!current(context)||text.isBlank()||text.length()>1900)return false;return outbound.offer(new Queued(new Outgoing(text,color,admittedPolicy,context.session(),context.expires()),admittedSettings,sender,context.epoch()));
    }
    public synchronized void flush(){
        outbound.removeIf(queued->!current(queued));
        if(clock.instant().isBefore(nextSend))return;
        Queued queued=outbound.poll();if(queued==null)return;
        if(!current(queued)){if(!plugin.bot().usable())invalidate();return;}
        try{
            plugin.bot().sendChat(queued.message(),()->current(queued));
            nextSend=clock.instant().plusSeconds(Math.max(0,plugin.bot().chatSlowmodeSeconds()));
        }catch(Exception failure){
            invalidate();pause=clock.instant().plusSeconds(15);
            if(!clock.instant().isBefore(lastWarning.plusSeconds(60))){lastWarning=clock.instant();plugin.getLogger().warning("Chat delivery unavailable; queued messages discarded (values withheld).");}
        }
    }
    public static Role highestRole(List<Role> ordered){return ordered.stream().filter(role->!role.isPublicRole()&&!role.isManaged()).max(Comparator.comparingInt(Role::getPosition)).orElse(null);}
    public static boolean human(boolean bot,boolean webhook,boolean fresh,String content,String prefix){return !bot&&!webhook&&fresh&&!content.isBlank()&&!content.startsWith(prefix);}
    public void receive(MessageReceivedEvent event,long session){
        long admittedPolicy=policy.get(),epoch=inboundEpoch.get();Settings settings=plugin.settings();String raw=event.getMessage().getContentRaw();Instant created=event.getMessage().getTimeCreated().toInstant();
        if(closed||inboundPaused||!settings.chat().toMinecraft()||!event.isFromGuild()||!event.getGuild().getId().equals(plugin.bot().applied().guild())||!event.getChannel().getId().equals(settings.chat().channel())||event.getMember()==null||!human(event.getAuthor().isBot(),event.isWebhookMessage(),!created.isBefore(clock.instant().minusSeconds(5))&&!created.isAfter(clock.instant().plusSeconds(5)),raw,settings.botPrefix())||!plugin.bot().content()||!plugin.bot().alive(session))return;
        synchronized(received){received.values().removeIf(time->!clock.instant().isBefore(time));if(received.containsKey(event.getMessageId())||received.size()>=4096)return;received.put(event.getMessageId(),clock.instant().plusSeconds(60));}
        if(!incomingRate.admit(event.getAuthor().getId()))return;Role role=highestRole(event.getMember().getRoles());String content=ChatText.plain(event.getMessage().getContentDisplay(),800);if(content.isEmpty())return;
        Message referenced=event.getMessage().getReferencedMessage();String display=referenced==null?content:ChatText.reply(referenced.getAuthor().getName(),content);
        Incoming message=new Incoming(role==null?"":ChatText.plain(role.getName(),64),role==null?0:role.getColorRaw(),ChatText.plain(event.getMember().getEffectiveName(),64),display,content,admittedPolicy,session,clock.instant().plusSeconds(5),settings,epoch);
        if(closed||inboundPaused||epoch!=inboundEpoch.get()||admittedPolicy!=policy.get()||!settings.equals(plugin.settings())||!plugin.bot().alive(session))return;
        if(inbound.offer(message)&&drainScheduled.compareAndSet(false,true))plugin.onMain(this::drain);
    }
    private void drain(){
        try{
            for(int i=0;i<64;i++){
                Incoming message=inbound.poll();if(message==null)break;
                if(closed||inboundPaused||message.epoch()!=inboundEpoch.get()||!plugin.ready()||!plugin.settings().chat().toMinecraft()||message.policy()!=policy.get()||!message.settings().equals(plugin.settings())||!plugin.bot().alive(message.session())||!plugin.bot().usable()||!clock.instant().isBefore(message.expires()))continue;
                var text=ChatText.minecraft(template("chat-to-minecraft"),message.role(),message.color(),message.name(),message.message());
                for(Player player:plugin.getServer().getOnlinePlayers()){
                    ChatPreferences.Preference pref=preferences.peek(player.getUniqueId());
                    if(pref==null){load(player.getUniqueId());continue;}
                    if(pref.inbound()&&player.hasPermission("dydiscord.chat.receive")){
                        player.spigot().sendMessage(text);
                        if(pref.notifications()&&ChatText.names(message.source(),player.getName()))notifyName(player);
                    }
                }
            }
        }finally{drainScheduled.set(false);if(!inbound.isEmpty()&&drainScheduled.compareAndSet(false,true))plugin.onMain(this::drain);}
    }
    private void notifyName(Player player){
        if(notifications.admit(player.getUniqueId()))player.playSound(player.getLocation(),org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,1f,1f);
    }
    public int queued(){return outbound.size()+inbound.size();}
    @Override public void close(){closed=true;invalidate();worker.shutdownNow();preferences.close();notifications.clear();}
}
