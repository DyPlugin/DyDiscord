package dev.dydiscord.community;
import dev.dydiscord.DyDiscord;
import dev.dydiscord.command.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.*;
/** Administration stays in Minecraft with leaf permissions; no public account details. */
public final class CommunityCommands {
 private static final Set<String> KEYS=Set.of("chat-inbound","chat-outbound","chat-notify","chat-pause","chat-resume","announce-preview","announce-send","announce-set","announcements-pause","announcements-resume","maintenance","restart-notice","support-report","config-validate","translations-validate","setup","rewards-preview");
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
    case "chat-inbound","chat-outbound","chat-notify"->{String value=Words.canonical(args[0]);boolean enabled;if(Words.matches("on",value)||Words.matches("enable",value))enabled=true;else if(Words.matches("off",value)||Words.matches("disable",value))enabled=false;else throw new IllegalArgumentException();String key=d.key().substring(5);p.chat().preference(playerId,key,enabled).get(5,java.util.concurrent.TimeUnit.SECONDS);p.message(sender,"saved",Map.of());}
    case "chat-pause","chat-resume"->{String direction=Words.canonical(args[0]);if(!Set.of("inbound","outbound","both").contains(direction))throw new IllegalArgumentException();p.chat().pause(direction,d.key().equals("chat-pause"));p.message(sender,"saved",Map.of());}
    case "announce-preview"->{p.message(sender,"notice-preview-minecraft",Map.of("text",p.notices().preview(args[0],false)));p.message(sender,"notice-preview-discord",Map.of("text",p.notices().preview(args[0],true)));}
    case "announce-send","maintenance","restart-notice"->{String id=d.key().equals("maintenance")?"maintenance":d.key().equals("restart-notice")?"restart":args[0];p.notices().publishTemplate(id,"both");p.message(sender,"notice-queued",Map.of());}
    case "announce-set"->{String id=args[0];if(!id.matches("[a-z][a-z0-9-]{0,31}"))throw new IllegalArgumentException();String text=String.join(" ",Arrays.copyOfRange(args,1,args.length));p.audit().write(actor,"notice-template",id,"accepted");p.configuration().change("bot.yml","community-tools.templates."+id,text);p.notices().refresh();p.message(sender,"saved",Map.of());}
    case "announcements-pause"->{p.notices().suspend(Integer.parseInt(args[0]));p.message(sender,"saved",Map.of());}
    case "announcements-resume"->{p.notices().resume();p.message(sender,"saved",Map.of());}
    case "support-report"->{Support.save(p);p.message(sender,"report-saved",Map.of());}
    case "config-validate","translations-validate"->{var missing=Support.validate(p.configuration());p.message(sender,"validation-result",Map.of("count",Integer.toString(missing.size())));for(String key:missing.stream().sorted().limit(20).toList())p.message(sender,"status-line",Map.of("status",key));}
    case "setup"->{for(String line:p.bot().diagnostics(true,language))p.message(sender,"status-line",Map.of("status",line));p.message(sender,"setup-guide",Map.of());}
    case "rewards-preview"->{var link=p.store().byUuid(UUID.fromString(args[0])).orElseThrow(()->new IllegalArgumentException());if(p.linkRewards()==null)throw new IllegalStateException();var preview=p.linkRewards().preview(link);p.message(sender,"reward-preview",Map.of("count",Integer.toString(preview.commands().size()),"enabled",language.equals("tr")?(preview.enabled()?"açık":"kapalı"):(preview.enabled()?"enabled":"disabled"),"eligible",language.equals("tr")?(preview.eligible()?"uygun":"uygun değil"):(preview.eligible()?"eligible":"not eligible")));for(String command:preview.commands())p.message(sender,"status-line",Map.of("status",command));}
    default->throw new IllegalArgumentException();
   }return null;
  }).exceptionally(error->{Throwable cause=error;while(cause.getCause()!=null&&cause instanceof java.util.concurrent.CompletionException)cause=cause.getCause();if(cause instanceof dev.dydiscord.linking.Linking.Rejected r)p.message(sender,r.key,Map.of());else if(cause instanceof IllegalArgumentException)p.message(sender,"usage",Map.of());else p.message(sender,"failed",Map.of("event",p.error(error)));return null;});
 }
}
