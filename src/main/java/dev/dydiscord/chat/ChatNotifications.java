package dev.dydiscord.chat;

import java.time.*;
import java.util.*;

/** A full cache suppresses additional sounds; it never resets another player's cooldown. */
public final class ChatNotifications {
    private final Clock clock;
    private final Map<UUID,Instant> deadlines=new HashMap<>();
    public ChatNotifications(Clock clock){this.clock=clock;}
    public synchronized boolean admit(UUID id){
        Instant now=clock.instant();deadlines.values().removeIf(deadline->!now.isBefore(deadline));
        if(deadlines.containsKey(id)||deadlines.size()>=2048)return false;
        deadlines.put(id,now.plusSeconds(15));return true;
    }
    public synchronized void leave(UUID id){deadlines.remove(id);}
    public synchronized void clear(){deadlines.clear();}
}
