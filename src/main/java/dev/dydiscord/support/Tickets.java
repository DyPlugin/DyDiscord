package dev.dydiscord.support;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.chat.ChatText;
import dev.dydiscord.command.Words;
import dev.dydiscord.storage.FeatureStore;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ticket: a player's message opens a Discord thread in the tickets channel, and further messages go to the same
 * thread until it is closed. Staff replies in the thread reach the player in game, or when they next join.
 */
public final class Tickets implements TabExecutor,Listener {
    public static final int MAX_MESSAGE=500;
    private final DyDiscord plugin;private final FeatureStore store;private final Map<UUID,Instant> last=new ConcurrentHashMap<>();
    public Tickets(DyDiscord plugin,FeatureStore store){this.plugin=plugin;this.store=store;}
    static boolean closeWord(String word){return word.equalsIgnoreCase("close")||Words.plain(word).equals("kapat");}

    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!plugin.ready()){plugin.message(sender,"busy",Map.of());return true;}
        if(!sender.hasPermission("dydiscord.ticket")){plugin.message(sender,"no-permission",Map.of());return true;}
        if(!(sender instanceof Player player)){plugin.message(sender,"player-only",Map.of());return true;}
        if(!plugin.settings().features().tickets().enabled()){plugin.message(sender,"ticket-disabled",Map.of());return true;}
        if(args.length==0){plugin.message(sender,"ticket-usage",Map.of());return true;}
        UUID uuid=player.getUniqueId();String name=player.getName();
        if(args.length==1&&closeWord(args[0])){close(sender,uuid,name);return true;}
        String text=ChatText.plain(String.join(" ",args),MAX_MESSAGE);if(text.isEmpty()){plugin.message(sender,"ticket-usage",Map.of());return true;}
        boolean staff=sender.hasPermission("dydiscord.ticket.notify");
        plugin.submit(()->{submit(sender,uuid,name,text,staff);return null;}).exceptionally(e->{plugin.error(e);plugin.message(sender,"ticket-failed",Map.of());return null;});
        return true;
    }
    /** I/O worker: adds to the open ticket, or opens a new one after the cooldown. */
    void submit(CommandSender sender,UUID uuid,String name,String text,boolean staff)throws Exception{
        var bot=plugin.bot();if(bot==null||!bot.usable()){plugin.message(sender,"ticket-failed",Map.of());return;}
        Optional<FeatureStore.Ticket> open=store.ticket(uuid);
        if(open.isPresent()&&bot.tickets().post(open.get().thread(),bot.text("ticket-message",Map.of("player",name,"message",text)))){plugin.message(sender,"ticket-added",Map.of());return;}
        Instant now=plugin.clock().instant();int cooldown=plugin.settings().features().tickets().cooldown();Instant previous=last.get(uuid);
        if(cooldown>0&&previous!=null&&now.isBefore(previous.plusSeconds(cooldown))&&!staff){plugin.message(sender,"ticket-cooldown",Map.of("seconds",Long.toString(Math.max(1,previous.plusSeconds(cooldown).getEpochSecond()-now.getEpochSecond()))));return;}
        String thread=bot.tickets().open(name,bot.text("ticket-opened-discord",Map.of("player",name,"message",text)));
        store.open(new FeatureStore.Ticket(uuid,name,thread,now));last.put(uuid,now);last.values().removeIf(time->now.isAfter(time.plusSeconds(Math.max(cooldown,1))));
        plugin.message(sender,"ticket-opened",Map.of());
        plugin.onMain(()->{for(Player online:plugin.getServer().getOnlinePlayers())if(online.hasPermission("dydiscord.ticket.notify")&&!online.getUniqueId().equals(uuid))plugin.message(online,"ticket-staff",Map.of("player",name,"message",text));});
    }
    private void close(CommandSender sender,UUID uuid,String name){
        plugin.submit(()->{
            Optional<FeatureStore.Ticket> open=store.ticket(uuid);if(open.isEmpty()){plugin.message(sender,"ticket-none",Map.of());return null;}
            var bot=plugin.bot();if(bot!=null&&bot.usable())bot.tickets().close(open.get().thread(),bot.text("ticket-closed-discord",Map.of("player",name)));
            store.close(uuid);plugin.message(sender,"ticket-closed",Map.of());return null;
        }).exceptionally(e->{plugin.error(e);plugin.message(sender,"ticket-failed",Map.of());return null;});
    }
    /** A staff message in a ticket thread; called on the Discord event thread. Unknown threads are ignored. */
    public void reply(String threadId,String author,String content){
        String staff=ChatText.plain(author,64),text=ChatText.plain(content,MAX_MESSAGE);if(text.isEmpty())return;
        plugin.submit(()->{deliver(threadId,staff,text);return null;}).exceptionally(e->{plugin.error(e);return null;});
    }
    void deliver(String threadId,String staff,String text)throws Exception{
        Optional<FeatureStore.Ticket> ticket=store.ticketByThread(threadId);if(ticket.isEmpty())return;UUID uuid=ticket.get().uuid();
        Player player=plugin.mainValue(()->{Player online=plugin.getServer().getPlayer(uuid);return online!=null&&online.isOnline()?online:null;});
        if(player!=null)plugin.message(player,"ticket-reply",Map.of("staff",staff,"message",text));else store.queueReply(uuid,staff,text);
    }
    /** Replies that arrived while the player was offline. */
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event){
        if(!plugin.ready()||!plugin.settings().features().tickets().enabled())return;Player player=event.getPlayer();UUID uuid=player.getUniqueId();
        plugin.submit(()->{for(FeatureStore.Reply reply:store.takeReplies(uuid))plugin.message(player,"ticket-reply",Map.of("staff",reply.author(),"message",reply.message()));return null;}).exceptionally(e->{plugin.error(e);return null;});
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(args.length!=1||!sender.hasPermission("dydiscord.ticket")||!plugin.ready())return List.of();
        String word=plugin.language(sender).equals("tr")?"kapat":"close";return Words.plain(word).startsWith(Words.plain(args[0]))?List.of(word):List.of();
    }
}
