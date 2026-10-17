package dev.dydiscord.discord;
import java.time.*;
import java.util.*;
/** Admission happens before creating replies, forms or asynchronous work. */
public final class IngressLimit {
    private final Clock clock;private final int burst,window;private final Map<String,Deque<Instant>> users=new HashMap<>();private final Deque<Instant> global=new ArrayDeque<>();
    public IngressLimit(Clock clock){this(clock,1,1);}
    public IngressLimit(Clock clock,int burst,int window){if(burst<1||burst>10||window<1||window>60)throw new IllegalArgumentException();this.clock=clock;this.burst=burst;this.window=window;}
    public synchronized boolean admit(String user){Instant now=clock.instant();users.values().forEach(times->{while(!times.isEmpty()&&!now.isBefore(times.peekFirst().plusSeconds(window)))times.removeFirst();});users.values().removeIf(Deque::isEmpty);while(!global.isEmpty()&&!now.isBefore(global.peekFirst().plusSeconds(60)))global.removeFirst();if(global.size()>=120||users.size()>=4096&&!users.containsKey(user))return false;Deque<Instant> times=users.computeIfAbsent(user,key->new ArrayDeque<>());if(times.size()>=burst)return false;times.addLast(now);global.addLast(now);return true;}
    public synchronized void clear(){users.clear();global.clear();}
}
