package dev.dydiscord.discord;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.chat.ChatBridge;
import dev.dydiscord.language.Messages;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.entity.Player;
import java.util.*;

/** Public server facts only; Bukkit state is captured before Discord rendering. */
public final class ServerInfo {
    private ServerInfo() {}
    public record Snapshot(String serverName,String version,int online,int maximum) {}

    /** Must run through the plugin's main-thread dispatcher. Never includes player identities. */
    public static Snapshot capture(DyDiscord plugin){
        var server=plugin.getServer();
        if(!server.isPrimaryThread())throw new IllegalStateException("Server information requires the main thread");
        List<Player> players=new ArrayList<>(server.getOnlinePlayers());
        ChatBridge chat=plugin.chat();int online=0;
        for(Player player:players)if(chat!=null?chat.visible(player):visible(player,players))online++;
        String version=server.getBukkitVersion().split("-",2)[0];
        return new Snapshot(plugin.settings().serverName(),version,online,server.getMaxPlayers());
    }

    private static boolean visible(Player player,List<Player> players){
        if(ChatBridge.hidden(player))return false;
        for(Player viewer:players)if(!viewer.getUniqueId().equals(player.getUniqueId())&&!viewer.canSee(player))return false;
        return true;
    }

    public static MessageEmbed render(Messages messages,String language,Snapshot snapshot){
        return new EmbedBuilder().setColor(0x5865F2)
            .setTitle(messages.render(true,language,"server-info-title",Map.of("server_name",snapshot.serverName())))
            .addField(messages.get(true,language,"server-info-players"),snapshot.online()+"/"+snapshot.maximum(),true)
            .addField(messages.get(true,language,"server-info-version"),Messages.safeDiscord(snapshot.version()),true)
            .build();
    }
}
