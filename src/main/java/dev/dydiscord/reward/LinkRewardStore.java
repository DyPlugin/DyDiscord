package dev.dydiscord.reward;

import dev.dydiscord.storage.Store;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Permanent identity tombstones and at-most-once command receipts. Never delete on unlink. */
public final class LinkRewardStore implements AutoCloseable {
    private final Connection db;
    private volatile boolean writable=true;
    public LinkRewardStore(Path file)throws Exception{
        Files.createDirectories(file.toAbsolutePath().getParent());Class.forName("org.sqlite.JDBC");
        db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
        try(Statement statement=db.createStatement()){
            statement.execute("PRAGMA busy_timeout=3000");
            try(ResultSet result=statement.executeQuery("PRAGMA quick_check")){if(!result.next()||!"ok".equals(result.getString(1)))throw new SQLException("Link reward integrity check failed");}
            Set<String> tables=new HashSet<>();try(ResultSet result=statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")){while(result.next())tables.add(result.getString(1));}
            if(!tables.isEmpty()){
                if(!tables.equals(Set.of("schema_version","seen_identities","link_receipts")))throw new SQLException("Invalid link reward schema");
                try(ResultSet result=statement.executeQuery("SELECT version FROM schema_version")){if(!result.next()||result.getInt(1)!=1||result.next())throw new SQLException("Unsupported link reward schema");}
                validateKey(statement,"seen_identities",Map.of("kind",1,"identity",2));
                validateKey(statement,"link_receipts",Map.of("uuid",1));
                // Verify the independent Discord uniqueness constraint, not merely column names.
                boolean unique=false;
                List<String> indexes=new ArrayList<>();try(ResultSet result=statement.executeQuery("PRAGMA index_list(link_receipts)")){while(result.next())if(result.getInt("unique")==1)indexes.add(result.getString("name"));}
                for(String index:indexes){List<String> columns=new ArrayList<>();try(ResultSet result=statement.executeQuery("PRAGMA index_info('"+index.replace("'","''")+"')")){while(result.next())columns.add(result.getString("name"));}if(columns.equals(List.of("discord")))unique=true;}
                if(!unique)throw new SQLException("Missing Discord reward uniqueness constraint");
                try(ResultSet result=statement.executeQuery("SELECT 1 FROM link_receipts WHERE status NOT IN ('CLAIMED','DISPATCHED','UNKNOWN') LIMIT 1")){if(result.next())throw new SQLException("Invalid link reward state");}
            }
            statement.execute("PRAGMA journal_mode=WAL");statement.execute("PRAGMA synchronous=FULL");db.setAutoCommit(false);
            if(tables.isEmpty()){
                statement.execute("CREATE TABLE schema_version(version INTEGER NOT NULL)");statement.execute("INSERT INTO schema_version VALUES(1)");
                statement.execute("CREATE TABLE seen_identities(kind TEXT NOT NULL CHECK(kind IN ('minecraft','discord')),identity TEXT NOT NULL,PRIMARY KEY(kind,identity))");
                statement.execute("CREATE TABLE link_receipts(uuid TEXT PRIMARY KEY NOT NULL,discord TEXT UNIQUE NOT NULL,link_version TEXT NOT NULL,claimed_at INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('CLAIMED','DISPATCHED','UNKNOWN')))");
            }
            statement.execute("UPDATE link_receipts SET status='UNKNOWN' WHERE status='CLAIMED'");db.commit();db.setAutoCommit(true);
        }catch(Exception error){try{if(!db.getAutoCommit())db.rollback();}catch(SQLException suppressed){error.addSuppressed(suppressed);}db.close();throw error;}
    }
    private static void validateKey(Statement statement,String table,Map<String,Integer> expected)throws SQLException{
        Map<String,Integer> actual=new HashMap<>();try(ResultSet result=statement.executeQuery("PRAGMA table_info("+table+")")){while(result.next())if(result.getInt("pk")>0)actual.put(result.getString("name"),result.getInt("pk"));}
        if(!actual.equals(expected))throw new SQLException("Invalid link reward identity key");
    }
    /** Startup-only baseline, before accepting new links; does not award existing accounts. */
    public void seedExisting(Store source)throws SQLException{
        String cursor="";while(true){List<Store.Link> page=source.linksPage(cursor,20);for(Store.Link link:page)claim(link,false);if(page.size()<20)return;cursor=page.get(page.size()-1).uuid().toString();}
    }
    public boolean writable(){return writable;}
    public synchronized boolean eligible(Store.Link link)throws SQLException{
        try(PreparedStatement statement=db.prepareStatement("SELECT 1 FROM seen_identities WHERE (kind='minecraft' AND identity=?) OR (kind='discord' AND identity=?) LIMIT 1")){
            statement.setString(1,link.uuid().toString());statement.setString(2,link.discord());try(ResultSet result=statement.executeQuery()){return !result.next();}
        }
    }
    /** Always reserve BOTH identities, including disabled or ineligible events. */
    public synchronized boolean claim(Store.Link link,boolean reward)throws SQLException{
        if(!writable)throw new SQLException("Link reward mutations unavailable");
        db.setAutoCommit(false);
        try{
            boolean first=eligible(link);
            try(PreparedStatement statement=db.prepareStatement("INSERT OR IGNORE INTO seen_identities VALUES(?,?)")){
                statement.setString(1,"minecraft");statement.setString(2,link.uuid().toString());statement.executeUpdate();
                statement.setString(1,"discord");statement.setString(2,link.discord());statement.executeUpdate();
            }
            if(first&&reward)try(PreparedStatement statement=db.prepareStatement("INSERT INTO link_receipts VALUES(?,?,?,?,'CLAIMED')")){
                statement.setString(1,link.uuid().toString());statement.setString(2,link.discord());statement.setString(3,link.version());statement.setLong(4,link.linkedAt().toEpochMilli());statement.executeUpdate();
            }
            db.commit();return first&&reward;
        }catch(SQLException error){writable=false;try{db.rollback();}catch(SQLException suppressed){error.addSuppressed(suppressed);}throw error;}
        finally{try{db.setAutoCommit(true);}catch(SQLException error){writable=false;throw error;}}
    }
    public synchronized void finish(Store.Link link,boolean dispatched)throws SQLException{
        if(!writable)throw new SQLException("Link reward mutations unavailable");
        try(PreparedStatement statement=db.prepareStatement("UPDATE link_receipts SET status=? WHERE uuid=? AND discord=? AND link_version=? AND status='CLAIMED'")){
            statement.setString(1,dispatched?"DISPATCHED":"UNKNOWN");statement.setString(2,link.uuid().toString());statement.setString(3,link.discord());statement.setString(4,link.version());statement.executeUpdate();
        }catch(SQLException error){writable=false;throw error;}
    }
    public synchronized String status(UUID uuid)throws SQLException{
        try(PreparedStatement statement=db.prepareStatement("SELECT status FROM link_receipts WHERE uuid=?")){statement.setString(1,uuid.toString());try(ResultSet result=statement.executeQuery()){return result.next()?result.getString(1):"NONE";}}
    }
    @Override public synchronized void close()throws SQLException{writable=false;db.close();}
}
