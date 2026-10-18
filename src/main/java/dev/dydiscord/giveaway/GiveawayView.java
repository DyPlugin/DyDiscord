package dev.dydiscord.giveaway;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.separator.Separator;
import java.util.*;

/** Shared, mention-free V2 presentation for public giveaways and private interactions. */
public final class GiveawayView {
    @FunctionalInterface public interface Text {String render(String key,Map<String,String> values);}
    private GiveawayView(){}
    public static Container card(Text text,GiveawayStore.Giveaway g,List<GiveawayStore.Winner> winners){
        String key=g.state().equals("DRAWN")?"giveaway-drawn":g.state().equals("CANCELLED")?"giveaway-cancelled":"giveaway-open";
        String names=winners.isEmpty()?text.render("giveaway-no-winners",Map.of()):String.join(", ",winners.stream().map(GiveawayStore.Winner::name).toList());
        String marker="DDTIME"+UUID.randomUUID().toString().replace("-","");
        String body=text.render(key,Map.of("prize",g.prize(),"winners",g.state().equals("DRAWN")?names:Integer.toString(g.winnerCount()),"ends",marker)).replace(marker,"<t:"+Math.max(0,Math.floorDiv(g.endsAt(),1000))+":R>");
        boolean disabled=!g.state().equals("ACTIVE")&&!g.state().equals("DRAFT");
        return Container.of(TextDisplay.of("## "+text.render("giveaway-title",Map.of())+"\n"+body),Separator.createDivider(Separator.Spacing.SMALL),ActionRow.of(
            Button.primary("dd:giveaway:"+g.id(),text.render("giveaway-enter",Map.of())).withDisabled(disabled),
            Button.danger("dd:giveaway:leave:"+g.id(),text.render("giveaway-leave",Map.of())).withDisabled(disabled)))
            .withAccentColor(g.state().equals("CANCELLED")?0xAAAAAA:0x5865F2);
    }
    public static Container confirmation(Text text,String token){return Container.of(TextDisplay.of(text.render("giveaway-leave-confirm",Map.of())),ActionRow.of(
        Button.danger("dd:giveaway:confirm:"+token,text.render("giveaway-leave",Map.of())),
        Button.success("dd:giveaway:cancel:"+token,text.render("giveaway-leave-cancel",Map.of())))).withAccentColor(0xED4245);}
    public static Container reply(Text text,String key){return Container.of(TextDisplay.of(text.render(key,Map.of()))).withAccentColor(0x5865F2);}
}
