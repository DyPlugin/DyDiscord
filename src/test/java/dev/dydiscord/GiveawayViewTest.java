package dev.dydiscord;

import dev.dydiscord.giveaway.*;
import dev.dydiscord.discord.PanelView;
import net.dv8tion.jda.api.components.buttons.*;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayViewTest {
 @TempDir Path dir;
 @Test void activeCardHasV2SpacingSafePrizeAndInlineJoinLeave()throws Exception {
  var messages=TestSupport.config(dir).active().messages();
  var giveaway=new GiveawayStore.Giveaway("a".repeat(32),"123","456","789","@everyone **Prize**",2,61000,"ACTIVE",false,Set.of(),false);
  try(var data=PanelView.create(GiveawayView.card((key,values)->messages.render(true,"en",key,values),giveaway,List.of()))){
   assertTrue(data.isUsingComponentsV2());assertTrue(data.getEmbeds().isEmpty());assertTrue(data.getAllowedMentions().isEmpty());
   var texts=data.getComponentTree().findAll(TextDisplay.class);assertEquals(1,texts.size());String body=texts.get(0).getContent();
   assertTrue(body.startsWith("## Giveaway\n"));assertTrue(body.contains("\n\n"));assertTrue(body.contains("<t:61:R>"));assertFalse(body.contains("@everyone"));assertFalse(body.contains("**Prize**"));assertFalse(body.contains("DyDiscord"));
   var buttons=data.getComponentTree().findAll(Button.class);assertEquals(2,buttons.size());assertEquals("Enter",buttons.get(0).getLabel());assertEquals("Leave",buttons.get(1).getLabel());assertEquals("dd:giveaway:leave:"+giveaway.id(),buttons.get(1).getCustomId());assertTrue(buttons.stream().noneMatch(Button::isDisabled));
  }
 }
 @Test void confirmationUsesRedLeaveGreenCancelAndPrivateResultHasNoButtons()throws Exception {
  var messages=TestSupport.config(dir).active().messages();GiveawayView.Text text=(key,values)->messages.render(true,"tr",key,values);
  try(var data=PanelView.create(GiveawayView.confirmation(text,"a".repeat(32)))){
   var buttons=data.getComponentTree().findAll(Button.class);assertEquals(2,buttons.size());assertEquals(ButtonStyle.DANGER,buttons.get(0).getStyle());assertEquals(ButtonStyle.SUCCESS,buttons.get(1).getStyle());assertEquals("Ayrıl",buttons.get(0).getLabel());assertEquals("Vazgeç",buttons.get(1).getLabel());
   assertEquals(new java.awt.Color(0xED4245),((net.dv8tion.jda.api.components.container.Container)data.getComponents().get(0)).getAccentColor());
  }
  try(var data=PanelView.update(GiveawayView.reply(text,"giveaway-left"))){assertTrue(data.isUsingComponentsV2());assertTrue(data.getComponentTree().findAll(Button.class).isEmpty());assertTrue(data.getAllowedMentions().isEmpty());}
 }
 @Test void finishedCardDisablesActionsAndEscapesWinnerNames()throws Exception {
  var messages=TestSupport.config(dir).active().messages();
  var giveaway=new GiveawayStore.Giveaway("a".repeat(32),"123","456","789","Prize",1,61000,"DRAWN",false,Set.of(),false);
  try(var data=PanelView.create(GiveawayView.card((key,values)->messages.render(true,"en",key,values),giveaway,List.of(new GiveawayStore.Winner("123","@everyone *Winner*"))))){
   assertTrue(data.getComponentTree().findAll(Button.class).stream().allMatch(Button::isDisabled));String body=data.getComponentTree().findAll(TextDisplay.class).get(0).getContent();assertFalse(body.contains("@everyone"));assertFalse(body.contains("*Winner*"));assertFalse(body.contains("123"));
  }
 }
}
