package dev.dydiscord;

import dev.dydiscord.discord.Cards;
import dev.dydiscord.discord.PanelView;
import dev.dydiscord.discord.ServerInfo;
import dev.dydiscord.discord.StatusCard;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every Discord message except relayed chat is a Components V2 container. */
class ContainerMessagesTest {
    @TempDir Path folder;
    private static List<String> texts(Container card){List<String> out=new ArrayList<>();card.getComponents().forEach(c->{if(c instanceof TextDisplay t)out.add(t.getContent());});return out;}

    @Test void embedsBecomeContainersWithTheirTitleFieldsFooterAndColour(){
        var embed=new EmbedBuilder().setTitle("Survival").setDescription("Hello").setColor(0x55AA55).addField("Status","Online",true).addField("Players","3/50",true).addField("Names","Alex\nSteve",false).setFooter("footer").setTimestamp(Instant.ofEpochSecond(1700000000)).build();
        Container card=Cards.of(embed);
        assertEquals(0x55AA55,card.getAccentColorRaw());
        assertEquals(List.of("### Survival\nHello","**Status:** Online · **Players:** 3/50\n\n**Names**\nAlex\nSteve","-# footer\n-# <t:1700000000:R>"),texts(card));
        assertEquals(Cards.BLURPLE,Cards.of(new EmbedBuilder().setDescription("x").build()).getAccentColorRaw(),"an embed without colour gets the DyDiscord blue");
    }
    @Test void longTextIsCutBelowTheV2LimitWithoutBreakingAnEscape(){
        String text=Cards.of(new EmbedBuilder().setTitle("t").setDescription("a".repeat(3890)+"\\_"+"b".repeat(150)).addField("f","c".repeat(900),false).build()).getComponents().stream().filter(c->c instanceof TextDisplay).map(c->((TextDisplay)c).getContent()).findFirst().orElseThrow();
        assertTrue(text.length()<=3900,"length "+text.length());assertTrue(text.endsWith("…"));assertFalse(text.endsWith("\\…"));
        assertTrue(texts(Cards.text("")).get(0).length()>0,"an empty reply still has visible content");
    }
    @Test void messagesUseComponentsV2AndNoEmbedsOrMentions(){
        var create=PanelView.create(Cards.text("Too many attempts."));
        assertTrue(create.isUsingComponentsV2());assertTrue(create.getEmbeds().isEmpty());assertTrue(create.getContent().isEmpty());assertTrue(create.getMentionedUsers().isEmpty());
        var edit=PanelView.update(Cards.text("x"));assertTrue(edit.isUsingComponentsV2());
        assertEquals(Cards.CONSOLE,Cards.console("```text\n[INFO] Done\n```").getAccentColorRaw());
    }
    @Test void statusCardAndLogsAreContainers()throws Exception{
        var messages=TestSupport.config(folder).active().messages();
        Container card=Cards.of(StatusCard.card(messages,"en",new StatusCard.Snapshot(new ServerInfo.Snapshot("Survival","1.21.4",3,50),20),true,Instant.EPOCH));
        assertEquals(0x55AA55,card.getAccentColorRaw());
        assertEquals("### Survival",texts(card).get(0));assertTrue(texts(card).get(1).contains("**Status:** 🟢 Online · **Players:** 3/50"),texts(card).get(1));assertTrue(texts(card).get(2).startsWith("-# <t:0:R>"));
        Container log=Cards.text(messages.render(true,"en","log-rtp",Map.of("player","Alex","world","world","x","1","y","64","z","2")),0x55AAFF);
        assertEquals(List.of("**Alex** teleported randomly to world (1, 64, 2)."),texts(log));assertEquals(0x55AAFF,log.getAccentColorRaw());
    }
}
