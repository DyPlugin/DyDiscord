package dev.dydiscord.discord;

import dev.dydiscord.language.Messages;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.utils.messages.*;
import java.util.*;

/** A persistent V2 card. Its numeric marker and button IDs survive language changes. */
public final class PanelView {
    public static final int MARKER=0x445944;
    private static final Set<String> BUTTONS=Set.of("dd:panel:link","dd:panel:account","dd:panel:unlink");
    private static final Set<String> LABELED_BUTTONS=Set.of("dd:panel:brand","dd:panel:link","dd:panel:account","dd:panel:unlink");
    private PanelView() {}
    public static Container card(Messages messages,String language,String serverName,String avatar){
        messages.validatePanel(serverName);
        String title=messages.render(true,language,"panel-title",Map.of("server_name",serverName));
        String body=messages.get(true,language,"panel-description"),privacy=messages.get(true,language,"panel-privacy");
        TextDisplay text=TextDisplay.of("## "+title+"\n"+body+"\n\n"+privacy);
        var header=avatar==null||avatar.isBlank()?text:Section.of(Thumbnail.fromUrl(avatar).withDescription("Discord"),text);
        return Container.of(header,Separator.createDivider(Separator.Spacing.SMALL),ActionRow.of(Button.primary("dd:panel:link",messages.get(true,language,"link")),Button.danger("dd:panel:unlink",messages.get(true,language,"unlink")),Button.secondary("dd:panel:account",messages.get(true,language,"account-button"))))
            .withAccentColor(0xFF55FF).withUniqueId(MARKER);
    }
    public static MessageCreateData create(Container card){return new MessageCreateBuilder().setComponents(card).useComponentsV2().setAllowedMentions(List.of()).build();}
    public static MessageEditData update(Container card){
        // Discord requires legacy content and embeds to be cleared during the irreversible V2 transition.
        return new MessageEditBuilder().setContent(null).setEmbeds(List.of()).setComponents(card).useComponentsV2().setAllowedMentions(List.of()).build();
    }
    public static boolean recognizable(Message message,String author){
        if(!message.getAuthor().getId().equals(author))return false;
        boolean marker=message.getComponents().stream().anyMatch(c->c.getType()==net.dv8tion.jda.api.components.Component.Type.CONTAINER&&c.getUniqueId()==MARKER);
        Set<String> ids=new HashSet<>();message.getComponentTree().findAll(Button.class).forEach(b->ids.add(b.getCustomId()));
        if(marker&&(ids.equals(BUTTONS)||ids.equals(LABELED_BUTTONS)))return true;
        return message.getEmbeds().stream().anyMatch(e->e.getFooter()!=null&&e.getFooter().getText()!=null&&e.getFooter().getText().contains("dd:panel:v1"));
    }
}
