package dev.dydiscord.community;
import dev.dydiscord.DyDiscord;
import dev.dydiscord.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
/** Administration stays in Minecraft with leaf permissions; no public account details. */
public final class CommunityCommands {
 private static final Set<String> KEYS=Set.of("chat-inbound","chat-to-minecraft","chat-to-discord","chat-both");
 private final DyDiscord p;
 public CommunityCommands(DyDiscord p){this.p=p;}
 public boolean handles(String key){return KEYS.contains(key)||key.startsWith("giveaway-");}
 public void execute(CommandSender sender,Definition d,String[] args){
  String actor=p.actor(sender),language=p.language(sender);UUID playerId=sender instanceof Player player?player.getUniqueId():null;
  p.submit(()->{
   if(!p.mainValue(()->sender.hasPermission(d.permission())&&(!(sender instanceof Player player)||player.isOnline())))throw new dev.dydiscord.linking.Linking.Rejected("no-permission");
   switch(d.key()){
    case "giveaway-start"->{if(p.giveaways()==null)throw new IllegalStateException();String prize=String.join(" ",Arrays.copyOfRange(args,2,args.length));String id=p.giveaways().start(Long.parseLong(args[0]),Integer.parseInt(args[1]),prize).get(15,java.util.concurrent.TimeUnit.SECONDS);p.message(sender,"giveaway-created",Map.of("id",id));}
    case "giveaway-end","giveaway-cancel"->{if(p.giveaways()==null)throw new IllegalStateException();if(!args[0].matches("[0-9a-f]{32}"))throw new IllegalArgumentException();boolean accepted=(d.key().equals("giveaway-end")?p.giveaways().end(args[0]):p.giveaways().cancel(args[0])).get(15,java.util.concurrent.TimeUnit.SECONDS);p.message(sender,accepted?(d.key().equals("giveaway-end")?"giveaway-ending":"giveaway-cancelled-admin"):"giveaway-not-found",Map.of());}
    case "giveaway-list"->{if(p.giveaways()==null)throw new IllegalStateException();var giveaways=p.giveaways().list().get(5,java.util.concurrent.TimeUnit.SECONDS);if(giveaways.isEmpty())p.message(sender,"giveaway-none",Map.of());for(var giveaway:giveaways)p.message(sender,"giveaway-list-entry",Map.of("id",giveaway.id(),"prize",giveaway.prize(),"state",p.messages().get(false,language,"giveaway-state-"+giveaway.state().toLowerCase(java.util.Locale.ROOT))));}
    case "chat-inbound"->{
      boolean enabled=enabled(args[0]);var previous=p.chat().preference(playerId).get(5,java.util.concurrent.TimeUnit.SECONDS);
      if(previous.inbound()==enabled){p.message(sender,enabled?"chat-personal-already-on":"chat-personal-already-off",Map.of());break;}
      if(!p.mainValue(()->sender.hasPermission(d.permission())&&sender instanceof Player player&&player.isOnline()))throw new dev.dydiscord.linking.Linking.Rejected("no-permission");
      p.chat().preference(playerId,"inbound",enabled).get(5,java.util.concurrent.TimeUnit.SECONDS);
      p.message(sender,enabled?"chat-personal-on":"chat-personal-off",Map.of());
    }
    case "chat-to-minecraft","chat-to-discord","chat-both"->{
      boolean enabled=enabled(args[0]);boolean incoming=!d.key().equals("chat-to-discord"),outgoing=!d.key().equals("chat-to-minecraft");
      var before=p.settings();String direction=incoming&&outgoing?"Discord ↔ Minecraft":incoming?"Discord → Minecraft":"Minecraft → Discord";
      if((!incoming||before.chat().toMinecraft()==enabled)&&(!outgoing||before.chat().toDiscord()==enabled)){p.message(sender,enabled?"chat-already-on":"chat-already-off",Map.of("direction",direction));break;}
      Map<String,Object> changes=new LinkedHashMap<>();if(incoming)changes.put("chat.discord-to-minecraft",enabled);if(outgoing)changes.put("chat.minecraft-to-discord",enabled);
      p.configuration().change("bot.yml",changes,next->p.bot().validateChanges(before,next));p.chat().invalidate();p.chat().pause("both",false);
      if(before.needsRestart(p.settings()))p.message(sender,"restart-required",Map.of());
      p.message(sender,enabled?"chat-enabled":"chat-disabled",Map.of("direction",direction));
    }
    default->throw new IllegalArgumentException();
   }return null;
  }).exceptionally(error->{Throwable cause=error;while(cause.getCause()!=null&&cause instanceof java.util.concurrent.CompletionException)cause=cause.getCause();if(cause instanceof dev.dydiscord.linking.Linking.Rejected r)p.message(sender,r.key,Map.of());else if(cause instanceof IllegalArgumentException)p.message(sender,"usage",Map.of());else p.message(sender,"failed",Map.of("event",p.error(error)));return null;});
 }
 private static boolean enabled(String value){if(Words.matches("on",value)||Words.matches("enable",value))return true;if(Words.matches("off",value)||Words.matches("disable",value))return false;throw new IllegalArgumentException();}
}
