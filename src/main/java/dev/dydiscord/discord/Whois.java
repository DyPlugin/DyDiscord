package dev.dydiscord.discord;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.chat.ChatBridge;
import dev.dydiscord.config.FeatureSettings;
import dev.dydiscord.storage.Store;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import java.util.*;
import java.util.regex.*;

/** Discord /whois for staff: the linked account of a Minecraft player and when they were last seen. Replies are private. */
public final class Whois {
    private static final Pattern MENTION=Pattern.compile("<@!?([0-9]{17,20})>"),NAME=Pattern.compile("[A-Za-z0-9_.]{1,16}");
    private Whois(){}
    /** Administrators always; otherwise a listed role, or Manage Server when no roles are listed. */
    public static boolean allowed(Member member,FeatureSettings.Whois settings){
        if(member==null)return false;if(member.hasPermission(Permission.ADMINISTRATOR))return true;
        if(settings.roles().isEmpty())return member.hasPermission(Permission.MANAGE_SERVER);
        for(Role role:member.getRoles())if(settings.roles().contains(role.getId()))return true;return false;
    }
    /** Discord hides the command from members without Manage Server unless roles are listed; the runtime check still applies. */
    public static DefaultMemberPermissions defaultPermissions(FeatureSettings.Whois settings){return settings.roles().isEmpty()?DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER):DefaultMemberPermissions.ENABLED;}
    /** Whether a registered command already matches, so a reconnect does not upsert it again. */
    public static boolean sameShape(Command previous,SlashCommandData data){
        if(previous.getOptions().size()!=1||data.getOptions().size()!=1)return false;var old=previous.getOptions().get(0);var next=data.getOptions().get(0);
        Long oldPermissions=previous.getDefaultPermissions()==null?null:previous.getDefaultPermissions().getPermissionsRaw(),nextPermissions=data.getDefaultPermissions()==null?null:data.getDefaultPermissions().getPermissionsRaw();
        return old.getName().equals(next.getName())&&old.getDescription().equals(next.getDescription())&&old.isRequired()==next.isRequired()&&Objects.equals(oldPermissions,nextPermissions);
    }
    /** What /whois shows for a player name, Discord mention or Discord ID. Runs on the I/O worker. */
    public static String lookup(DyDiscord plugin,String query,String lang)throws Exception{
        String value=query==null?"":query.strip();Matcher mention=MENTION.matcher(value);Optional<Store.Link> link;
        if(mention.matches())link=plugin.store().byDiscord(mention.group(1));
        else if(dev.dydiscord.config.Settings.snowflake(value))link=plugin.store().byDiscord(value);
        else if(NAME.matcher(value).matches())link=plugin.store().byName(value);
        else return plugin.messages().render(true,lang,"whois-not-found",Map.of("query",dev.dydiscord.chat.ChatText.plain(value,32)));
        String name=link.map(Store.Link::name).orElse(value);UUID known=link.map(Store.Link::uuid).orElse(null);
        Seen seen=plugin.mainValue(()->seen(plugin,known,name));
        String seenLine=seen.online()?plugin.messages().get(true,lang,"whois-online"):seen.lastPlayed()>0?plugin.messages().render(true,lang,"whois-last-seen",Map.of("time",Long.toString(seen.lastPlayed()/1000))):plugin.messages().get(true,lang,"whois-never");
        if(link.isPresent()){Store.Link found=link.get();
            String discord=found.discordName().isBlank()?found.discord():found.discordName();
            return plugin.messages().render(true,lang,"whois-linked",Map.of("player",found.name(),"discord_id",found.discord(),"discord",discord,"linked",Long.toString(found.linkedAt().getEpochSecond())))+"\n"+seenLine;}
        if(!seen.online())return plugin.messages().render(true,lang,"whois-not-found",Map.of("query",dev.dydiscord.chat.ChatText.plain(value,32)));
        return plugin.messages().render(true,lang,"whois-not-linked",Map.of("player",seen.name()))+"\n"+seenLine;
    }
    record Seen(boolean online,long lastPlayed,String name) {}
    /** Main thread: an online player counts only when the vanish policy shows them; otherwise the last logout time. */
    static Seen seen(DyDiscord plugin,UUID known,String name){
        org.bukkit.entity.Player online=known!=null?plugin.getServer().getPlayer(known):plugin.getServer().getPlayerExact(name);
        ChatBridge chat=plugin.chat();boolean visible=online!=null&&online.isOnline()&&(chat==null?!ChatBridge.hidden(online):chat.visible(online));
        if(visible)return new Seen(true,0,online.getName());
        long last=known==null?0:plugin.getServer().getOfflinePlayer(known).getLastPlayed();return new Seen(false,last,name);
    }
}
