package dev.dydiscord.discord;

import dev.dydiscord.config.CommunitySettings;
import dev.dydiscord.language.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import java.util.*;

/** Renders bounded snapshots and configured public facts without account storage access. */
public final class CommunityInfo {
    private CommunityInfo() {}
    public static MessageEmbed players(Messages messages,String lang,ServerInfo.PlayersSnapshot snapshot){
        String names=snapshot.names().stream().map(Messages::safeDiscord).collect(java.util.stream.Collectors.joining(", "));
        if(names.isEmpty())names=messages.get(true,lang,"players-empty");
        if(snapshot.online()>snapshot.names().size())names+="\n"+messages.render(true,lang,"players-more",Map.of("count",Integer.toString(snapshot.online()-snapshot.names().size())));
        return new EmbedBuilder().setColor(0x5865F2).setTitle(messages.render(true,lang,"players-title",Map.of("count",Integer.toString(snapshot.online())))).setDescription(names).build();
    }
    public static MessageEmbed addresses(Messages messages,String lang,CommunitySettings settings){
        EmbedBuilder card=new EmbedBuilder().setColor(0x5865F2).setTitle(messages.get(true,lang,"addresses-title"));
        if(!settings.javaAddress().isEmpty())card.addField(messages.get(true,lang,"address-java"),Messages.safeDiscord(settings.javaAddress()),false);
        if(!settings.bedrockAddress().isEmpty())card.addField(messages.get(true,lang,"address-bedrock"),Messages.safeDiscord(settings.bedrockAddress()),false);
        if(card.getFields().isEmpty())card.setDescription(messages.get(true,lang,"addresses-empty"));return card.build();
    }
    public static MessageEmbed links(Messages messages,String lang,CommunitySettings settings){
        EmbedBuilder card=new EmbedBuilder().setColor(0x5865F2).setTitle(messages.get(true,lang,"links-title"));
        for(var link:settings.links())card.addField(Messages.safeDiscord(link.name()),link.url().replace("@","%40").replace("(","%28").replace(")","%29"),false);
        if(card.getFields().isEmpty())card.setDescription(messages.get(true,lang,"links-empty"));return card.build();
    }
}
