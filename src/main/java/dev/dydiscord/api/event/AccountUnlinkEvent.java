package dev.dydiscord.api.event;

import dev.dydiscord.api.DyDiscordAPI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the main thread after an account link was removed by the player, the Discord user or an administrator. */
public final class AccountUnlinkEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final DyDiscordAPI.Account account;
    public AccountUnlinkEvent(DyDiscordAPI.Account account){this.account=account;}
    /** The link as it was before removal. */
    public DyDiscordAPI.Account getAccount(){return account;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
