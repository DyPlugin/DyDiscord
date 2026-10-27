package dev.dydiscord.hook;

import dev.dydiscord.DyDiscord;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Posts DyClaim, DyRTP and DyHomes events to the plugin-logs channel. The other plugins are optional,
 * so their event classes are found at runtime and read through their public API methods.
 */
public final class PluginLogs implements Listener {
    /** A log line before rendering: the event switch, the message key, its values and the embed colour. */
    public record Entry(String event,String key,Map<String,String> values,int color) {}
    private static final int MAX_PENDING=50;
    private final DyDiscord plugin;private final AtomicInteger pending=new AtomicInteger();private final Set<String> hooked=new HashSet<>();
    public PluginLogs(DyDiscord plugin){this.plugin=plugin;}

    /** Hooks every supported plugin that is enabled now; also called when a plugin enables later. */
    public void hookAll(){for(Plugin other:Bukkit.getPluginManager().getPlugins())if(other.isEnabled())hook(other);}
    public void hook(Plugin other){
        String name=other.getName();if(!Set.of("DyClaim","DyRTP","DyHomes").contains(name)||!hooked.add(name))return;
        ClassLoader loader=other.getClass().getClassLoader();int count=0;
        if(name.equals("DyClaim"))for(String event:List.of("ClaimCreateEvent","ClaimRemoveEvent","ClaimTrustChangeEvent","ClaimTransferEvent"))count+=listen(loader,"dev.dyclaim.api.event."+event)?1:0;
        else if(name.equals("DyHomes"))for(String event:List.of("WarpChangeEvent","HomePublicChangeEvent","DyHomesTeleportEvent"))count+=listen(loader,"dev.dyhomes.api.event."+event)?1:0;
        else count+=listen(loader,"dev.dyrtp.api.event.DyRtpPostTeleportEvent")?1:0;
        if(count==0)hooked.remove(name);else plugin.getLogger().info("Discord plugin logs: "+name+" events hooked.");
    }
    /** A plugin that disables loses its listeners; allow hooking it again when it returns. */
    public void unhook(Plugin other){hooked.remove(other.getName());}
    private boolean listen(ClassLoader loader,String className){
        try{
            Class<?> type=Class.forName(className,true,loader);if(!Event.class.isAssignableFrom(type))return false;
            @SuppressWarnings("unchecked") Class<? extends Event> event=(Class<? extends Event>)type;
            Bukkit.getPluginManager().registerEvent(event,this,EventPriority.MONITOR,(listener,fired)->{if(event.isInstance(fired))handle(fired);},plugin,true);
            return true;
        }catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){return false;}
    }
    private void handle(Event fired){
        if(!plugin.ready())return;var settings=plugin.settings().integrations();
        Entry entry;try{entry=entry(fired);}catch(ReflectiveOperationException|RuntimeException unreadable){return;}
        if(entry==null||!settings.logs(entry.event())||plugin.bot()==null||!plugin.bot().usable())return;
        if(pending.incrementAndGet()>MAX_PENDING){pending.decrementAndGet();return;}
        String channelId=settings.logChannel();
        plugin.submit(()->{
            try{TextChannel channel=plugin.bot().channel(channelId);
                if(!channel.getGuild().getSelfMember().hasPermission(channel,Permission.VIEW_CHANNEL,Permission.MESSAGE_SEND,Permission.MESSAGE_EMBED_LINKS))return null;
                channel.sendMessage(dev.dydiscord.discord.PanelView.create(dev.dydiscord.discord.Cards.text(plugin.bot().text(entry.key(),entry.values()),entry.color()))).setAllowedMentions(List.of()).queue(v->{},plugin::error);
            }finally{pending.decrementAndGet();}return null;
        }).exceptionally(e->{plugin.error(e);return null;});
    }
    /** Turns a DyClaim, DyRTP or DyHomes event into a log entry; null for events that are not logged. */
    public static Entry entry(Event fired)throws ReflectiveOperationException{
        String type=fired.getClass().getSimpleName();
        if(type.equals("DyRtpPostTeleportEvent")){
            Player player=(Player)call(fired,"getPlayer");Location to=(Location)call(fired,"getLocation");
            return new Entry("dyrtp.teleports","log-rtp",Map.of("player",player.getName(),"world",to.getWorld()==null?"?":to.getWorld().getName(),"x",Integer.toString(to.getBlockX()),"y",Integer.toString(to.getBlockY()),"z",Integer.toString(to.getBlockZ())),0x55AAFF);
        }
        if(type.equals("WarpChangeEvent")){
            Object warp=call(fired,"getWarp"),sender=call(fired,"getSender");Map<String,String> values=place(call(warp,"location"));
            values.put("warp",String.valueOf(call(warp,"name")));values.put("player",sender==null?"?":String.valueOf(call(sender,"getName")));
            boolean created=(Boolean)call(fired,"isCreated"),deleted=(Boolean)call(fired,"isDeleted");
            return new Entry("dyhomes.warps",deleted?"log-warp-deleted":created?"log-warp-created":"log-warp-moved",values,deleted?0xAAAAAA:0x55AA55);
        }
        if(type.equals("HomePublicChangeEvent")){
            Object home=call(fired,"getHome");Map<String,String> values=new HashMap<>();values.put("home",String.valueOf(call(home,"name")));values.put("owner",name((UUID)call(home,"owner")));
            boolean open=(Boolean)call(home,"publicHome");return new Entry("dyhomes.public-homes",open?"log-home-public":"log-home-private",values,open?0x55AAFF:0xAAAAAA);
        }
        if(type.equals("DyHomesTeleportEvent")){
            Player player=(Player)call(fired,"getPlayer");Location to=(Location)call(fired,"getDestination");Object kind=call(fired,"getKind");
            return new Entry("dyhomes.teleports","log-home-teleport",Map.of("player",player.getName(),"kind",String.valueOf(kind).toLowerCase(Locale.ROOT),"name",String.valueOf(call(fired,"getName")),"world",to.getWorld()==null?"?":to.getWorld().getName()),0x55AAFF);
        }
        if(!type.startsWith("Claim"))return null;
        Object claim=call(fired,"getClaim");Map<String,String> values=new HashMap<>();
        values.put("world",String.valueOf(call(claim,"getWorld")));values.put("x",Integer.toString(((Integer)call(claim,"getChunkX"))*16));values.put("z",Integer.toString(((Integer)call(claim,"getChunkZ"))*16));values.put("owner",String.valueOf(call(claim,"getOwnerName")));
        return switch(type){
            case "ClaimCreateEvent"->new Entry("dyclaim.claims","log-claim-created",values,0x55AA55);
            case "ClaimRemoveEvent"->new Entry("dyclaim.claims","log-claim-removed",values,0xAAAAAA);
            case "ClaimTrustChangeEvent"->{values.put("player",name((UUID)call(fired,"getPlayer")));yield new Entry("dyclaim.trust",(Boolean)call(fired,"isTrusted")?"log-trust-added":"log-trust-removed",values,0xFFAA00);}
            case "ClaimTransferEvent"->{values.put("from",name((UUID)call(fired,"getPreviousOwner")));values.put("to",name((UUID)call(fired,"getNewOwner")));yield new Entry("dyclaim.transfers","log-claim-transferred",values,0xAA55FF);}
            default->null;
        };
    }
    /** World and block coordinates of a DyHomes saved location. */
    private static Map<String,String> place(Object location)throws ReflectiveOperationException{
        Map<String,String> values=new HashMap<>();values.put("world",String.valueOf(call(location,"world")));
        for(String axis:List.of("x","y","z"))values.put(axis,Long.toString((long)Math.floor((Double)call(location,axis))));return values;
    }
    private static String name(UUID id){if(id==null)return "?";String name=Bukkit.getOfflinePlayer(id).getName();return name==null?id.toString().substring(0,8):name;}
    /** Calls a public method through a public declaring type, so implementation classes may stay private. */
    private static Object call(Object target,String method)throws ReflectiveOperationException{
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass()){
            if(java.lang.reflect.Modifier.isPublic(type.getModifiers()))try{return type.getMethod(method).invoke(target);}catch(NoSuchMethodException next){}
            for(Class<?> face:type.getInterfaces())if(java.lang.reflect.Modifier.isPublic(face.getModifiers()))try{return face.getMethod(method).invoke(target);}catch(NoSuchMethodException next){}
        }
        throw new NoSuchMethodException(method);
    }
}
