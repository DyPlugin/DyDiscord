package dev.dydiscord.discord;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.IntegrationSettings;
import dev.dydiscord.language.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.scheduler.BukkitTask;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One status message per server, edited in place; it is recreated only when it was deleted. */
public final class StatusCard {
    public record Snapshot(ServerInfo.Snapshot server,double tps) {}
    private final DyDiscord plugin;private final AtomicBoolean publishing=new AtomicBoolean();
    private dev.dydiscord.util.Tasks.Task task;private int interval;
    public StatusCard(DyDiscord plugin){this.plugin=plugin;}

    /** Applies the current settings; call on the main thread after startup and every reload. */
    public synchronized void refresh(){
        IntegrationSettings settings=plugin.settings().integrations();
        if(!settings.statusCard()){cancel();return;}
        if(task!=null&&interval==settings.statusInterval())return;
        cancel();interval=settings.statusInterval();long ticks=interval*20L;
        task=plugin.tasks().globalTimer(this::tick,100,ticks);
    }
    public synchronized void cancel(){if(task!=null){task.cancel();task=null;}}
    private void tick(){
        if(!plugin.ready()||!plugin.settings().integrations().statusCard()||plugin.bot()==null||!plugin.bot().usable()||!publishing.compareAndSet(false,true))return;
        Snapshot snapshot=new Snapshot(ServerInfo.capture(plugin),tps());
        plugin.submit(()->{try{publish(snapshot,true);}finally{publishing.set(false);}return null;}).exceptionally(e->{publishing.set(false);plugin.error(e);return null;});
    }
    /** Marks the card offline during a normal shutdown, within two seconds. */
    public void offline(){
        cancel();if(plugin.settings()==null||!plugin.settings().integrations().statusCard()||plugin.bot()==null||!plugin.bot().usable())return;
        Snapshot snapshot;try{snapshot=new Snapshot(ServerInfo.capture(plugin),-1);}catch(RuntimeException e){return;}
        var worker=Executors.newSingleThreadExecutor(dev.dydiscord.util.PluginThreads.factory("DyDiscord-status-card"));
        try{worker.submit(()->{publish(snapshot,false);return null;}).get(2,TimeUnit.SECONDS);}
        catch(Exception failure){plugin.getLogger().info("The status card could not be marked offline before shutdown.");}
        finally{worker.shutdownNow();}
    }
    private void publish(Snapshot snapshot,boolean online)throws Exception{
        String channelId=plugin.settings().integrations().statusChannel();TextChannel channel=plugin.bot().channel(channelId);
        if(!channel.getGuild().getSelfMember().hasPermission(channel,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND,Permission.MESSAGE_EMBED_LINKS))throw new IllegalStateException("Status card channel permissions unavailable");
        MessageEmbed card=card(plugin.messages(),plugin.bot().activeLanguage(),snapshot,online,Instant.now());
        Optional<String[]> stored=plugin.store().statusCard();
        if(stored.isPresent()&&stored.get()[0].equals(channelId)){
            try{plugin.bot().await(channel.editMessageById(stored.get()[1],PanelView.update(Cards.of(card))).setAllowedMentions(List.of()));return;}
            catch(ErrorResponseException missing){if(missing.getErrorResponse()!=ErrorResponse.UNKNOWN_MESSAGE)throw missing;}
        }
        String id=plugin.bot().await(channel.sendMessage(PanelView.create(Cards.of(card))).setAllowedMentions(List.of())).getId();plugin.store().statusCard(channelId,id);
    }
    public static MessageEmbed card(Messages messages,String lang,Snapshot snapshot,boolean online,Instant now){
        ServerInfo.Snapshot server=snapshot.server();
        EmbedBuilder card=new EmbedBuilder().setColor(online?0x55AA55:0xFF5555).setTimestamp(now)
            .setTitle(messages.render(true,lang,"server-info-title",Map.of("server_name",server.serverName())))
            .addField(messages.get(true,lang,"status-card-state"),messages.get(true,lang,online?"status-card-online":"status-card-offline"),true);
        if(online){
            card.addField(messages.get(true,lang,"server-info-players"),server.online()+"/"+server.maximum(),true);
            card.addField(messages.get(true,lang,"server-info-version"),Messages.safeDiscord(server.version()),true);
            if(snapshot.tps()>=0)card.addField(messages.get(true,lang,"status-card-tps"),String.format(Locale.ROOT,"%.1f",Math.min(20,snapshot.tps())),true);
        }
        return card.build();
    }
    /** Paper and its forks report TPS; Spigot does not, so the field is left out there. */
    private double tps(){try{double[] values=(double[])plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());return values.length>0?values[0]:-1;}catch(ReflectiveOperationException|RuntimeException unavailable){return -1;}}
}
