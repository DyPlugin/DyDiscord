package dev.dydiscord.moderation;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.ModerationSettings;
import dev.dydiscord.discord.StaffNotices;
import dev.dydiscord.storage.Store;
import dev.dydiscord.util.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /cheatcheck: tells a player on screen, in chat and with a sound that staff are checking them, and asks a linked
 * player on Discord to join the voice channel. Optionally freezes the player until the check ends.
 */
public final class CheatCheck implements TabExecutor,Listener {
    public record Session(UUID target,String name,String staff,Instant started,Tasks.Task reminder) {}
    static final Set<String> END=Set.of("end","stop","bitir","bitti","beenden","terminar","fin","конец","завершить");
    static final Set<String> LIST=Set.of("list","liste","lista","список");
    private final DyDiscord plugin;private final Map<UUID,Session> sessions=new ConcurrentHashMap<>();
    public CheatCheck(DyDiscord plugin){this.plugin=plugin;}
    public boolean active(UUID player){return sessions.containsKey(player);}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!plugin.ready()){plugin.message(sender,"busy",Map.of());return true;}
        if(!sender.hasPermission("dydiscord.cheatcheck")){plugin.message(sender,"no-permission",Map.of());return true;}
        if(args.length==1&&LIST.contains(args[0].toLowerCase(Locale.ROOT))){list(sender);return true;}
        if(args.length<1||args.length>2||args.length==2&&!END.contains(args[1].toLowerCase(Locale.ROOT))){plugin.message(sender,"cheatcheck-usage",Map.of());return true;}
        Player target=Bukkit.getPlayerExact(args[0]);
        if(target==null){Session known=sessions.values().stream().filter(s->s.name().equalsIgnoreCase(args[0])).findFirst().orElse(null);if(args.length==2&&known!=null){end(sender,known.target());return true;}plugin.message(sender,"cheatcheck-not-found",Map.of("player",args[0]));return true;}
        if(args.length==2)end(sender,target.getUniqueId());else start(sender,target);
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("dydiscord.cheatcheck"))return List.of();
        if(args.length==1){List<String> names=new ArrayList<>();for(Player p:Bukkit.getOnlinePlayers())if(p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))names.add(p.getName());if("list".startsWith(args[0].toLowerCase(Locale.ROOT)))names.add("list");return names;}
        if(args.length==2&&"end".startsWith(args[1].toLowerCase(Locale.ROOT)))return List.of(plugin.language(sender).equals("tr")?"bitir":"end");
        return List.of();
    }

    void start(CommandSender sender,Player target){
        if(target.hasPermission("dydiscord.cheatcheck.exempt")){plugin.message(sender,"cheatcheck-exempt",Map.of("player",target.getName()));return;}
        ModerationSettings settings=plugin.settings().moderation();UUID uuid=target.getUniqueId();String name=target.getName(),staff=sender.getName();
        long period=settings.reminderSeconds()*20L;
        Tasks.Task reminder=period>0?plugin.tasks().entityTimer(target,()->remind(target),1,period):plugin.tasks().entity(target,()->remind(target),1);
        if(sessions.putIfAbsent(uuid,new Session(uuid,name,staff,plugin.clock().instant(),reminder))!=null){reminder.cancel();plugin.message(sender,"cheatcheck-already",Map.of("player",name));return;}
        Optional<Store.Link> cached=plugin.cachedLink(uuid);
        plugin.message(target,cached.isPresent()?"cheatcheck-linked":"cheatcheck-unlinked",Map.of("staff",staff));
        staff("cheatcheck-notify",Map.of("staff",staff,"player",name),sender);
        plugin.message(sender,"cheatcheck-started",Map.of("player",name));
        plugin.submit(()->{
            Optional<Store.Link> link=plugin.store().byUuid(uuid);var bot=plugin.bot();
            if(link.isEmpty()){String invite=bot==null?"":bot.invitation();if(!invite.isEmpty())plugin.message(target,"invite",Map.of("invite_url",invite));}
            StaffNotices.Delivery delivery=link.isEmpty()||bot==null?null:bot.staff().cheatCheck(link.get().discord(),name,staff);
            String result=delivery==null?"unlinked":delivery.name().toLowerCase(Locale.ROOT);
            plugin.message(sender,"cheatcheck-delivery-"+result,Map.of("player",name));
            if(bot!=null)bot.staff().log(bot.staff().checkLogChannel(),bot.text("log-cheatcheck-started",Map.of("staff",staff,"player",name,"delivery",bot.text("cheatcheck-delivery-"+result,Map.of()))),0xFFAA00);
            return null;
        }).exceptionally(e->{plugin.error(e);return null;});
    }
    private void remind(Player target){
        if(!target.isOnline()||!sessions.containsKey(target.getUniqueId()))return;
        String lang=plugin.language(target);boolean linked=plugin.cachedLink(target.getUniqueId()).isPresent();
        target.sendTitle(plugin.messages().render(false,lang,"cheatcheck-title",Map.of()),plugin.messages().render(false,lang,linked?"cheatcheck-subtitle-linked":"cheatcheck-subtitle-unlinked",Map.of()),5,70,10);
        String sound=plugin.settings().moderation().sound();if(!sound.isEmpty())try{target.playSound(target.getLocation(),sound,1f,1f);}catch(RuntimeException unknown){}
    }
    void end(CommandSender sender,UUID uuid){
        Session session=sessions.remove(uuid);
        if(session==null){Player online=Bukkit.getPlayer(uuid);plugin.message(sender,"cheatcheck-none",Map.of("player",online==null?uuid.toString():online.getName()));return;}
        session.reminder().cancel();Player target=Bukkit.getPlayer(uuid);
        if(target!=null){plugin.tasks().entity(target,target::resetTitle,1);plugin.message(target,"cheatcheck-ended-player",Map.of());}
        plugin.message(sender,"cheatcheck-ended",Map.of("player",session.name()));
        long minutes=Duration.between(session.started(),plugin.clock().instant()).toMinutes();
        log("log-cheatcheck-ended",Map.of("staff",sender.getName(),"player",session.name(),"minutes",Long.toString(minutes)),0x55AA55);
    }
    private void list(CommandSender sender){
        if(sessions.isEmpty()){plugin.message(sender,"cheatcheck-list-empty",Map.of());return;}
        List<String> names=sessions.values().stream().map(Session::name).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        plugin.message(sender,"cheatcheck-list",Map.of("players",String.join(", ",names)));
    }
    private void staff(String key,Map<String,String> values,CommandSender except){
        for(Player p:Bukkit.getOnlinePlayers())if(p!=except&&p.hasPermission("dydiscord.cheatcheck.notify"))plugin.message(p,key,values);
    }
    private void log(String key,Map<String,String> values,int color){
        var bot=plugin.bot();if(bot==null)return;
        plugin.submit(()->{bot.staff().log(bot.staff().checkLogChannel(),bot.text(key,values),color);return null;}).exceptionally(e->{plugin.error(e);return null;});
    }

    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent event){
        if(!frozen(event.getPlayer()))return;Location from=event.getFrom(),to=event.getTo();
        if(to==null||from.getX()==to.getX()&&from.getY()==to.getY()&&from.getZ()==to.getZ())return;
        Location back=from.clone();back.setYaw(to.getYaw());back.setPitch(to.getPitch());event.setTo(back);
    }
    @EventHandler(ignoreCancelled=true) public void teleport(PlayerTeleportEvent event){
        if(frozen(event.getPlayer())&&Set.of("ENDER_PEARL","CHORUS_FRUIT","CONSUMABLE_EFFECT").contains(event.getCause().name()))event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.LOWEST) public void command(PlayerCommandPreprocessEvent event){
        if(!frozen(event.getPlayer()))return;
        String word=event.getMessage().length()<2?"":event.getMessage().substring(1).split(" ",2)[0].toLowerCase(Locale.ROOT);
        String plain=word.contains(":")?word.substring(word.indexOf(':')+1):word;Set<String> allowed=plugin.settings().moderation().allowedCommands();
        if(allowed.contains(word)||allowed.contains(plain))return;
        event.setCancelled(true);plugin.message(event.getPlayer(),"cheatcheck-frozen",Map.of());
    }
    @EventHandler public void quit(PlayerQuitEvent event){
        Session session=sessions.remove(event.getPlayer().getUniqueId());if(session==null)return;session.reminder().cancel();
        staff("cheatcheck-quit",Map.of("player",session.name(),"staff",session.staff()),null);
        log("log-cheatcheck-quit",Map.of("player",session.name(),"staff",session.staff()),0xFF5555);
        List<String> commands=plugin.settings().moderation().quitCommands();if(commands.isEmpty())return;
        String name=session.name(),uuid=session.target().toString();
        plugin.onMain(()->{for(String command:commands)try{plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),command.replace("{player}",name).replace("{uuid}",uuid));}catch(RuntimeException failure){plugin.error(failure);}});
    }
    private boolean frozen(Player player){return sessions.containsKey(player.getUniqueId())&&plugin.ready()&&plugin.settings().moderation().freeze();}
    public void close(){for(Session session:sessions.values())session.reminder().cancel();sessions.clear();}
}
