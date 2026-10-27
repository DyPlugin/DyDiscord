package dev.dydiscord.moderation;

import dev.dydiscord.DyDiscord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** /report: tells online staff and posts the report to the Discord reports channel. */
public final class Reports implements TabExecutor {
    static final int MAX_REASON=200;
    private final DyDiscord plugin;private final Map<String,Instant> last=new ConcurrentHashMap<>();
    public Reports(DyDiscord plugin){this.plugin=plugin;}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!plugin.ready()){plugin.message(sender,"busy",Map.of());return true;}
        if(!sender.hasPermission("dydiscord.report")){plugin.message(sender,"no-permission",Map.of());return true;}
        if(args.length<2){plugin.message(sender,"player-report-usage",Map.of());return true;}
        Player target=Bukkit.getPlayerExact(args[0]);
        if(target==null){plugin.message(sender,"player-report-not-found",Map.of("player",args[0]));return true;}
        if(target.getName().equalsIgnoreCase(sender.getName())){plugin.message(sender,"player-report-self",Map.of());return true;}
        String reason=String.join(" ",Arrays.copyOfRange(args,1,args.length)).replaceAll("\\p{Cntrl}"," ").strip();
        if(reason.length()>MAX_REASON)reason=reason.substring(0,MAX_REASON);
        String key=plugin.actor(sender);Instant now=plugin.clock().instant();int cooldown=plugin.settings().moderation().reportCooldown();
        Instant previous=last.get(key);
        if(cooldown>0&&previous!=null&&now.isBefore(previous.plusSeconds(cooldown))&&!sender.hasPermission("dydiscord.report.notify")){
            plugin.message(sender,"player-report-cooldown",Map.of("seconds",Long.toString(previous.plusSeconds(cooldown).getEpochSecond()-now.getEpochSecond())));return true;}
        last.put(key,now);last.values().removeIf(time->now.isAfter(time.plusSeconds(Math.max(cooldown,1))));
        Location at=target.getLocation();
        Map<String,String> values=Map.of("reporter",sender.getName(),"player",target.getName(),"reason",reason,"world",at.getWorld()==null?"?":at.getWorld().getName(),"x",Integer.toString(at.getBlockX()),"y",Integer.toString(at.getBlockY()),"z",Integer.toString(at.getBlockZ()));
        for(Player staff:Bukkit.getOnlinePlayers())if(staff.hasPermission("dydiscord.report.notify"))plugin.message(staff,"player-report-staff",values);
        var bot=plugin.bot();
        if(bot==null){plugin.message(sender,"player-report-sent",Map.of());return true;}
        plugin.submit(()->{boolean posted=bot.staff().report(bot.text("log-report",values)+"\n"+bot.text("log-report-location",values));
            plugin.message(sender,"player-report-sent",Map.of());if(!posted&&!plugin.settings().moderation().reportChannel().isEmpty())plugin.getLogger().warning("A player report could not be posted to Discord; check the reports channel and bot permissions.");return null;
        }).exceptionally(e->{plugin.error(e);plugin.message(sender,"player-report-sent",Map.of());return null;});
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length!=1||!sender.hasPermission("dydiscord.report"))return List.of();
        List<String> names=new ArrayList<>();for(Player p:Bukkit.getOnlinePlayers())if(!p.getName().equalsIgnoreCase(sender.getName())&&p.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))names.add(p.getName());return names;
    }
}
