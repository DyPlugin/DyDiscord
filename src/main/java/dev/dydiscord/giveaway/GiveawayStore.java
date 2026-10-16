package dev.dydiscord.giveaway;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Independent ledger. Winner rows and ACTIVE -> DRAWN commit in the same transaction. */
public final class GiveawayStore implements AutoCloseable {
    public record Giveaway(String id,String guild,String channel,String message,String prize,int winnerCount,long endsAt,String state,boolean requireLinked,Set<String> roles,boolean announced){}
    public record Winner(String discord,String name){}
    public enum Entry { JOINED, ALREADY, CLOSED, FULL }
    private final Connection db;
    public GiveawayStore(Path path)throws Exception{
        Files.createDirectories(path.toAbsolutePath().getParent());db=DriverManager.getConnection("jdbc:sqlite:"+path.toAbsolutePath());
        try(Statement s=db.createStatement()){
            s.execute("PRAGMA busy_timeout=5000");
            try(ResultSet r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new SQLException("Invalid giveaway integrity");}
            int version;try(ResultSet r=s.executeQuery("PRAGMA user_version")){version=r.next()?r.getInt(1):-1;}
            Set<String> tables=new HashSet<>();try(ResultSet r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")){while(r.next())tables.add(r.getString(1));}
            if(version<0||version>1||tables.isEmpty()&&version!=0||!tables.isEmpty()&&(version!=1||!tables.equals(Set.of("giveaways","entries","winners"))))throw new SQLException("Unsupported giveaway schema");
            if(!tables.isEmpty()){
                validateTable(s,"giveaways",Set.of("id","guild","channel","message","prize","winner_count","ends_at","state","require_linked","roles","announced"),Map.of("id",1));
                validateTable(s,"entries",Set.of("giveaway","discord"),Map.of("giveaway",1,"discord",2));
                validateTable(s,"winners",Set.of("giveaway","position","discord","name"),Map.of("giveaway",1,"position",2));
            }
            s.execute("PRAGMA foreign_keys=ON");s.execute("PRAGMA journal_mode=WAL");
            db.setAutoCommit(false);
            s.execute("CREATE TABLE IF NOT EXISTS giveaways(id TEXT PRIMARY KEY,guild TEXT NOT NULL,channel TEXT NOT NULL,message TEXT NOT NULL DEFAULT '',prize TEXT NOT NULL,winner_count INTEGER NOT NULL,ends_at INTEGER NOT NULL,state TEXT NOT NULL,require_linked INTEGER NOT NULL,roles TEXT NOT NULL,announced INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE TABLE IF NOT EXISTS entries(giveaway TEXT NOT NULL REFERENCES giveaways(id),discord TEXT NOT NULL,PRIMARY KEY(giveaway,discord))");
            s.execute("CREATE TABLE IF NOT EXISTS winners(giveaway TEXT NOT NULL REFERENCES giveaways(id),position INTEGER NOT NULL,discord TEXT NOT NULL,name TEXT NOT NULL,PRIMARY KEY(giveaway,position),UNIQUE(giveaway,discord))");s.execute("CREATE INDEX IF NOT EXISTS giveaway_pending_scope ON giveaways(guild,channel,id)");s.execute("PRAGMA user_version=1");db.commit();db.setAutoCommit(true);
        }catch(Exception e){try{if(!db.getAutoCommit())db.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}db.close();throw e;}
    }
    private static void validateTable(Statement s,String table,Set<String> columns,Map<String,Integer> keys)throws SQLException{
        Set<String> actual=new HashSet<>();Map<String,Integer> primary=new HashMap<>();try(ResultSet r=s.executeQuery("PRAGMA table_info("+table+")")){while(r.next()){actual.add(r.getString("name"));if(r.getInt("pk")>0)primary.put(r.getString("name"),r.getInt("pk"));}}
        if(!actual.equals(columns)||!primary.equals(keys))throw new SQLException("Invalid giveaway table schema");
    }
    public static void validate(long seconds,int winners,String prize){
        if(seconds<60||seconds>2592000||winners<1||winners>20||prize==null||prize.isBlank()||prize.length()>200||prize.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid giveaway duration, winner count or prize");
    }
    public synchronized Giveaway create(String guild,String channel,long seconds,int winners,String prize,boolean linked,Set<String> roles,long now)throws SQLException{
        validate(seconds,winners,prize);String id=UUID.randomUUID().toString().replace("-","");
        transaction(()->{
            try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM giveaways WHERE state IN ('DRAFT','ACTIVE')")){if(r.next()&&r.getInt(1)>=20)throw new SQLException("Giveaway capacity reached");}
            try(PreparedStatement s=db.prepareStatement("INSERT INTO giveaways(id,guild,channel,prize,winner_count,ends_at,state,require_linked,roles) VALUES(?,?,?,?,?,?,'DRAFT',?,?)")){
                s.setString(1,id);s.setString(2,guild);s.setString(3,channel);s.setString(4,prize);s.setInt(5,winners);s.setLong(6,Math.addExact(now,seconds*1000));s.setBoolean(7,linked);s.setString(8,String.join(",",new TreeSet<>(roles)));s.executeUpdate();
            }return null;
        });return find(id).orElseThrow();
    }
    public synchronized Optional<Giveaway> find(String id)throws SQLException{try(PreparedStatement s=db.prepareStatement("SELECT * FROM giveaways WHERE id=?")){s.setString(1,id);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(row(r)):Optional.empty();}}}
    private Giveaway row(ResultSet r)throws SQLException{String roles=r.getString("roles");return new Giveaway(r.getString("id"),r.getString("guild"),r.getString("channel"),r.getString("message"),r.getString("prize"),r.getInt("winner_count"),r.getLong("ends_at"),r.getString("state"),r.getBoolean("require_linked"),roles.isEmpty()?Set.of():Set.of(roles.split(",")),r.getBoolean("announced"));}
    public synchronized List<Giveaway> pending()throws SQLException{return query("SELECT * FROM giveaways WHERE state='ACTIVE' OR (state IN ('DRAWN','CANCELLED') AND announced=0) ORDER BY CASE WHEN state='ACTIVE' THEN 0 ELSE 1 END, ends_at LIMIT 60");}
    public synchronized List<Giveaway> pending(String guild,String channel)throws SQLException{return pending(guild,channel,"");}
    public synchronized List<Giveaway> pending(String guild,String channel,String after)throws SQLException{
        List<Giveaway> result=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("SELECT * FROM giveaways WHERE guild=? AND channel=? AND id>? AND (state='ACTIVE' OR (state IN ('DRAWN','CANCELLED') AND announced=0)) ORDER BY id LIMIT 60")){
            s.setString(1,guild);s.setString(2,channel);s.setString(3,after);try(ResultSet r=s.executeQuery()){while(r.next())result.add(row(r));}
        }return List.copyOf(result);
    }    public synchronized List<Giveaway> list()throws SQLException{return query("SELECT * FROM giveaways ORDER BY CASE WHEN state IN ('DRAFT','ACTIVE') THEN 0 ELSE 1 END,rowid DESC LIMIT 100");}
    private List<Giveaway> query(String sql)throws SQLException{List<Giveaway> result=new ArrayList<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery(sql)){while(r.next())result.add(row(r));}return List.copyOf(result);}
    public synchronized boolean activate(String id,String message)throws SQLException{try(PreparedStatement s=db.prepareStatement("UPDATE giveaways SET message=?,state='ACTIVE' WHERE id=? AND state='DRAFT'")){s.setString(1,message);s.setString(2,id);return s.executeUpdate()==1;}}
    public synchronized boolean end(String id,long now)throws SQLException{try(PreparedStatement s=db.prepareStatement("UPDATE giveaways SET ends_at=MIN(ends_at,?) WHERE id=? AND state='ACTIVE'")){s.setLong(1,now);s.setString(2,id);return s.executeUpdate()==1;}}
    public synchronized boolean cancel(String id)throws SQLException{try(PreparedStatement s=db.prepareStatement("UPDATE giveaways SET state='CANCELLED' WHERE id=? AND state IN ('DRAFT','ACTIVE')")){s.setString(1,id);return s.executeUpdate()==1;}}
    public synchronized Entry enter(String id,String actor,long now)throws SQLException{return transaction(()->{
        Optional<Giveaway> found=find(id);if(found.isEmpty()||!found.get().state().equals("ACTIVE")||found.get().endsAt()<=now)return Entry.CLOSED;
        try(PreparedStatement s=db.prepareStatement("SELECT 1 FROM entries WHERE giveaway=? AND discord=?")){s.setString(1,id);s.setString(2,actor);try(ResultSet r=s.executeQuery()){if(r.next())return Entry.ALREADY;}}
        try(PreparedStatement s=db.prepareStatement("SELECT count(*) FROM entries WHERE giveaway=?")){s.setString(1,id);try(ResultSet r=s.executeQuery()){if(r.next()&&r.getInt(1)>=10000)return Entry.FULL;}}
        try(PreparedStatement s=db.prepareStatement("INSERT INTO entries VALUES(?,?)")){s.setString(1,id);s.setString(2,actor);s.executeUpdate();}return Entry.JOINED;
    });}
    public synchronized List<String> entrants(String id)throws SQLException{List<String> result=new ArrayList<>();try(PreparedStatement s=db.prepareStatement("SELECT discord FROM entries WHERE giveaway=? ORDER BY discord LIMIT 10000")){s.setString(1,id);try(ResultSet r=s.executeQuery()){while(r.next())result.add(r.getString(1));}}return List.copyOf(result);}
    public synchronized boolean commitDraw(String id,List<Winner> winners)throws SQLException{return transaction(()->{
        Giveaway g=find(id).orElseThrow();if(!g.state().equals("ACTIVE"))return false;if(winners.size()>g.winnerCount())throw new SQLException("Too many giveaway winners");
        try(PreparedStatement s=db.prepareStatement("UPDATE giveaways SET state='DRAWN' WHERE id=? AND state='ACTIVE'")){s.setString(1,id);if(s.executeUpdate()!=1)return false;}
        try(PreparedStatement s=db.prepareStatement("INSERT INTO winners VALUES(?,?,?,?)")){for(int i=0;i<winners.size();i++){Winner winner=winners.get(i);s.setString(1,id);s.setInt(2,i);s.setString(3,winner.discord());s.setString(4,winner.name());s.addBatch();}s.executeBatch();}return true;
    });}
    public synchronized List<Winner> winners(String id)throws SQLException{List<Winner> result=new ArrayList<>();try(PreparedStatement s=db.prepareStatement("SELECT discord,name FROM winners WHERE giveaway=? ORDER BY position")){s.setString(1,id);try(ResultSet r=s.executeQuery()){while(r.next())result.add(new Winner(r.getString(1),r.getString(2)));}}return List.copyOf(result);}
    public synchronized void announced(String id)throws SQLException{try(PreparedStatement s=db.prepareStatement("UPDATE giveaways SET announced=1 WHERE id=? AND state IN ('DRAWN','CANCELLED')")){s.setString(1,id);s.executeUpdate();}}
    private interface SqlCall<T>{T call()throws SQLException;}
    private <T>T transaction(SqlCall<T> call)throws SQLException{db.setAutoCommit(false);try{T result=call.call();db.commit();return result;}catch(SQLException|RuntimeException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}}
    @Override public synchronized void close()throws SQLException{db.close();}
}
