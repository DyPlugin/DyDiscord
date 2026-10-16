package dev.dydiscord.chat;

import dev.dydiscord.util.PluginThreads;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** The dedicated bounded worker owns all JDBC work. Unknown preferences fail closed. */
public final class ChatPreferences implements AutoCloseable {
    public record Preference(boolean inbound,boolean outbound,boolean notifications) {}
    private static final Preference DEFAULT=new Preference(true,true,false);
    private final Path file;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),PluginThreads.factory("DyDiscord-chat-preferences"),new ThreadPoolExecutor.AbortPolicy()){
        @Override protected void beforeExecute(Thread thread,Runnable operation){workerThread=thread;}
        @Override protected void terminated(){try{if(connection!=null)connection.close();}catch(SQLException failure){java.util.logging.Logger.getLogger("DyDiscord").warning("Chat preference storage could not close cleanly (values withheld).");}}
    };
    private final Map<UUID,Preference> cache=new LinkedHashMap<>(64,.75f,true);
    private final Map<UUID,CompletableFuture<Preference>> loading=new HashMap<>();
    private final Map<UUID,Object> sessions=new HashMap<>();
    private Connection connection;
    private volatile boolean closed;private volatile Thread workerThread;
    private final Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();
    public ChatPreferences(Path file){this.file=Objects.requireNonNull(file);}
    public synchronized Preference peek(UUID id){return cache.get(id);}
    public synchronized void leave(UUID id){cache.remove(id);loading.remove(id);sessions.remove(id);}
    private synchronized void remember(UUID id,Preference value){if(closed)return;cache.put(id,value);while(cache.size()>2048){UUID oldest=cache.keySet().iterator().next();cache.remove(oldest);sessions.remove(oldest);}}
    private synchronized void release(UUID id,CompletableFuture<Preference> result){loading.remove(id,result);if(!cache.containsKey(id)&&!loading.containsKey(id))sessions.remove(id);}
    private Connection db()throws Exception{
        if(connection==null){
            Files.createDirectories(file.toAbsolutePath().getParent());
            Connection candidate=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
            try(var statement=candidate.createStatement()){
                statement.execute("PRAGMA busy_timeout=3000");
                try(var rows=statement.executeQuery("PRAGMA quick_check")){if(!rows.next()||!rows.getString(1).equals("ok"))throw new SQLException("Invalid chat preference integrity");}
                int version;try(var rows=statement.executeQuery("PRAGMA user_version")){version=rows.next()?rows.getInt(1):-1;}
                Set<String> tables=new HashSet<>();try(var rows=statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")){while(rows.next())tables.add(rows.getString(1));}
                if(version<0||version>1||tables.isEmpty()&&version!=0||!tables.isEmpty()&&!tables.equals(Set.of("chat_preferences")))throw new SQLException("Unsupported chat preference schema");
                if(!tables.isEmpty()){
                    Set<String> columns=new HashSet<>();Map<String,Integer> keys=new HashMap<>();try(var rows=statement.executeQuery("PRAGMA table_info(chat_preferences)")){while(rows.next()){columns.add(rows.getString("name"));if(rows.getInt("pk")>0)keys.put(rows.getString("name"),rows.getInt("pk"));}}
                    if(!columns.equals(Set.of("uuid","inbound","outbound","notify"))||!keys.equals(Map.of("uuid",1)))throw new SQLException("Invalid chat preference schema");
                }
                statement.execute("CREATE TABLE IF NOT EXISTS chat_preferences (uuid TEXT PRIMARY KEY, inbound INTEGER NOT NULL, outbound INTEGER NOT NULL, notify INTEGER NOT NULL)");
                statement.execute("PRAGMA user_version=1");connection=candidate;
            }catch(Exception failure){candidate.close();throw failure;}
        }
        return connection;
    }
    private Preference read(UUID id)throws Exception{
        try(var query=db().prepareStatement("SELECT inbound,outbound,notify FROM chat_preferences WHERE uuid=?")){
            query.setString(1,id.toString());try(var rows=query.executeQuery()){if(!rows.next())return DEFAULT;for(int i=1;i<=3;i++){Object value=rows.getObject(i);if(!(value instanceof Number n)||n.intValue()<0||n.intValue()>1||n.doubleValue()!=n.intValue())throw new SQLException("Invalid chat preference value");}return new Preference(rows.getBoolean(1),rows.getBoolean(2),rows.getBoolean(3));}
        }
    }
    public synchronized CompletableFuture<Preference> get(UUID id){
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Chat preferences closed"));
        Preference cached=cache.get(id);if(cached!=null)return CompletableFuture.completedFuture(cached);
        CompletableFuture<Preference> pending=loading.get(id);if(pending!=null)return pending;
        var result=new CompletableFuture<Preference>();this.pending.add(result);result.whenComplete((v,e)->this.pending.remove(result));loading.put(id,result);Object session=sessions.computeIfAbsent(id,ignored->new Object());
        try{worker.execute(()->{try{if(closed)throw new IllegalStateException("Chat preferences closed");Preference value=read(id);synchronized(this){if(sessions.get(id)==session)remember(id,value);release(id,result);}result.complete(value);}catch(Exception failure){release(id,result);result.completeExceptionally(failure);}});}
        catch(RejectedExecutionException failure){release(id,result);result.completeExceptionally(failure);}
        return result;
    }
    public synchronized CompletableFuture<Preference> set(UUID id,String key,boolean enabled){
        if(!Set.of("inbound","outbound","notify").contains(key))throw new IllegalArgumentException("Unknown chat preference");
        if(closed)return CompletableFuture.failedFuture(new IllegalStateException("Chat preferences closed"));
        var result=new CompletableFuture<Preference>();
        this.pending.add(result);result.whenComplete((v,e)->this.pending.remove(result));
        loading.put(id,result);Object session=sessions.computeIfAbsent(id,ignored->new Object());
        try{worker.execute(()->{try{
            if(closed)throw new IllegalStateException("Chat preferences closed");Preference previous=read(id);Preference value=new Preference(key.equals("inbound")?enabled:previous.inbound(),key.equals("outbound")?enabled:previous.outbound(),key.equals("notify")?enabled:previous.notifications());
            try(var query=db().prepareStatement("INSERT INTO chat_preferences(uuid,inbound,outbound,notify) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET inbound=excluded.inbound,outbound=excluded.outbound,notify=excluded.notify")){
                query.setString(1,id.toString());query.setBoolean(2,value.inbound());query.setBoolean(3,value.outbound());query.setBoolean(4,value.notifications());query.executeUpdate();
            }
            synchronized(this){if(sessions.get(id)==session)remember(id,value);release(id,result);}result.complete(value);
        }catch(Exception failure){release(id,result);result.completeExceptionally(failure);}});}
        catch(RejectedExecutionException failure){release(id,result);result.completeExceptionally(failure);}
        return result;
    }
    @Override public void close(){
        synchronized(this){closed=true;cache.clear();loading.clear();sessions.clear();}
        pending.forEach(future->future.completeExceptionally(new IllegalStateException("Chat preferences closed")));worker.shutdownNow();
        if(Thread.currentThread()!=workerThread)try{if(!worker.awaitTermination(4,TimeUnit.SECONDS))java.util.logging.Logger.getLogger("DyDiscord").warning("Chat preference worker shutdown deadline reached (values withheld).");}catch(InterruptedException failure){Thread.currentThread().interrupt();}
    }
}
