package dev.dydiscord;
import dev.dydiscord.update.Modrinth;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class ReleaseUpdateTest {
 private String release(String id,String version,String type){return "{\"id\":\""+id+"\",\"version_number\":\""+version+"\",\"version_type\":\""+type+"\",\"status\":\"listed\",\"game_versions\":[\"26.3\"],\"loaders\":[\"paper\"]}";}
 @Test void optedInStableSkipsSupersededBetasButAcceptsFutureBeta()throws Exception{
  String old=release("old","v1.0.2-BETA","beta");
  assertTrue(Modrinth.newer("["+old+"]","1.0.0","26.3",Set.of("paper"),true).isEmpty());
  assertEquals("rc",Modrinth.newer("["+release("rc","1.0.1-RC.1","beta")+"]","1.0.0","26.3",Set.of("paper"),true).orElseThrow().id());
  assertEquals("future",Modrinth.newer("["+old+","+release("future","1.1.0-BETA","beta")+"]","1.0.0","26.3",Set.of("paper"),true).orElseThrow().id());
 }
}
