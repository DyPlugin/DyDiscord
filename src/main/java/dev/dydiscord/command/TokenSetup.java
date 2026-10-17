package dev.dydiscord.command;

import dev.dydiscord.DyDiscord;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived secret entry. Values never enter command routing, messages or audit fields. */
public final class TokenSetup implements Listener,AutoCloseable {
    public static final String PERMISSION="dydiscord.admin.bot.token";
    private static final String HIDDEN="/dydiscord-token-hidden";
    private record Entry(Player player,Instant expires,UUID nonce,boolean submitted) {
        Entry submittedEntry(){return new Entry(player,expires,nonce,true);}
    }
    private final DyDiscord plugin;
    private final Map<UUID,Entry> pending=new ConcurrentHashMap<>();
    private volatile boolean closed;
    public TokenSetup(DyDiscord plugin){this.plugin=plugin;}
    public void open(CommandSender sender){
        if(!(sender instanceof Player player)){say(sender,"player-only");return;}
        if(!player.hasPermission(PERMISSION)){say(sender,"no-permission");return;}
        if(closed||!plugin.ready()||!player.isOnline()){say(sender,"token-entry-failed");return;}
        if(environment()){say(sender,"token-entry-environment");return;}
        synchronized(pending){
            pending.entrySet().removeIf(e->!plugin.clock().instant().isBefore(e.getValue().expires()));
            if(pending.size()>=128&&!pending.containsKey(player.getUniqueId())){say(player,"token-entry-busy");return;}
            pending.put(player.getUniqueId(),new Entry(player,plugin.clock().instant().plusSeconds(60),UUID.randomUUID(),false));
        }
        say(player,"token-entry-prompt");
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
    public void preprocess(PlayerCommandPreprocessEvent event){
        String raw=event.getMessage();Player player=event.getPlayer();
        // Block unsafe inline values even when the sender lacks permission or the service is closed.
        if(directValue(raw)){hide(event);say(player,player.hasPermission(PERMISSION)?"token-entry-invalid":"no-permission");return;}
        UUID actor=player.getUniqueId();Entry entry=pending.get(actor);if(entry==null)return;
        hide(event);
        if(cancelCommand(raw)){pending.remove(actor,entry);say(player,"cancelled");return;}
        if(closed||entry.player()!=player||!plugin.clock().instant().isBefore(entry.expires())){pending.remove(actor,entry);say(player,"token-entry-expired");return;}
        if(!player.hasPermission(PERMISSION)){pending.remove(actor,entry);say(player,"no-permission");return;}
        if(entry.submitted()){say(player,"token-entry-busy");return;}
        String token=raw.startsWith("/")?raw.substring(1):raw;
        if(!validToken(token)){pending.remove(actor,entry);say(player,"token-entry-invalid");return;}
        if(environment()){pending.remove(actor,entry);say(player,"token-entry-environment");return;}
        Entry submitted=entry.submittedEntry();if(!pending.replace(actor,entry,submitted))return;
        plugin.submit(()->{
            boolean persisted=false;
            try{
                if(!plugin.mainValue(()->current(actor,submitted))){say(player,"token-entry-expired");return null;}
                if(environment()){say(player,"token-entry-environment");return null;}
                plugin.audit().write("mc:"+actor,"token-change","bot.yml","requested");
                plugin.configuration().change("bot.yml","token",token,candidate->{
                    if(candidate.tokenSource().equals("environment"))throw new IllegalStateException("Environment token is active");
                    if(!plugin.mainValue(()->current(actor,submitted)))throw new IllegalStateException("Token entry expired");
                });
                persisted=true;
                plugin.bot().restart();
                plugin.audit().write("mc:"+actor,"token-change","bot.yml","saved");
                say(player,"token-entry-saved");
            }catch(Exception failure){plugin.error(failure);say(player,persisted?"token-entry-saved":"token-entry-failed");}
            finally{pending.remove(actor,submitted);}
            return null;
        }).exceptionally(failure->{pending.remove(actor,submitted);plugin.error(failure);say(player,"token-entry-failed");return null;});
    }
    private boolean current(UUID actor,Entry entry){return !closed&&plugin.ready()&&pending.get(actor)==entry&&plugin.clock().instant().isBefore(entry.expires())&&entry.player().isOnline()&&plugin.getServer().getPlayer(actor)==entry.player()&&entry.player().hasPermission(PERMISSION);}
    private boolean environment(){return plugin.settings().tokenSource().equals("environment");}
    public static boolean validToken(String token){return token!=null&&token.length()>=32&&token.length()<=512&&token.matches("[A-Za-z0-9_.-]+");}
    static boolean directValue(String raw){
        String[] words=raw.strip().split("\\s+");if(words.length<4||!words[0].startsWith("/"))return false;
        String root=words[0].substring(1).toLowerCase(Locale.ROOT);int colon=root.lastIndexOf(':');if(colon>=0)root=root.substring(colon+1);
        if(!Set.of("dyda","dydiscord","discord").contains(root))return false;
        int offset=words.length>1&&Words.matches("admin",words[1])?2:1;
        return words.length>offset+2&&Words.matches("bot",words[offset])&&Words.matches("token",words[offset+1]);
    }
    private static boolean cancelCommand(String raw){
        String[] words=raw.strip().split("\\s+");if(words.length!=2||!words[0].startsWith("/"))return false;
        String root=words[0].substring(1).toLowerCase(Locale.ROOT);int colon=root.lastIndexOf(':');if(colon>=0)root=root.substring(colon+1);
        return Set.of("dyda","dydiscord","discord").contains(root)&&Words.matches("cancel",words[1]);
    }
    private static void hide(PlayerCommandPreprocessEvent event){event.setCancelled(true);event.setMessage(HIDDEN);}
    private void say(CommandSender player,String key){plugin.message(player,key,Map.of());}
    public void leave(UUID actor){pending.remove(actor);}
    public void clear(){pending.clear();}
    @Override public void close(){closed=true;clear();}
}
