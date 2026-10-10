package dev.dydiscord.storage;

import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Access serialized by the I/O executor and synchronized domain transactions. Never resets damaged data. */
public final class Store implements AutoCloseable {
    public record Link(UUID uuid,String discord,String name,Instant linkedAt,String version,String discordName) {
        public Link(UUID uuid,String discord,String name,Instant linkedAt,String version){this(uuid,discord,name,linkedAt,version,"");}
        public Link {discordName=discordName==null?"":discordName;}
    }
    public record Panel(String guild,String channel,String message) {}
    public static final class Revoked extends SQLException {public Revoked(){super("Owner context revoked before commit");}}
    private final Connection db;
    private volatile boolean writable=true;
    public Store(Path file)throws Exception {
        Files.createDirectories(file.getParent()); Class.forName("org.sqlite.JDBC");
        db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
        try(Statement s=db.createStatement()){
            s.execute("PRAGMA busy_timeout=3000");s.execute("PRAGMA foreign_keys=ON");
            try(ResultSet r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new SQLException("Database integrity check failed");}
            s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            int schema=0;
            try(ResultSet r=s.executeQuery("SELECT version FROM schema_version")){if(r.next()){schema=r.getInt(1);if((schema!=1&&schema!=2)||r.next())throw new SQLException("Unsupported database schema");}}
            s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");
            db.setAutoCommit(false);
            s.execute("CREATE TABLE IF NOT EXISTS links(uuid TEXT PRIMARY KEY, discord TEXT NOT NULL UNIQUE, name TEXT NOT NULL, linked_at TEXT NOT NULL, version TEXT NOT NULL UNIQUE, discord_name TEXT NOT NULL DEFAULT '')");
            Set<String> columns=new HashSet<>();try(ResultSet r=s.executeQuery("PRAGMA table_info(links)")){while(r.next())columns.add(r.getString("name"));}
            if(!columns.containsAll(Set.of("uuid","discord","name","linked_at","version")))throw new SQLException("Invalid database schema");
            if(!columns.contains("discord_name")){if(schema!=1)throw new SQLException("Invalid database schema");s.execute("ALTER TABLE links ADD COLUMN discord_name TEXT NOT NULL DEFAULT ''");}
            if(schema==0)s.execute("INSERT INTO schema_version VALUES(2)");else if(schema==1)s.execute("UPDATE schema_version SET version=2");
            s.execute("CREATE TABLE IF NOT EXISTS player_preferences(uuid TEXT PRIMARY KEY, language TEXT NOT NULL CHECK(language IN ('en','tr','auto')))");
            s.execute("CREATE TABLE IF NOT EXISTS panel_state(id INTEGER PRIMARY KEY CHECK(id=1), guild TEXT NOT NULL, channel TEXT NOT NULL, message TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS console_receipts(message TEXT PRIMARY KEY, accepted_at TEXT NOT NULL, result TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS managed_commands(id TEXT PRIMARY KEY, guild TEXT NOT NULL)");
            // Accepted but unfinished commands are never replayed after restart.
            s.execute("UPDATE console_receipts SET result='unknown' WHERE result='accepted'");
            db.commit();db.setAutoCommit(true);
        } catch(Exception e){try{if(!db.getAutoCommit())db.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}db.close();throw e;}
    }
    public boolean writable(){return writable;}
    private void requireWrite()throws SQLException{if(!writable)throw new SQLException("Data mutations unavailable");}
    public synchronized Optional<Link> byUuid(UUID uuid)throws SQLException{return find("uuid",uuid.toString());}
    public synchronized Optional<Link> byDiscord(String id)throws SQLException{return find("discord",id);}
    private Optional<Link> find(String column,String value)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT * FROM links WHERE "+column+"=?")){s.setString(1,value);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(new Link(UUID.fromString(r.getString("uuid")),r.getString("discord"),r.getString("name"),Instant.parse(r.getString("linked_at")),r.getString("version"),r.getString("discord_name"))):Optional.empty();}}
    }
    public synchronized Link link(UUID uuid,String discord,String name,Instant now)throws SQLException{
        return link(uuid,discord,name,now,()->true);
    }
    public synchronized Link link(UUID uuid,String discord,String name,Instant now,java.util.function.BooleanSupplier valid)throws SQLException{
        return link(uuid,discord,name,"",now,valid);
    }
    public synchronized Link link(UUID uuid,String discord,String name,String discordName,Instant now)throws SQLException{
        return link(uuid,discord,name,discordName,now,()->true);
    }
    public synchronized Link link(UUID uuid,String discord,String name,String discordName,Instant now,java.util.function.BooleanSupplier valid)throws SQLException{
        requireWrite();discordName=checkedDiscordName(discordName);Link link=new Link(uuid,discord,name,now,UUID.randomUUID().toString(),discordName);
        db.setAutoCommit(false);
        try(PreparedStatement s=db.prepareStatement("INSERT INTO links(uuid,discord,name,linked_at,version,discord_name) VALUES(?,?,?,?,?,?)")){s.setString(1,uuid.toString());s.setString(2,discord);s.setString(3,name);s.setString(4,now.toString());s.setString(5,link.version);s.setString(6,discordName);s.executeUpdate();if(!valid.getAsBoolean())throw new Revoked();db.commit();return link;}
        catch(SQLException|RuntimeException e){try{db.rollback();}catch(SQLException rollback){writable=false;e.addSuppressed(rollback);}if(e instanceof SQLException sql&&!(sql instanceof Revoked)&&sql.getErrorCode()!=19)writable=false;throw e;}
        finally{try{db.setAutoCommit(true);}catch(SQLException e){writable=false;throw e;}}
    }
    public synchronized boolean unlink(Link expected)throws SQLException{
        requireWrite();try(PreparedStatement s=db.prepareStatement("DELETE FROM links WHERE uuid=? AND version=?")){s.setString(1,expected.uuid.toString());s.setString(2,expected.version);return s.executeUpdate()==1;}catch(SQLException e){writable=false;throw e;}
    }
    public synchronized Map<UUID,String> preferences()throws SQLException{
        Map<UUID,String> map=new HashMap<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT * FROM player_preferences")){while(r.next())map.put(UUID.fromString(r.getString(1)),r.getString(2));}return map;
    }
    public synchronized void preference(UUID uuid,String language)throws SQLException{
        requireWrite();try(PreparedStatement s=db.prepareStatement("INSERT INTO player_preferences VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET language=excluded.language")){s.setString(1,uuid.toString());s.setString(2,language);s.executeUpdate();}catch(SQLException e){writable=false;throw e;}
    }
    public synchronized Optional<Panel> panel()throws SQLException{
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT guild,channel,message FROM panel_state WHERE id=1")){return r.next()?Optional.of(new Panel(r.getString(1),r.getString(2),r.getString(3))):Optional.empty();}
    }
    public synchronized void panel(Panel panel)throws SQLException{
        requireWrite();try(PreparedStatement s=db.prepareStatement("INSERT INTO panel_state VALUES(1,?,?,?) ON CONFLICT(id) DO UPDATE SET guild=excluded.guild,channel=excluded.channel,message=excluded.message")){s.setString(1,panel.guild);s.setString(2,panel.channel);s.setString(3,panel.message);s.executeUpdate();}catch(SQLException e){writable=false;throw e;}
    }
    public synchronized boolean accept(String id,Instant now)throws SQLException{
        requireWrite();try(PreparedStatement clean=db.prepareStatement("DELETE FROM console_receipts WHERE accepted_at<?")){clean.setString(1,now.minus(Duration.ofDays(30)).toString());clean.executeUpdate();}
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM console_receipts")){if(r.next()&&r.getInt(1)>=20000)throw new SQLException("Receipt capacity reached");}
        try(PreparedStatement s=db.prepareStatement("INSERT OR IGNORE INTO console_receipts VALUES(?,?,'accepted')")){s.setString(1,id);s.setString(2,now.toString());return s.executeUpdate()==1;}
    }
    public synchronized void result(String id,String result)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("UPDATE console_receipts SET result=? WHERE message=?")){s.setString(1,result);s.setString(2,id);s.executeUpdate();}
    }
    public synchronized void updateName(UUID uuid,String name)throws SQLException{
        requireWrite();try(PreparedStatement s=db.prepareStatement("UPDATE links SET name=? WHERE uuid=?")){s.setString(1,name);s.setString(2,uuid.toString());s.executeUpdate();}
    }
    public synchronized Optional<Link> updateDiscordName(Link expected,String discordName)throws SQLException{
        requireWrite();discordName=checkedDiscordName(discordName);
        try(PreparedStatement s=db.prepareStatement("UPDATE links SET discord_name=? WHERE uuid=? AND discord=? AND version=?")){
            s.setString(1,discordName);s.setString(2,expected.uuid.toString());s.setString(3,expected.discord);s.setString(4,expected.version);
            if(s.executeUpdate()!=1)return Optional.empty();
            return byUuid(expected.uuid).filter(current->current.discord.equals(expected.discord)&&current.version.equals(expected.version));
        }catch(SQLException e){writable=false;throw e;}
    }
    private static String checkedDiscordName(String name){if(name==null)return "";if(name.length()>100||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid Discord username");return name;}
    public synchronized Set<String> commands(String guild)throws SQLException{Set<String> ids=new HashSet<>();try(PreparedStatement s=db.prepareStatement("SELECT id FROM managed_commands WHERE guild=?")){s.setString(1,guild);try(ResultSet r=s.executeQuery()){while(r.next())ids.add(r.getString(1));}}return ids;}
    public synchronized void command(String guild,String id)throws SQLException{requireWrite();try(PreparedStatement s=db.prepareStatement("INSERT OR IGNORE INTO managed_commands VALUES(?,?)")){s.setString(1,id);s.setString(2,guild);s.executeUpdate();}}
    public synchronized void forgetCommand(String id)throws SQLException{requireWrite();try(PreparedStatement s=db.prepareStatement("DELETE FROM managed_commands WHERE id=?")){s.setString(1,id);s.executeUpdate();}}
    @Override public synchronized void close()throws SQLException{db.close();}
}
