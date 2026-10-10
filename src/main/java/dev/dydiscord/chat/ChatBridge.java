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
    record Context(Settings settings,long policy,long session,Instant expires) {}
    private record Queued(Outgoing message,Settings settings) {}
    private record Incoming(String role,int color,String name,String message,long policy,long session,Instant expires,Settings settings) {}
    private final DyDiscord plugin;private final Ranks ranks;private final Clock clock;private final IngressLimit incomingRate,outgoingRate;
    private final ArrayBlockingQueue<Queued> outbound=new ArrayBlockingQueue<>(128);private final ArrayBlockingQueue<Incoming> inbound=new ArrayBlockingQueue<>(64);
    private final Map<String,Instant> received=new LinkedHashMap<>();private final AtomicLong policy=new AtomicLong();private final AtomicBoolean drainScheduled=new AtomicBoolean();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(PluginThreads.factory("DyDiscord-chat"));
    private volatile boolean closed;private volatile Instant pause=Instant.EPOCH;private Instant lastWarning=Instant.EPOCH;
    public ChatBridge(DyDiscord plugin){this.plugin=plugin;ranks=new Ranks(plugin);clock=plugin.clock();incomingRate=new IngressLimit(clock);outgoingRate=new IngressLimit(clock);}
    public void start(){worker.scheduleWithFixedDelay(this::flush,500,500,TimeUnit.MILLISECONDS);}
    public void invalidate(){policy.incrementAndGet();outbound.clear();inbound.clear();incomingRate.clear();outgoingRate.clear();synchronized(received){received.clear();}}
    public boolean current(Outgoing message){return !closed&&plugin.ready()&&message.policy()==policy.get()&&plugin.bot().alive(message.session())&&plugin.bot().usable()&&clock.instant().isBefore(message.expires())&&!plugin.settings().chat().channel().isEmpty();}
    Context capture(){return new Context(plugin.settings(),policy.get(),plugin.bot().generation(),clock.instant().plusSeconds(5));}
    boolean current(Context context){return context.settings().equals(plugin.settings())&&current(new Outgoing("",null,context.policy(),context.session(),context.expires()));}
    private boolean current(Queued queued){return current(queued.message())&&queued.settings().equals(plugin.settings());}
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
    void minecraft(Player player,String message,Context context){if(!current(context)||!context.settings().chat().toDiscord()||!player.isOnline()||!visible(player)||muted(player)||!player.hasPermission("dydiscord.chat.send")||!outgoingRate.admit(player.getUniqueId().toString()))return;String content=ChatText.plain(message,800);if(content.isEmpty())return;offer(ChatText.discord(template("chat-to-discord"),ranks.rank(player),player.getName(),content),null,context);}
    public void event(String event,Player player,String death){event(event,player,death,capture());}
    void event(String event,Player player,String death,Context context){if(!current(context))return;ChatSettings s=context.settings().chat();boolean enabled=switch(event){case "join"->s.join();case "quit"->s.quit();case "death"->s.death();default->false;};if(!enabled||!visible(player))return;int color=switch(event){case "join"->s.joinColor();case "quit"->s.quitColor();default->s.deathColor();};offer(ChatText.event(template("chat-"+event),player.getName(),death),color,context);}
    private String template(String key){String lang=plugin.bot().activeLanguage();return plugin.messages().get(true,lang,key);}
    public boolean offer(String text,Integer color){
        return offer(text,color,capture());
    }
    private boolean offer(String text,Integer color,Context context){
        long admittedPolicy=context.policy();Settings admittedSettings=context.settings();
        if(closed||!plugin.ready()||!plugin.bot().usable()||admittedSettings.chat().channel().isEmpty()||clock.instant().isBefore(pause)){outbound.clear();return false;}
        if(!current(context)||text.isBlank()||text.length()>1900)return false;return outbound.offer(new Queued(new Outgoing(text,color,admittedPolicy,context.session(),context.expires()),admittedSettings));
    }
    public void flush(){Queued queued=outbound.poll();if(queued==null)return;if(!current(queued)){if(!plugin.bot().usable())invalidate();return;}try{plugin.bot().sendChat(queued.message(),()->current(queued));}catch(Exception failure){invalidate();pause=clock.instant().plusSeconds(15);if(!clock.instant().isBefore(lastWarning.plusSeconds(60))){lastWarning=clock.instant();plugin.getLogger().warning("Chat delivery unavailable; queued messages discarded (values withheld).");}}}
    public static Role highestRole(List<Role> ordered){return ordered.stream().filter(role->!role.isPublicRole()&&!role.isManaged()).max(Comparator.comparingInt(Role::getPosition)).orElse(null);}
    public static boolean human(boolean bot,boolean webhook,boolean fresh,String content,String prefix){return !bot&&!webhook&&fresh&&!content.isBlank()&&!content.startsWith(prefix);}
    public void receive(MessageReceivedEvent event,long session){
        long admittedPolicy=policy.get();Settings settings=plugin.settings();String raw=event.getMessage().getContentRaw();Instant created=event.getMessage().getTimeCreated().toInstant();
        if(closed||!settings.chat().toMinecraft()||!event.isFromGuild()||!event.getGuild().getId().equals(plugin.bot().applied().guild())||!event.getChannel().getId().equals(settings.chat().channel())||event.getMember()==null||!human(event.getAuthor().isBot(),event.isWebhookMessage(),!created.isBefore(clock.instant().minusSeconds(5))&&!created.isAfter(clock.instant().plusSeconds(5)),raw,settings.botPrefix())||!plugin.bot().content()||!plugin.bot().alive(session))return;
        synchronized(received){received.values().removeIf(time->!clock.instant().isBefore(time));if(received.containsKey(event.getMessageId())||received.size()>=4096)return;received.put(event.getMessageId(),clock.instant().plusSeconds(60));}
        if(!incomingRate.admit(event.getAuthor().getId()))return;Role role=highestRole(event.getMember().getRoles());String content=ChatText.plain(event.getMessage().getContentDisplay(),800);if(content.isEmpty())return;
        Incoming message=new Incoming(role==null?"":ChatText.plain(role.getName(),64),role==null?0:role.getColorRaw(),ChatText.plain(event.getMember().getEffectiveName(),64),content,admittedPolicy,session,clock.instant().plusSeconds(5),settings);
        if(closed||admittedPolicy!=policy.get()||!settings.equals(plugin.settings())||!plugin.bot().alive(session))return;
        if(inbound.offer(message)&&drainScheduled.compareAndSet(false,true))plugin.onMain(this::drain);
    }
    private void drain(){try{for(int i=0;i<64;i++){Incoming message=inbound.poll();if(message==null)break;if(closed||!plugin.ready()||!plugin.settings().chat().toMinecraft()||message.policy()!=policy.get()||!message.settings().equals(plugin.settings())||!plugin.bot().alive(message.session())||!plugin.bot().usable()||!clock.instant().isBefore(message.expires()))continue;var text=ChatText.minecraft(template("chat-to-minecraft"),message.role(),message.color(),message.name(),message.message());for(Player player:plugin.getServer().getOnlinePlayers())if(player.hasPermission("dydiscord.chat.receive"))player.spigot().sendMessage(text);}}finally{drainScheduled.set(false);if(!inbound.isEmpty()&&drainScheduled.compareAndSet(false,true))plugin.onMain(this::drain);}}
    public int queued(){return outbound.size()+inbound.size();}
    @Override public void close(){closed=true;invalidate();worker.shutdownNow();}
}
