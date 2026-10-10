package dev.dydiscord.linking;

import dev.dydiscord.storage.Store;
import dev.dydiscord.config.Settings;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

/** All entry points share this lock: code consumption follows the durable unique insert. */
public final class Linking {
    public static final class Rejected extends Exception {public final String key; public Rejected(String key){super(key);this.key=key;}}
    public record Code(UUID uuid,String name,String digest,Instant expires,long session) {}
    public record Confirmation(String actor,Store.Link target,String permission,String reason,Instant expires,long generation) {}
    public record Issued(String code,int seconds) { @Override public String toString(){return "Issued[code=REDACTED]";} }
    private final Store store; private final Clock clock; private final SecureRandom random;
    private final Map<UUID,Code> codes=new HashMap<>();private final Map<UUID,Instant> generated=new HashMap<>();
    private final Map<String,Confirmation> confirmations=new HashMap<>();
    private final Map<String,Deque<Instant>> attempts=new HashMap<>(); private final Deque<Instant> global=new ArrayDeque<>();
    private final byte[] salt=new byte[32];private long generation;
    public Linking(Store store,Clock clock,SecureRandom random){this.store=store;this.clock=clock;this.random=random;random.nextBytes(salt);}
    private String digest(String value){try{MessageDigest d=MessageDigest.getInstance("SHA-256");d.update(salt);return HexFormat.of().formatHex(d.digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public synchronized Issued issue(UUID uuid,String name,long session,Settings settings,boolean available)throws Exception{
        cleanup();if(!settings.linking())throw new Rejected("linking-disabled");if(!available)throw new Rejected("bot-unavailable");
        if(!store.writable())throw new Rejected("data-unavailable");if(store.byUuid(uuid).isPresent())throw new Rejected("already-linked");
        Instant now=clock.instant();if(generated.containsKey(uuid)&&now.isBefore(generated.get(uuid).plusSeconds(settings.cooldown())))throw new Rejected("cooldown");
        if(codes.size()>=4096||generated.size()>=8192)throw new Rejected("busy");
        String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789",value;
        do{StringBuilder out=new StringBuilder();for(int i=0;i<settings.codeLength();i++)out.append(alphabet.charAt(random.nextInt(alphabet.length())));value=out.toString();}while(find(digest(value))!=null);
        codes.put(uuid,new Code(uuid,name,digest(value),now.plusSeconds(settings.codeExpiry()),session));generated.put(uuid,now);
        return new Issued(value,settings.codeExpiry());
    }
    public synchronized Optional<Code> inspect(String input){cleanup();if(input==null||!input.matches("[A-HJ-NP-Z2-9]{8,16}"))return Optional.empty();return Optional.ofNullable(find(digest(input)));}
    private Code find(String hash){for(Code c:codes.values())if(MessageDigest.isEqual(c.digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))return c;return null;}
    public synchronized Store.Link redeem(String input,String discord,Settings settings,boolean contextValid,Predicate<Code> ownerValid)throws Exception{
        return redeem(input,discord,"",settings,contextValid,ownerValid);
    }
    public synchronized Store.Link redeem(String input,String discord,String discordName,Settings settings,boolean contextValid,Predicate<Code> ownerValid)throws Exception{
        cleanup();Instant now=clock.instant();
        if(!contextValid||!Settings.snowflake(discord)||!settings.linking())throw new Rejected("invalid-code");
        if(attempts.size()>=4096&&!attempts.containsKey(discord))throw new Rejected("rate-limit");
        Deque<Instant> local=attempts.computeIfAbsent(discord,k->new ArrayDeque<>());prune(local,now.minusSeconds(settings.attemptWindow()));prune(global,now.minusSeconds(300));
        if(local.size()>=settings.attempts()||global.size()>=1000)throw new Rejected("rate-limit");
        Optional<Code> found=input!=null&&input.matches("[A-HJ-NP-Z2-9]{8,16}")?Optional.ofNullable(find(digest(input))):Optional.empty();
        if(found.isEmpty()||!ownerValid.test(found.get())||!clock.instant().isBefore(found.get().expires)){local.addLast(now);global.addLast(now);throw new Rejected("invalid-code");}
        Code c=found.get();if(store.byUuid(c.uuid).isPresent()||store.byDiscord(discord).isPresent())throw new Rejected("already-linked");
        Store.Link saved;try{saved=store.link(c.uuid,discord,c.name,discordName,now,()->ownerValid.test(c)&&clock.instant().isBefore(c.expires));}catch(Store.Revoked e){throw new Rejected("invalid-code");}codes.remove(c.uuid);attempts.remove(discord);return saved;
    }
    public synchronized String confirm(String actor,Store.Link target,String permission,String reason,int seconds){
        cleanup();if(confirmations.size()>=4096)throw new IllegalStateException("Confirmation capacity reached");
        confirmations.values().removeIf(c->c.actor.equals(actor));byte[] bytes=new byte[16];random.nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
        confirmations.put(token,new Confirmation(actor,target,permission,reason,clock.instant().plusSeconds(seconds),generation));return token;
    }
    public synchronized Optional<Confirmation> pending(String token,String actor){cleanup();Confirmation c=confirmations.get(token);return c!=null&&c.actor.equals(actor)?Optional.of(c):Optional.empty();}
    public synchronized boolean remove(String token,String actor,Predicate<String> permission,Consumer<Confirmation> audit)throws Exception{
        cleanup();Confirmation c=confirmations.get(token);if(c==null||!c.actor.equals(actor)||c.generation!=generation)throw new Rejected("confirmation-invalid");
        confirmations.remove(token);if(!permission.test(c.permission))throw new Rejected("no-permission");if(!clock.instant().isBefore(c.expires))throw new Rejected("confirmation-invalid");
        Optional<Store.Link> current=store.byUuid(c.target.uuid());if(current.isEmpty()||!current.get().version().equals(c.target.version()))throw new Rejected("confirmation-invalid");
        audit.accept(c);if(!permission.test(c.permission))throw new Rejected("no-permission");if(!clock.instant().isBefore(c.expires))throw new Rejected("confirmation-invalid");boolean result=store.unlink(c.target);codes.remove(c.target.uuid());confirmations.values().removeIf(v->v.target.uuid().equals(c.target.uuid()));return result;
    }
    public synchronized void cancel(String actor){confirmations.values().removeIf(c->c.actor.equals(actor));}
    public synchronized void leave(UUID uuid){codes.remove(uuid);cancel("mc:"+uuid);}
    public synchronized void invalidate(){generation++;codes.clear();confirmations.clear();attempts.clear();global.clear();generated.clear();}
    public synchronized void cleanup(){Instant now=clock.instant();codes.values().removeIf(c->!now.isBefore(c.expires));confirmations.values().removeIf(c->!now.isBefore(c.expires));generated.values().removeIf(t->!now.isBefore(t.plusSeconds(900)));attempts.values().removeIf(q->q.isEmpty()||!now.isBefore(q.peekLast().plusSeconds(900)));}
    static void prune(Deque<Instant> q,Instant boundary){while(!q.isEmpty()&&!q.peekFirst().isAfter(boundary))q.removeFirst();}
}
