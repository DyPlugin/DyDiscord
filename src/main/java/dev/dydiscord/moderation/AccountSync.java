package dev.dydiscord.moderation;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.storage.Store;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps linked Discord members in step with Minecraft: nickname sync and, when enabled, ban sync in either direction. */
public final class AccountSync implements Listener {
    private final DyDiscord plugin;
    /** Players banned because of a Discord ban; their kick must not echo the ban back to Discord. */
    private final Set<UUID> fromDiscord=ConcurrentHashMap.newKeySet();
    public AccountSync(DyDiscord plugin){this.plugin=plugin;}

    /** Called on the storage worker right after an account is linked. */
    public void linked(Store.Link link){nickname(link);}
    private void nickname(Store.Link link){
        if(!plugin.ready()||!plugin.settings().moderation().nicknameSync()||plugin.bot()==null)return;
        try{plugin.bot().staff().nickname(link.discord(),plugin.settings().moderation().nickname(link.name()));}catch(Exception failure){plugin.error(failure);}
    }
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event){
        if(!plugin.ready()||!plugin.settings().moderation().nicknameSync())return;UUID uuid=event.getPlayer().getUniqueId();String name=event.getPlayer().getName();
        plugin.submit(()->{plugin.store().byUuid(uuid).ifPresent(link->nickname(new Store.Link(link.uuid(),link.discord(),name,link.linkedAt(),link.version(),link.discordName())));return null;}).exceptionally(e->{plugin.error(e);return null;});
    }
    /** A ban on Minecraft kicks the player; once the kick has happened the ban list tells whether it was a ban. */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void kick(PlayerKickEvent event){
        if(!plugin.ready()||!plugin.settings().moderation().banToDiscord())return;
        Player player=event.getPlayer();UUID uuid=player.getUniqueId();String name=player.getName();
        if(fromDiscord.remove(uuid))return;
        plugin.onMain(()->{
            OfflinePlayer offline=Bukkit.getOfflinePlayer(uuid);if(!offline.isBanned())return;
            plugin.submit(()->{Optional<Store.Link> link=plugin.store().byUuid(uuid);if(link.isPresent()&&plugin.bot()!=null)plugin.bot().staff().ban(link.get().discord(),"Banned on Minecraft as "+name);return null;}).exceptionally(e->{plugin.error(e);return null;});
        });
    }
    /** A Discord ban or unban of a linked member; runs on the Discord thread and hands the work to the storage worker. */
    public void fromDiscord(String discordId,boolean banned){
        if(!plugin.ready()||!plugin.settings().moderation().banFromDiscord())return;
        plugin.submit(()->{
            Optional<Store.Link> link=plugin.store().byDiscord(discordId);if(link.isEmpty())return null;UUID uuid=link.get().uuid();String reason=plugin.messages().render(false,plugin.settings().botLanguage(),"moderation-banned",Map.of());
            plugin.onMain(()->{
                OfflinePlayer offline=Bukkit.getOfflinePlayer(uuid);
                if(!banned){if(offline.isBanned())((BanList<org.bukkit.profile.PlayerProfile>)Bukkit.getBanList(BanList.Type.PROFILE)).pardon(offline.getPlayerProfile());return;}
                if(offline.isBanned())return;
                Player online=offline.getPlayer();if(online!=null)fromDiscord.add(uuid);
                offline.ban(reason,(java.util.Date)null,"DyDiscord");
                if(online!=null)plugin.tasks().entity(online,()->online.kickPlayer(reason),1);
            });return null;
        }).exceptionally(e->{plugin.error(e);return null;});
    }
}
