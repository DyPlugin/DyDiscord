package dev.dydiscord.api;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.storage.Store;
import org.bukkit.Bukkit;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Read-only access to DyDiscord account links for other plugins.
 * Lookups for online players are answered from memory; other lookups run on DyDiscord's storage thread.
 * Listen to {@link dev.dydiscord.api.event.AccountLinkEvent} and {@link dev.dydiscord.api.event.AccountUnlinkEvent} for changes.
 */
public final class DyDiscordAPI {
    private DyDiscordAPI(){}
    /** A linked pair of accounts. The Discord name is the last one DyDiscord saw and may be blank. */
    public record Account(UUID minecraftId,String minecraftName,String discordId,String discordName,Instant linkedAt) {}

    /** True while DyDiscord is enabled and its storage has started. */
    public static boolean isAvailable(){DyDiscord plugin=plugin();return plugin!=null&&plugin.ready();}
    /** The link of an online player, or empty when the player is offline, not linked or DyDiscord is unavailable. */
    public static Optional<Account> getOnlineAccount(UUID player){
        DyDiscord plugin=plugin();if(plugin==null||!plugin.ready()||player==null)return Optional.empty();
        return plugin.cachedLink(player).map(DyDiscordAPI::account);
    }
    /** Looks a Minecraft player up, online or offline. Completes empty when unlinked; fails when DyDiscord is unavailable. */
    public static CompletableFuture<Optional<Account>> findByMinecraft(UUID player){
        Objects.requireNonNull(player);DyDiscord plugin=plugin();if(plugin==null||!plugin.ready())return CompletableFuture.failedFuture(new IllegalStateException("DyDiscord is unavailable"));
        return plugin.submit(()->plugin.store().byUuid(player).map(DyDiscordAPI::account));
    }
    /** Looks a Discord user ID (snowflake) up. Completes empty when unlinked; fails when DyDiscord is unavailable. */
    public static CompletableFuture<Optional<Account>> findByDiscord(String discordId){
        Objects.requireNonNull(discordId);DyDiscord plugin=plugin();if(plugin==null||!plugin.ready())return CompletableFuture.failedFuture(new IllegalStateException("DyDiscord is unavailable"));
        return plugin.submit(()->plugin.store().byDiscord(discordId).map(DyDiscordAPI::account));
    }
    public static Account account(Store.Link link){return new Account(link.uuid(),link.name(),link.discord(),link.discordName(),link.linkedAt());}
    private static DyDiscord plugin(){return Bukkit.getPluginManager().getPlugin("DyDiscord") instanceof DyDiscord plugin&&plugin.isEnabled()?plugin:null;}
}
