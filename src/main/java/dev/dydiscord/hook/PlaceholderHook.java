package dev.dydiscord.hook;

import dev.dydiscord.DyDiscord;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** %dydiscord_linked%, %dydiscord_discord_name% and %dydiscord_bot_connected%; answered from memory, never from storage. */
public final class PlaceholderHook extends PlaceholderExpansion {
    private final DyDiscord plugin;
    public PlaceholderHook(DyDiscord plugin){this.plugin=plugin;}
    @Override public String getIdentifier(){return "dydiscord";}
    @Override public String getAuthor(){return "Claww";}
    @Override public String getVersion(){return plugin.getDescription().getVersion();}
    @Override public boolean persist(){return true;}
    @Override public String onRequest(OfflinePlayer player,String params){return value(plugin,player==null?null:player.getUniqueId(),params);}
    /** Separate from PlaceholderAPI so the values can be tested without it. */
    public static String value(DyDiscord plugin,java.util.UUID player,String params){
        if(!plugin.ready())return "";
        return switch(params.toLowerCase(java.util.Locale.ROOT)){
            case "linked"->player==null?"":Boolean.toString(plugin.cachedLink(player).isPresent());
            case "discord_name"->player==null?"":plugin.cachedLink(player).map(link->link.discordName()).orElse("");
            case "bot_connected"->Boolean.toString(plugin.bot()!=null&&plugin.bot().usable());
            default->null;
        };
    }
}
