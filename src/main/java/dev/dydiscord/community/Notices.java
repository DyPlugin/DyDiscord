package dev.dydiscord.community;
import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.*;
import dev.dydiscord.language.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.command.CommandSender;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
/** Small main-thread scheduler; Discord sends are bounded asynchronous tasks. */
public final class Notices implements AutoCloseable {
 private final DyDiscord plugin;
 private final Map<ToolsSettings.Schedule,Instant> due=new LinkedHashMap<>();
 private Instant lastScheduled=Instant.MIN;
 private volatile Instant suspended=Instant.EPOCH;
 private org.bukkit.scheduler.BukkitTask timer;
 private volatile boolean closed,startEligible,startSent;
 private volatile Instant startDeadline=Instant.EPOCH;
 public Notices(DyDiscord plugin){this.plugin=plugin;}
 public void start(){timer=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,20,20);}
 public void serverLoaded(){if(!startSent){startEligible=true;startDeadline=plugin.clock().instant().plusSeconds(120);}}
 public void suspend(int minutes){if(minutes<1||minutes>10080)throw new IllegalArgumentException();suspended=plugin.clock().instant().plusSeconds(minutes*60L);}
 public void resume(){suspended=Instant.EPOCH;}
 public void refresh(){synchronized(due){due.clear();}}
 private void tick(){
  if(closed||!plugin.ready())return;Instant now=plugin.clock().instant();ToolsSettings s=plugin.settings().tools();
  if(startEligible&&!startSent&&now.isBefore(startDeadline)&&s.startup()&&plugin.bot().usable()){
   startSent=true;String text=plugin.messages().render(true,plugin.bot().activeLanguage(),"server-started",Map.of("server_name",plugin.settings().serverName()));sendDiscord(text,0x55AA55,plugin.settings());
  }
  synchronized(due){
   if(!s.scheduled()){due.clear();return;}due.keySet().retainAll(s.schedules());
   ToolsSettings.Schedule selected=null;Instant earliest=Instant.MAX;
   for(var schedule:s.schedules()){
    Instant next=due.computeIfAbsent(schedule,k->now.plusSeconds(k.seconds()));if(now.isBefore(next))continue;
    if(now.isBefore(suspended)||now.isAfter(next.plusSeconds(30))){due.put(schedule,now.plusSeconds(schedule.seconds()));continue;}
    if(next.isBefore(earliest)){selected=schedule;earliest=next;}
   }
   if(selected!=null&&!now.isBefore(lastScheduled.plusSeconds(1))){lastScheduled=now;due.put(selected,now.plusSeconds(selected.seconds()));publish(template(selected.template()),selected.target(),true);}
  }
 }
 public String template(String name){String text=plugin.settings().tools().templates().get(name);if(text==null)throw new IllegalArgumentException();if(name.equals("maintenance")&&text.equals("Maintenance will begin shortly.")||name.equals("restart")&&text.equals("The server will restart shortly.")){String language=plugin.bot().activeLanguage();if(language==null||language.isEmpty())language=plugin.settings().botLanguage();return plugin.messages().get(true,language,"notice-"+name);}return text;}
 public String preview(String name,boolean discord){String text=template(name);return discord?Messages.safeDiscord(text):Messages.safeMinecraft(text);}
 public void publishTemplate(String name,String target){publish(template(name),target);}
 private void publish(String text,String target){publish(text,target,false);}
 private void publish(String text,String target,boolean scheduled){
  if(closed||!plugin.ready()||!Set.of("both","minecraft","discord").contains(target))return;Settings captured=plugin.settings();
  if(!target.equals("discord"))plugin.onMain(()->{if(!closed&&captured.equals(plugin.settings())&&(!scheduled||!plugin.clock().instant().isBefore(suspended)))for(var player:plugin.getServer().getOnlinePlayers())plugin.message(player,"community-notice",Map.of("text",text));});
  if(!target.equals("minecraft"))sendDiscord(Messages.safeDiscord(text),0x5865F2,captured,scheduled);
 }
 private void sendDiscord(String text,int color,Settings settings){sendDiscord(text,color,settings,false);}
 private boolean connected(Settings settings){return plugin.bot().usable()&&plugin.bot().applied()!=null&&settings.guild().equals(plugin.bot().applied().guild());}
 private void sendDiscord(String text,int color,Settings settings,boolean scheduled){
  if(settings.tools().channel().isEmpty()||!connected(settings))return;long session=plugin.bot().generation();Instant expires=plugin.clock().instant().plusSeconds(5);
  plugin.submit(()->{if(closed||!settings.equals(plugin.settings())||!plugin.clock().instant().isBefore(expires)||!plugin.bot().alive(session)||!connected(settings)||scheduled&&plugin.clock().instant().isBefore(suspended))return null;
   plugin.bot().validateChannel(settings.tools().channel(),false);
   plugin.bot().channel(settings.tools().channel()).sendMessageEmbeds(new EmbedBuilder().setColor(color).setDescription(text).build()).setAllowedMentions(List.of()).mentionRepliedUser(false).setCheck(()->!closed&&settings.equals(plugin.settings())&&connected(settings)&&(!scheduled||!plugin.clock().instant().isBefore(suspended))&&plugin.bot().alive(session)&&plugin.clock().instant().isBefore(expires)).timeout(5,TimeUnit.SECONDS).queue(v->{},plugin::error);return null;
  }).exceptionally(e->{plugin.error(e);return null;});
 }
 @Override public void close(){closed=true;if(timer!=null)timer.cancel();synchronized(due){due.clear();}}
}
