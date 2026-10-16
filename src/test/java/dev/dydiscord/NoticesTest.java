package dev.dydiscord;
import dev.dydiscord.community.Notices;
import dev.dydiscord.config.Settings;
import dev.dydiscord.discord.Bot;
import org.junit.jupiter.api.Test;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class NoticesTest {
 @Test void simultaneousSchedulesArePacedNotBurst()throws Exception{
  var templates=new LinkedHashMap<String,String>();var schedules=new ArrayList<Map<String,Object>>();for(int i=0;i<20;i++){templates.put("notice"+i,"text"+i);schedules.add(Map.of("template","notice"+i,"every-seconds",60,"target","minecraft"));}
  Settings settings=Settings.read(Map.of(),Map.of("community-tools",Map.of("templates",templates,"announcements",Map.of("enabled",true,"schedules",schedules))),null);
  DyDiscord p=mock(DyDiscord.class);TestSupport.Time time=new TestSupport.Time();when(p.settings()).thenReturn(settings);when(p.clock()).thenReturn(time);when(p.ready()).thenReturn(true);Server server=mock(Server.class);when(p.getServer()).thenReturn(server);Player player=mock(Player.class);doReturn(List.of(player)).when(server).getOnlinePlayers();doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(p).onMain(any());
  var notices=new Notices(p);var tick=Notices.class.getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(notices);time.advance(60);tick.invoke(notices);verify(p,times(1)).message(eq(player),eq("community-notice"),any());tick.invoke(notices);verify(p,times(1)).message(eq(player),eq("community-notice"),any());time.advance(1);tick.invoke(notices);verify(p,times(2)).message(eq(player),eq("community-notice"),any());notices.close();
 }
 @Test void stagedGuildChangeNeverSendsThroughOldBot(){
  String channel="1557418524983566447";Settings current=Settings.read(Map.of(),Map.of("guild-id","1557418524983566448","community-tools",Map.of("channel-id",channel,"templates",Map.of("maintenance","Custom notice"))),null);Settings applied=Settings.read(Map.of(),Map.of("guild-id","1557418524983566449"),null);DyDiscord p=mock(DyDiscord.class);Bot bot=mock(Bot.class);when(p.bot()).thenReturn(bot);when(bot.usable()).thenReturn(true);when(bot.applied()).thenReturn(applied);when(p.settings()).thenReturn(current);when(p.clock()).thenReturn(new TestSupport.Time());when(p.ready()).thenReturn(true);var notices=new Notices(p);notices.publishTemplate("maintenance","discord");verify(p,never()).submit(any());notices.close();
 }
}
