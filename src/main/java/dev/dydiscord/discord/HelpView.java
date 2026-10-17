package dev.dydiscord.discord;

import dev.dydiscord.language.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** One shared, localized help card for slash and message commands; no account data. */
public final class HelpView {
    private HelpView(){}
    public static MessageEmbed card(Messages messages,String language,boolean panel){
        return new EmbedBuilder().setColor(0x5865F2)
            .setTitle(messages.get(true,language,"help-card-title"))
            .setDescription(messages.get(true,language,panel?"help":"commands-help"))
            .addField(messages.get(true,language,"help-card-accounts"),messages.get(true,language,"help-card-account-commands"),false)
            .addField(messages.get(true,language,"help-card-server"),messages.get(true,language,"help-server")+"\n"+messages.get(true,language,"help-community"),false)
            .addField(messages.get(true,language,"help-card-other"),messages.get(true,language,"help-card-other-commands"),false).build();
    }
}
