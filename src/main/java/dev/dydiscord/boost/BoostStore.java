package dev.dydiscord.boost;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Append-only period receipts: a claim is committed before any external command. */
public final class BoostStore implements AutoCloseable {
    public record Receipt(String guild,String discord,Instant period,UUID uuid,String name,Instant claimedAt,String status,String removal) {}
    private final Connection db;
    private volatile boolean writable=true;
    public BoostStore(Path file)throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());Class.forName("org.sqlite.JDBC");
        db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
        try(Statement s=db.createStatement()){
            s.execute("PRAGMA busy_timeout=3000");
            try(ResultSet r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!"ok".equals(r.getString(1)))throw new SQLException("Boost database integrity check failed");}
            Set<String> tables=new HashSet<>();try(ResultSet r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")){while(r.next())tables.add(r.getString(1));}
            if(!tables.isEmpty()){
                if(!tables.containsAll(Set.of("schema_version","boost_receipts")))throw new SQLException("Invalid boost database schema");
                try(ResultSet r=s.executeQuery("SELECT version FROM schema_version")){if(!r.next()||r.getInt(1)!=1||r.next())throw new SQLException("Unsupported boost database schema");}
                Set<String> columns=new HashSet<>();Map<String,Integer> key=new HashMap<>();try(ResultSet r=s.executeQuery("PRAGMA table_info(boost_receipts)")){while(r.next()){columns.add(r.getString("name"));if(r.getInt("pk")>0)key.put(r.getString("name"),r.getInt("pk"));}}
                if(!columns.containsAll(Set.of("guild","discord","period","uuid","name","claimed_at","status","removal")))throw new SQLException("Invalid boost database schema");
                if(!key.equals(Map.of("guild",1,"discord",2,"period",3)))throw new SQLException("Invalid boost receipt uniqueness constraint");
                try(ResultSet r=s.executeQuery("SELECT 1 FROM boost_receipts WHERE status NOT IN ('CLAIMED','DISPATCHED','UNKNOWN') OR removal NOT IN ('NONE','CLAIMED','DISPATCHED','UNKNOWN') LIMIT 1")){if(r.next())throw new SQLException("Invalid boost receipt state");}
            }
            s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");db.setAutoCommit(false);
            if(tables.isEmpty()){
                s.execute("CREATE TABLE schema_version(version INTEGER NOT NULL)");s.execute("INSERT INTO schema_version VALUES(1)");
                s.execute("CREATE TABLE boost_receipts(guild TEXT NOT NULL,discord TEXT NOT NULL,period TEXT NOT NULL,uuid TEXT NOT NULL,name TEXT NOT NULL,claimed_at INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('CLAIMED','DISPATCHED','UNKNOWN')),removal TEXT NOT NULL CHECK(removal IN ('NONE','CLAIMED','DISPATCHED','UNKNOWN')),PRIMARY KEY(guild,discord,period))");
                s.execute("CREATE INDEX boost_cooldown ON boost_receipts(guild,discord,claimed_at)");
            }
            s.execute("UPDATE boost_receipts SET status='UNKNOWN' WHERE status='CLAIMED'");
            s.execute("UPDATE boost_receipts SET removal='UNKNOWN' WHERE removal='CLAIMED'");
            db.commit();db.setAutoCommit(true);
        }catch(Exception e){try{if(!db.getAutoCommit())db.rollback();}catch(SQLException suppressed){e.addSuppressed(suppressed);}db.close();throw e;}
    }
    public boolean writable(){return writable;}
    private void requireWrite()throws SQLException{if(!writable)throw new SQLException("Boost receipt mutations unavailable");}
    public synchronized boolean claim(String guild,String discord,Instant period,UUID uuid,String name,Instant now,int cooldownHours)throws SQLException{
        requireWrite();if(cooldownHours<168)throw new IllegalArgumentException("Boost cooldown cannot be shorter than seven days");
        if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Invalid linked Minecraft name");
        db.setAutoCommit(false);
        try{
            try(PreparedStatement s=db.prepareStatement("SELECT MAX(claimed_at) FROM boost_receipts WHERE guild=? AND discord=?")){
                s.setString(1,guild);s.setString(2,discord);try(ResultSet r=s.executeQuery()){if(r.next()){long last=r.getLong(1);if(!r.wasNull()&&now.toEpochMilli()-last<Duration.ofHours(cooldownHours).toMillis()){db.rollback();return false;}}}
            }
            int inserted;try(PreparedStatement s=db.prepareStatement("INSERT OR IGNORE INTO boost_receipts VALUES(?,?,?,?,?,?,'CLAIMED','NONE')")){
                s.setString(1,guild);s.setString(2,discord);s.setString(3,period.toString());s.setString(4,uuid.toString());s.setString(5,name);s.setLong(6,now.toEpochMilli());inserted=s.executeUpdate();
            }
            db.commit();return inserted==1;
        }catch(SQLException e){writable=false;try{db.rollback();}catch(SQLException suppressed){e.addSuppressed(suppressed);}throw e;}
        finally{try{db.setAutoCommit(true);}catch(SQLException e){writable=false;throw e;}}
    }
    public synchronized List<Receipt> receipts(String guild,String discord)throws SQLException{return readReceipts(guild,discord,false);}
    public synchronized List<Receipt> pendingRemoval(String guild,String discord)throws SQLException{return readReceipts(guild,discord,true);}
    private List<Receipt> readReceipts(String guild,String discord,boolean pending)throws SQLException{
        List<Receipt> result=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("SELECT * FROM boost_receipts WHERE guild=? AND discord=?"+(pending?" AND removal='NONE'":"")+" ORDER BY claimed_at LIMIT 20")){
            s.setString(1,guild);s.setString(2,discord);try(ResultSet r=s.executeQuery()){while(r.next())result.add(new Receipt(r.getString("guild"),r.getString("discord"),Instant.parse(r.getString("period")),UUID.fromString(r.getString("uuid")),r.getString("name"),Instant.ofEpochMilli(r.getLong("claimed_at")),r.getString("status"),r.getString("removal")));}
        }return List.copyOf(result);
    }
    public synchronized boolean claimRemoval(Receipt receipt)throws SQLException{return update(receipt,"removal","CLAIMED","NONE")==1;}
    public synchronized void finish(Receipt receipt,boolean removal,boolean dispatched)throws SQLException{update(receipt,removal?"removal":"status",dispatched?"DISPATCHED":"UNKNOWN","CLAIMED");}
    private int update(Receipt r,String column,String status,String expected)throws SQLException{
        requireWrite();try(PreparedStatement s=db.prepareStatement("UPDATE boost_receipts SET "+column+"=? WHERE guild=? AND discord=? AND period=? AND "+column+"=?")){
            s.setString(1,status);s.setString(2,r.guild());s.setString(3,r.discord());s.setString(4,r.period().toString());s.setString(5,expected);return s.executeUpdate();
        }catch(SQLException e){writable=false;throw e;}
    }
    public synchronized List<String> discordAfter(String guild,String cursor,int limit)throws SQLException{
        if(limit<1||limit>20)throw new IllegalArgumentException("Boost page exceeds bound");List<String> result=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("SELECT DISTINCT discord FROM boost_receipts WHERE guild=? AND discord>? ORDER BY discord LIMIT ?")){
            s.setString(1,guild);s.setString(2,cursor);s.setInt(3,limit);try(ResultSet r=s.executeQuery()){while(r.next())result.add(r.getString(1));}
        }return List.copyOf(result);
    }
    public synchronized Map<String,Integer> statusCounts()throws SQLException{
        Map<String,Integer> result=new TreeMap<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT status,COUNT(*) FROM boost_receipts GROUP BY status")){while(r.next())result.put(r.getString(1),r.getInt(2));}return Map.copyOf(result);
    }
    @Override public synchronized void close()throws SQLException{writable=false;db.close();}
}
