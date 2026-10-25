package dev.dydiscord.api.event;

import dev.dydiscord.api.DyDiscordAPI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired on the main thread after a Minecraft account was linked to a Discord account. */
public final class AccountLinkEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();
    private final DyDiscordAPI.Account account;
    public AccountLinkEvent(DyDiscordAPI.Account account){this.account=account;}
    public DyDiscordAPI.Account getAccount(){return account;}
    @Override public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
