package dev.dydiscord.alerts;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.FeatureSettings;
import dev.dydiscord.discord.Cards;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import java.time.Instant;
import java.util.*;

/**
 * Server health alerts: low TPS and high memory that last a minute, and online-player milestones. Readings are taken
 * every 30 seconds on the global thread; each kind of alert waits at least cooldown-minutes before it is sent again.
 */
public final class Alerts {
    public enum Kind {TPS,MEMORY,PLAYERS}
    public record Reading(double tps,long usedBytes,long maxBytes,int players) {}
    public record Alert(Kind kind,String key,Map<String,String> values,int color) {}
    /** Two readings in a row, 30 seconds apart. */
    static final int SUSTAINED=2;
    private final DyDiscord plugin;
    private int tpsBreaches,memoryBreaches,milestone;private boolean primed;private final Map<Kind,Instant> sent=new EnumMap<>(Kind.class);
    private dev.dydiscord.util.Tasks.Task task;
    public Alerts(DyDiscord plugin){this.plugin=plugin;}

    /** Starts or stops the readings to match the settings; main thread, after startup and every reload. */
    public synchronized void refresh(){
        boolean wanted=plugin.settings().features().alerts().enabled();
        if(wanted&&task==null)task=plugin.tasks().globalTimer(this::tick,600,600);
        else if(!wanted&&task!=null){task.cancel();task=null;reset();}
    }
    public synchronized void close(){if(task!=null){task.cancel();task=null;}}
    private void tick(){
        if(!plugin.ready()||plugin.bot()==null||!plugin.bot().usable())return;
        FeatureSettings.Alerts settings=plugin.settings().features().alerts();if(!settings.enabled())return;
        Runtime runtime=Runtime.getRuntime();int players=plugin.getServer().getOnlinePlayers().size();
        List<Alert> due=check(new Reading(dev.dydiscord.util.Tps.current(plugin.getServer()),runtime.totalMemory()-runtime.freeMemory(),runtime.maxMemory(),players),settings,plugin.clock().instant());
        for(Alert alert:due)plugin.submit(()->{post(settings,alert);return null;}).exceptionally(e->{plugin.error(e);return null;});
    }
    synchronized void reset(){primed=false;tpsBreaches=0;memoryBreaches=0;milestone=0;sent.clear();}
    /** The alerts a reading causes; updates the sustained counters, milestones and cooldowns. */
    public synchronized List<Alert> check(Reading reading,FeatureSettings.Alerts settings,Instant now){
        List<Alert> due=new ArrayList<>();
        boolean lowTps=settings.lowTps()>0&&reading.tps()>=0&&reading.tps()<settings.lowTps();tpsBreaches=lowTps?tpsBreaches+1:0;
        if(tpsBreaches>=SUSTAINED&&ready(Kind.TPS,settings,now))due.add(new Alert(Kind.TPS,"alert-low-tps",Map.of("tps",String.format(Locale.ROOT,"%.1f",reading.tps()),"threshold",Integer.toString(settings.lowTps())),0xFFAA00));
        int percent=reading.maxBytes()>0?(int)(reading.usedBytes()*100/reading.maxBytes()):0;boolean highMemory=settings.memoryPercent()>0&&reading.maxBytes()>0&&percent>=settings.memoryPercent();memoryBreaches=highMemory?memoryBreaches+1:0;
        if(memoryBreaches>=SUSTAINED&&ready(Kind.MEMORY,settings,now))due.add(new Alert(Kind.MEMORY,"alert-memory",Map.of("percent",Integer.toString(percent),"used",Long.toString(reading.usedBytes()>>20),"max",Long.toString(reading.maxBytes()>>20)),0xFF5555));
        int reached=0;for(int value:settings.milestones())if(reading.players()>=value)reached=value;
        // The first reading after start only sets the baseline, so a restart does not announce a milestone again.
        if(!primed){primed=true;milestone=reached;}
        if(reached<milestone)milestone=reached;
        else if(reached>milestone&&ready(Kind.PLAYERS,settings,now)){milestone=reached;due.add(new Alert(Kind.PLAYERS,"alert-players",Map.of("count",Integer.toString(reading.players()),"milestone",Integer.toString(reached)),0x55AA55));}
        for(Alert alert:due)sent.put(alert.kind(),now);
        return due;
    }
    private boolean ready(Kind kind,FeatureSettings.Alerts settings,Instant now){Instant previous=sent.get(kind);return previous==null||!now.isBefore(previous.plusSeconds(settings.cooldownMinutes()*60L));}
    /** I/O worker: posts one alert card, mentioning only the configured role. Skipped without channel access. */
    void post(FeatureSettings.Alerts settings,Alert alert)throws Exception{
        var bot=plugin.bot();if(bot==null||!bot.usable()||settings.channel().isEmpty())return;
        TextChannel channel=bot.channel(settings.channel());
        if(!channel.getGuild().getSelfMember().hasPermission(channel,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND))return;
        String role=settings.pingRole();String body=(role.isEmpty()?"":"<@&"+role+">\n")+bot.text(alert.key(),alert.values());
        var message=new MessageCreateBuilder().setComponents(Cards.text(body,alert.color())).useComponentsV2().setAllowedMentions(List.of());if(!role.isEmpty())message.mentionRoles(role);
        bot.await(channel.sendMessage(message.build()));
    }
}
