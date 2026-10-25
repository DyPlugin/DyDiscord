package dev.dydiscord.discord;

import dev.dydiscord.config.AccountSettings;
import dev.dydiscord.language.Messages;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Private account displays. External names are escaped before entering templates. */
public final class AccountView {
    private AccountView(){}
    public static String details(Messages messages,String lang,Store.Link link,String discord,AccountSettings settings,List<String> roles){
        Locale locale=lang.equals("en")?Locale.ENGLISH:Locale.forLanguageTag(lang);
        String date=DateTimeFormatter.ofPattern(lang.equals("en")?"MMMM d, yyyy, HH:mm z":"d MMMM yyyy, HH:mm z",locale).withZone(settings.zone()).format(link.linkedAt());
        String result=messages.render(true,lang,"account",Map.of("name",link.name(),"discord",discord,"date",date));
        if(!Messages.placeholders(messages.get(true,lang,"account")).contains("date"))result+="\n"+messages.render(true,lang,"account-date",Map.of("date",date));
        if(settings.showRoles()&&!roles.isEmpty()){
            // Bound the whole field as well as individual names; never truncate Markdown escapes.
            StringBuilder names=new StringBuilder();for(String role:roles){String safe=Messages.safeDiscord(dev.dydiscord.chat.ChatText.plain(role,100));if(names.length()+safe.length()+2>1200)break;if(!names.isEmpty())names.append(", ");names.append(safe);}
            if(!names.isEmpty())result+="\n"+messages.get(true,lang,"account-roles")+" "+names;
        }
        if(result.length()>3900)throw new IllegalArgumentException("Account message too long");return result;
    }
    public static Container confirmation(Messages messages,String lang,String token,int seconds){
        return Container.of(TextDisplay.of(messages.render(true,lang,"confirm",Map.of("seconds",Integer.toString(seconds)))),ActionRow.of(
            Button.danger("dd:confirm:"+token,messages.get(true,lang,"confirm-button")),
            Button.success("dd:cancel:"+token,messages.get(true,lang,"cancel-button")))).withAccentColor(0xED4245);
    }
}
