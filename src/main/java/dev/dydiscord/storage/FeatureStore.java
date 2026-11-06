package dev.dydiscord.storage;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * 1.4.0 data in its own file: open support tickets, staff replies waiting for an offline player, and the
 * LuckPerms groups DyDiscord itself granted from Discord roles (so groups given by hand are never removed).
 * The file is only created when one of those features is used; an unknown or newer schema is refused unchanged.
 */
public final class FeatureStore implements AutoCloseable {
    public record Ticket(UUID uuid,String name,String thread,Instant opened) {}
    public record Reply(String author,String message) {}
    /** Replies kept per offline player; older ones are dropped first. */
    static final int MAX_REPLIES=20;
    private static final Set<String> TABLES=Set.of("tickets","ticket_replies","group_grants");
    private final Path file;private Connection db;
    public FeatureStore(Path file){this.file=Objects.requireNonNull(file);}
    private Connection db()throws SQLException{
        if(db!=null)return db;
        try{Files.createDirectories(file.toAbsolutePath().getParent());}catch(java.io.IOException failure){throw new SQLException("Feature data folder unavailable",failure);}
        Connection candidate=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
        try(Statement s=candidate.createStatement()){
            s.execute("PRAGMA busy_timeout=3000");
            int version;try(ResultSet r=s.executeQuery("PRAGMA user_version")){version=r.next()?r.getInt(1):-1;}
            Set<String> tables=new HashSet<>();try(ResultSet r=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")){while(r.next())tables.add(r.getString(1));}
            if(tables.isEmpty()&&version==0){
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE tickets(uuid TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,thread TEXT UNIQUE NOT NULL,opened INTEGER NOT NULL)");
                s.execute("CREATE TABLE ticket_replies(id INTEGER PRIMARY KEY AUTOINCREMENT,uuid TEXT NOT NULL,author TEXT NOT NULL,message TEXT NOT NULL)");
                s.execute("CREATE INDEX ticket_replies_uuid ON ticket_replies(uuid)");
                s.execute("CREATE TABLE group_grants(uuid TEXT NOT NULL,grp TEXT NOT NULL,PRIMARY KEY(uuid,grp))");
                s.execute("PRAGMA user_version=1");
            }else if(version!=1||!tables.equals(TABLES))throw new SQLException("Unsupported feature data schema");
        }catch(SQLException failure){candidate.close();throw failure;}
        return db=candidate;
    }
    public synchronized Optional<Ticket> ticket(UUID uuid)throws SQLException{return ticket("uuid",uuid.toString());}
    public synchronized Optional<Ticket> ticketByThread(String thread)throws SQLException{return ticket("thread",thread);}
    private Optional<Ticket> ticket(String column,String value)throws SQLException{
        try(PreparedStatement s=db().prepareStatement("SELECT uuid,name,thread,opened FROM tickets WHERE "+column+"=?")){s.setString(1,value);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(new Ticket(UUID.fromString(r.getString(1)),r.getString(2),r.getString(3),Instant.ofEpochMilli(r.getLong(4)))):Optional.empty();}}
    }
    /** Records a new ticket, replacing the player's previous one. */
    public synchronized void open(Ticket ticket)throws SQLException{
        try(PreparedStatement s=db().prepareStatement("INSERT INTO tickets VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,thread=excluded.thread,opened=excluded.opened")){s.setString(1,ticket.uuid().toString());s.setString(2,ticket.name());s.setString(3,ticket.thread());s.setLong(4,ticket.opened().toEpochMilli());s.executeUpdate();}
    }
    public synchronized boolean close(UUID uuid)throws SQLException{try(PreparedStatement s=db().prepareStatement("DELETE FROM tickets WHERE uuid=?")){s.setString(1,uuid.toString());return s.executeUpdate()>0;}}
    public synchronized void queueReply(UUID uuid,String author,String message)throws SQLException{
        Connection c=db();
        try(PreparedStatement s=c.prepareStatement("INSERT INTO ticket_replies(uuid,author,message) VALUES(?,?,?)")){s.setString(1,uuid.toString());s.setString(2,author);s.setString(3,message);s.executeUpdate();}
        try(PreparedStatement s=c.prepareStatement("DELETE FROM ticket_replies WHERE uuid=? AND id NOT IN (SELECT id FROM ticket_replies WHERE uuid=? ORDER BY id DESC LIMIT "+MAX_REPLIES+")")){s.setString(1,uuid.toString());s.setString(2,uuid.toString());s.executeUpdate();}
    }
    /** Returns and forgets the replies waiting for a player, oldest first. */
    public synchronized List<Reply> takeReplies(UUID uuid)throws SQLException{
        if(db==null&&!Files.exists(file))return List.of();
        List<Reply> replies=new ArrayList<>();Connection c=db();
        try(PreparedStatement s=c.prepareStatement("SELECT author,message FROM ticket_replies WHERE uuid=? ORDER BY id")){s.setString(1,uuid.toString());try(ResultSet r=s.executeQuery()){while(r.next())replies.add(new Reply(r.getString(1),r.getString(2)));}}
        if(!replies.isEmpty())try(PreparedStatement s=c.prepareStatement("DELETE FROM ticket_replies WHERE uuid=?")){s.setString(1,uuid.toString());s.executeUpdate();}
        return replies;
    }
    public synchronized Set<String> grants(UUID uuid)throws SQLException{
        if(db==null&&!Files.exists(file))return Set.of();
        Set<String> groups=new TreeSet<>();try(PreparedStatement s=db().prepareStatement("SELECT grp FROM group_grants WHERE uuid=?")){s.setString(1,uuid.toString());try(ResultSet r=s.executeQuery()){while(r.next())groups.add(r.getString(1));}}return groups;
    }
    public synchronized void grant(UUID uuid,String group)throws SQLException{try(PreparedStatement s=db().prepareStatement("INSERT OR IGNORE INTO group_grants VALUES(?,?)")){s.setString(1,uuid.toString());s.setString(2,group);s.executeUpdate();}}
    public synchronized void revoke(UUID uuid,String group)throws SQLException{try(PreparedStatement s=db().prepareStatement("DELETE FROM group_grants WHERE uuid=? AND grp=?")){s.setString(1,uuid.toString());s.setString(2,group);s.executeUpdate();}}
    @Override public synchronized void close()throws SQLException{if(db!=null){db.close();db=null;}}
}
