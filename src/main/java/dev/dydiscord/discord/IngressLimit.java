package dev.dydiscord.discord;
import java.time.*;
import java.util.*;
/** Admission happens before creating replies, forms or asynchronous work. */
public final class IngressLimit {
    private final Clock clock;private final Map<String,Instant> users=new HashMap<>();private final Deque<Instant> global=new ArrayDeque<>();
    public IngressLimit(Clock clock){this.clock=clock;}
    public synchronized boolean admit(String user){Instant now=clock.instant();users.values().removeIf(t->!now.isBefore(t.plusSeconds(60)));while(!global.isEmpty()&&!now.isBefore(global.peekFirst().plusSeconds(60)))global.removeFirst();if(global.size()>=120||users.size()>=4096&&!users.containsKey(user)||users.containsKey(user)&&now.isBefore(users.get(user).plusSeconds(1)))return false;users.put(user,now);global.addLast(now);return true;}
    public synchronized void clear(){users.clear();global.clear();}
}
