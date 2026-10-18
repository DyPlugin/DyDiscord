package dev.dydiscord;

import dev.dydiscord.giveaway.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.security.SecureRandom;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayRepairTest {
 @TempDir Path dir;
 @Test void refreshRepairsExistingActiveMessageOncePerSessionWithoutRepublishing()throws Exception {
  var host=new GiveawayServiceTest.Host();var store=new GiveawayStore(dir.resolve("repair.db"));
  var g=store.create(GiveawayServiceTest.G,GiveawayServiceTest.C,600,1,"Prize",false,Set.of(),System.currentTimeMillis());store.activate(g.id(),GiveawayServiceTest.M);
  try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   service.refresh();service.list().get();assertEquals(1,host.edits);assertEquals(GiveawayServiceTest.M,store.find(g.id()).orElseThrow().message());
   service.refresh();service.list().get();assertEquals(1,host.edits);
   host.context=new GiveawayHost.Context(true,2,GiveawayServiceTest.G,host.context.settings());service.refresh();service.list().get();assertEquals(2,host.edits);
  }
 }
 @Test void closureDuringActiveRepairInvalidatesRestCheckAndNextPollShowsFinalState()throws Exception {
  var store=new GiveawayStore(dir.resolve("race.db"));List<String> presentations=new ArrayList<>();
  var host=new GiveawayServiceTest.Host(){
   public void update(GiveawayStore.Giveaway g,List<GiveawayStore.Winner> winners,java.util.function.BooleanSupplier current)throws Exception {
    if(g.state().equals("ACTIVE")){assertTrue(current.getAsBoolean());store.cancel(g.id());assertFalse(current.getAsBoolean());return;}
    assertTrue(current.getAsBoolean());presentations.add(g.state());
   }
  };
  var g=store.create(GiveawayServiceTest.G,GiveawayServiceTest.C,600,1,"Prize",false,Set.of(),System.currentTimeMillis());store.activate(g.id(),GiveawayServiceTest.M);
  try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){service.refresh();service.list().get();assertEquals("CANCELLED",store.find(g.id()).orElseThrow().state());service.poll().get();assertEquals(List.of("CANCELLED"),presentations);}
 }
 @Test void changedMessageTemplatesAreRepairedButCannotPublishAStaleActiveCard()throws Exception {
  var host=new GiveawayServiceTest.Host(){String version="first";boolean replace;
   public Object presentationVersion(){return version;}
   public void update(GiveawayStore.Giveaway g,List<GiveawayStore.Winner> winners,java.util.function.BooleanSupplier current){
    if(replace){replace=false;version="replacement";assertFalse(current.getAsBoolean());return;}
    assertTrue(current.getAsBoolean());edits++;
   }
  };
  var store=new GiveawayStore(dir.resolve("templates.db"));var g=store.create(GiveawayServiceTest.G,GiveawayServiceTest.C,600,1,"Prize",false,Set.of(),System.currentTimeMillis());store.activate(g.id(),GiveawayServiceTest.M);
  try(var service=new GiveawayService(store,host,Clock.systemUTC(),new SecureRandom())){
   service.refresh();service.list().get();assertEquals(1,host.edits);
   host.version="changed";host.replace=true;service.refresh();service.list().get();assertEquals(1,host.edits);
   service.refresh();service.list().get();assertEquals(2,host.edits);
   service.refresh();service.list().get();assertEquals(2,host.edits);
  }
 }
}
