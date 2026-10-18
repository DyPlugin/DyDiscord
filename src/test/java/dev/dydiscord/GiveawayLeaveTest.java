package dev.dydiscord;

import dev.dydiscord.giveaway.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayLeaveTest {
 @TempDir Path dir;
 static final String G=GiveawayServiceTest.G,C=GiveawayServiceTest.C,M=GiveawayServiceTest.M,U=GiveawayServiceTest.U,V=GiveawayServiceTest.V;
 static class MutableClock extends Clock {
  long now=1000;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return Instant.ofEpochMilli(now);}
 }
 @Test void leaveRequiresConfirmationBoundToActorAndDoesNotRemoveOtherEntrants()throws Exception {
  var host=new GiveawayServiceTest.Host();var store=new GiveawayStore(dir.resolve("leave.db"));
  try(var service=new GiveawayService(store,host,new MutableClock(),new SecureRandom())){
   String id=service.start(60,1,"Prize").get();service.enter(id,G,C,M,U,1).get();service.enter(id,G,C,M,V,1).get();
   var request=service.requestLeave(id,G,C,M,U,1).get();assertEquals(GiveawayService.LeaveResult.CONFIRM,request.result());
   assertEquals(List.of(U,V),store.entrants(id));
   assertEquals(GiveawayService.LeaveResult.UNAVAILABLE,service.finishLeave(request.token(),G,C,V,1,true).get());assertEquals(List.of(U,V),store.entrants(id));
   assertEquals(GiveawayService.LeaveResult.LEFT,service.finishLeave(request.token(),G,C,U,1,true).get());assertEquals(List.of(V),store.entrants(id));
   assertEquals(GiveawayService.LeaveResult.EXPIRED,service.finishLeave(request.token(),G,C,U,1,true).get());
  }
 }
 @Test void cancelExpirySessionConfigAndClosedGiveawayNeverRemoveEntry()throws Exception {
  var host=new GiveawayServiceTest.Host();var store=new GiveawayStore(dir.resolve("stale.db"));var clock=new MutableClock();
  try(var service=new GiveawayService(store,host,clock,new SecureRandom())){
   String id=service.start(600,1,"Prize").get();service.enter(id,G,C,M,U,1).get();
   var request=service.requestLeave(id,G,C,M,U,1).get();assertEquals(GiveawayService.LeaveResult.CANCELLED,service.finishLeave(request.token(),G,C,U,1,false).get());assertEquals(List.of(U),store.entrants(id));
   request=service.requestLeave(id,G,C,M,U,1).get();clock.now+=60000;assertEquals(GiveawayService.LeaveResult.EXPIRED,service.finishLeave(request.token(),G,C,U,1,true).get());
   request=service.requestLeave(id,G,C,M,U,1).get();host.context=new GiveawayHost.Context(true,2,G,host.context.settings());assertEquals(GiveawayService.LeaveResult.UNAVAILABLE,service.finishLeave(request.token(),G,C,U,2,true).get());
   request=service.requestLeave(id,G,C,M,U,2).get();host.context=new GiveawayHost.Context(true,2,G,new dev.dydiscord.config.GiveawaySettings(true,C,true,Set.of()));assertEquals(GiveawayService.LeaveResult.UNAVAILABLE,service.finishLeave(request.token(),G,C,U,2,true).get());
   request=service.requestLeave(id,G,C,M,U,2).get();service.end(id).get();assertEquals(GiveawayService.LeaveResult.CLOSED,service.finishLeave(request.token(),G,C,U,2,true).get());assertEquals(List.of(U),store.entrants(id));
  }
 }
 @Test void freshMembershipAndContextAreCheckedAfterRestBeforeRemoval()throws Exception {
  var host=new GiveawayServiceTest.Host();var store=new GiveawayStore(dir.resolve("rest.db"));
  try(var service=new GiveawayService(store,host,new MutableClock(),new SecureRandom())){
   String id=service.start(60,1,"Prize").get();service.enter(id,G,C,M,U,1).get();var request=service.requestLeave(id,G,C,M,U,1).get();
   host.unknown=true;assertEquals(GiveawayService.LeaveResult.UNAVAILABLE,service.finishLeave(request.token(),G,C,U,1,true).get());assertEquals(List.of(U),store.entrants(id));
   host.unknown=false;request=service.requestLeave(id,G,C,M,U,1).get();host.changeSession=true;assertEquals(GiveawayService.LeaveResult.UNAVAILABLE,service.finishLeave(request.token(),G,C,U,1,true).get());assertEquals(List.of(U),store.entrants(id));
  }
 }
 @Test void forgedPublicMessageAndNonEntrantsCannotGetConfirmation()throws Exception {
  var host=new GiveawayServiceTest.Host();var store=new GiveawayStore(dir.resolve("forged.db"));
  try(var service=new GiveawayService(store,host,new MutableClock(),new SecureRandom())){
   String id=service.start(60,1,"Prize").get();service.enter(id,G,C,M,U,1).get();
   assertEquals(GiveawayService.LeaveResult.UNAVAILABLE,service.requestLeave(id,G,C,"62345678901234567",U,1).get().result());
   assertEquals(GiveawayService.LeaveResult.NOT_ENTERED,service.requestLeave(id,G,C,M,V,1).get().result());
  }
 }
 @Test void absentMemberAndExpiryDuringRestCannotRemoveEntry()throws Exception {
  var clock=new MutableClock();
  var host=new GiveawayServiceTest.Host(){int mode;public Member member(String guild,String actor){if(mode==2)clock.now+=60000;return new Member(mode!=1,false,"Alex",Set.of());}};
  var store=new GiveawayStore(dir.resolve("membership.db"));
  try(var service=new GiveawayService(store,host,clock,new SecureRandom())){
   String id=service.start(600,1,"Prize").get();service.enter(id,G,C,M,U,1).get();
   var request=service.requestLeave(id,G,C,M,U,1).get();host.mode=1;assertEquals(GiveawayService.LeaveResult.INELIGIBLE,service.finishLeave(request.token(),G,C,U,1,true).get());assertEquals(List.of(U),store.entrants(id));
   host.mode=0;request=service.requestLeave(id,G,C,M,U,1).get();host.mode=2;assertEquals(GiveawayService.LeaveResult.EXPIRED,service.finishLeave(request.token(),G,C,U,1,true).get());assertEquals(List.of(U),store.entrants(id));
  }
 }
 @Test void replacementPromptInvalidatesPreviousTokenAndSuccessfulLeavePersists()throws Exception {
  Path file=dir.resolve("persistent.db");var host=new GiveawayServiceTest.Host();String id;
  try(var service=new GiveawayService(new GiveawayStore(file),host,new MutableClock(),new SecureRandom())){
   id=service.start(600,1,"Prize").get();service.enter(id,G,C,M,U,1).get();
   var old=service.requestLeave(id,G,C,M,U,1).get();var latest=service.requestLeave(id,G,C,M,U,1).get();
   assertNotEquals(old.token(),latest.token());assertEquals(GiveawayService.LeaveResult.EXPIRED,service.finishLeave(old.token(),G,C,U,1,true).get());
   assertEquals(GiveawayService.LeaveResult.LEFT,service.finishLeave(latest.token(),G,C,U,1,true).get());
  }
  try(var store=new GiveawayStore(file)){assertTrue(store.entrants(id).isEmpty());assertEquals("ACTIVE",store.find(id).orElseThrow().state());}
 }
}
