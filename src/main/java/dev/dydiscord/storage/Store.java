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
    public record LinkSnapshot(Optional<Link> link,long generation) {}
    public record RoleTarget(String guild,String discord,long revision,Instant due,String status,boolean cleanup,long batch) {}
    public record RoleSweep(String guild,boolean cleanup,String linkCursor,String targetCursor,int phase,long batch) {}
    public static final class Revoked extends SQLException {public Revoked(){super("Owner context revoked before commit");}}
    private final Connection db;
    private final java.util.concurrent.atomic.AtomicLong linkGeneration=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong rewardSequence=new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.ConcurrentHashMap<String,Long> rewardGenerations=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile java.util.function.Consumer<String> linkListener=id->{};
    public long linkGeneration(){return linkGeneration.get();}
    public synchronized LinkSnapshot snapshotByDiscord(String id)throws SQLException{return new LinkSnapshot(byDiscord(id),linkGeneration.get());}
    public void linkListener(java.util.function.Consumer<String> listener){linkListener=Objects.requireNonNull(listener);}
    private volatile java.util.function.BiConsumer<Link,Boolean> accountListener=(link,linked)->{};
    /** Called after a link is committed (true) or removed (false), on the thread that changed it. */
    public void accountListener(java.util.function.BiConsumer<Link,Boolean> listener){accountListener=Objects.requireNonNull(listener);}
    private void announce(Link link,boolean linked){try{accountListener.accept(link,linked);}catch(RuntimeException ignored){}}
    public long rewardGeneration(String discord){return rewardGenerations.getOrDefault(discord,-1L);}
    public synchronized LinkSnapshot rewardSnapshot(String discord)throws SQLException{
        Optional<Link> link=byDiscord(discord);
        if(!rewardGenerations.containsKey(discord)){if(rewardGenerations.size()>=256)rewardGenerations.remove(rewardGenerations.keys().nextElement());rewardGenerations.put(discord,rewardSequence.incrementAndGet());}
        return new LinkSnapshot(link,rewardGeneration(discord));
    }
    private void linkChanged(String discord){linkGeneration.incrementAndGet();rewardGenerations.computeIfPresent(discord,(id,old)->rewardSequence.incrementAndGet());linkListener.accept(discord);}
    private volatile boolean writable=true;
    public Store(Path file)throws Exception {
        Files.createDirectories(file.getParent()); Class.forName("org.sqlite.JDBC");
        db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
        try(Statement s=db.createStatement()){
            s.execute("PRAGMA busy_timeout=3000");s.execute("PRAGMA foreign_keys=ON");
            try(ResultSet r=s.executeQuery("PRAGMA quick_check")){if(!r.next()||!r.getString(1).equals("ok"))throw new SQLException("Database integrity check failed");}
            s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            int schema=0;
            try(ResultSet r=s.executeQuery("SELECT version FROM schema_version")){if(r.next()){schema=r.getInt(1);if((schema<1||schema>3)||r.next())throw new SQLException("Unsupported database schema");}}
            if(schema>0&&schema<3){
                Path backup=file.toAbsolutePath().resolveSibling(file.getFileName()+".backup-schema"+schema+"-"+UUID.randomUUID());
                try(PreparedStatement copy=db.prepareStatement("VACUUM INTO ?")){copy.setString(1,backup.toString());copy.execute();}
            }
            if(schema==3){
                boolean needsRewardBackup;
                try(ResultSet rows=s.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('link_reward_seen','link_reward_origins')")){
                    needsRewardBackup=rows.next()&&rows.getInt(1)==0;
                }
                if(needsRewardBackup){
                    Path backup=file.toAbsolutePath().resolveSibling(file.getFileName()+".backup-reward-observations-"+UUID.randomUUID());
                    // Snapshot committed WAL data before the additive migration mutates anything.
                    // A failed backup aborts initialization without creating observation tables.
                    try(PreparedStatement copy=db.prepareStatement("VACUUM INTO ?")){copy.setString(1,backup.toString());copy.execute();}
                }
            }
            s.execute("PRAGMA journal_mode=WAL");s.execute("PRAGMA synchronous=FULL");
            db.setAutoCommit(false);
            s.execute("CREATE TABLE IF NOT EXISTS links(uuid TEXT PRIMARY KEY, discord TEXT NOT NULL UNIQUE, name TEXT NOT NULL, linked_at TEXT NOT NULL, version TEXT NOT NULL UNIQUE, discord_name TEXT NOT NULL DEFAULT '')");
            Set<String> columns=new HashSet<>();try(ResultSet r=s.executeQuery("PRAGMA table_info(links)")){while(r.next())columns.add(r.getString("name"));}
            if(!columns.containsAll(Set.of("uuid","discord","name","linked_at","version")))throw new SQLException("Invalid database schema");
            if(!columns.contains("discord_name")){if(schema!=1)throw new SQLException("Invalid database schema");s.execute("ALTER TABLE links ADD COLUMN discord_name TEXT NOT NULL DEFAULT ''");}
            if(schema==0)s.execute("INSERT INTO schema_version VALUES(3)");else if(schema<3)s.execute("UPDATE schema_version SET version=3");
            s.execute("CREATE TABLE IF NOT EXISTS player_preferences(uuid TEXT PRIMARY KEY, language TEXT NOT NULL CHECK(language IN ('en','tr','auto')))");
            s.execute("CREATE TABLE IF NOT EXISTS status_card(id INTEGER PRIMARY KEY CHECK(id=1), channel TEXT NOT NULL, message TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS player_languages(uuid TEXT PRIMARY KEY, language TEXT NOT NULL CHECK(language IN ('de','es','ru')))");
            s.execute("CREATE TABLE IF NOT EXISTS panel_state(id INTEGER PRIMARY KEY CHECK(id=1), guild TEXT NOT NULL, channel TEXT NOT NULL, message TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS console_receipts(message TEXT PRIMARY KEY, accepted_at TEXT NOT NULL, result TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS managed_commands(id TEXT PRIMARY KEY, guild TEXT NOT NULL)");
            if(schema==3){
                Set<String> cols=new HashSet<>();try(ResultSet r=s.executeQuery("PRAGMA table_info(role_targets)")){while(r.next())cols.add(r.getString("name"));}
                if(!cols.containsAll(Set.of("guild","discord","revision","dirty","due","status","cleanup","batch")))throw new SQLException("Invalid role schema");
                for(String table:List.of("role_history","role_sweeps","role_cleanup_roles")){
                    Set<String> found=new HashSet<>();try(ResultSet r=s.executeQuery("PRAGMA table_info("+table+")")){while(r.next())found.add(r.getString("name"));}
                    Set<String> required=table.equals("role_sweeps")?Set.of("guild","cleanup","link_cursor","target_cursor","phase","batch"):Set.of("guild","role");
                    if(!found.containsAll(required))throw new SQLException("Invalid role schema");
                }
            }
            s.execute("CREATE TABLE IF NOT EXISTS role_history(guild TEXT NOT NULL,role TEXT NOT NULL,PRIMARY KEY(guild,role))");
            s.execute("CREATE TABLE IF NOT EXISTS role_targets(guild TEXT NOT NULL,discord TEXT NOT NULL,revision INTEGER NOT NULL,dirty INTEGER NOT NULL,due INTEGER NOT NULL,status TEXT NOT NULL,cleanup INTEGER NOT NULL,batch INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(guild,discord))");
            s.execute("CREATE INDEX IF NOT EXISTS role_due ON role_targets(dirty,due)");
            s.execute("CREATE TABLE IF NOT EXISTS role_sweeps(guild TEXT PRIMARY KEY,cleanup INTEGER NOT NULL,link_cursor TEXT NOT NULL,target_cursor TEXT NOT NULL,phase INTEGER NOT NULL,batch INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS role_cleanup_roles(guild TEXT NOT NULL,role TEXT NOT NULL,PRIMARY KEY(guild,role))");
            // First-link facts remain available even when the optional reward ledger is down.
            initializeLinkRewardFacts(s);
            // Accepted but unfinished commands are never replayed after restart.
            s.execute("UPDATE console_receipts SET result='unknown' WHERE result='accepted'");
            db.commit();db.setAutoCommit(true);
        } catch(Exception e){try{if(!db.getAutoCommit())db.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}db.close();throw e;}
    }
    private static void initializeLinkRewardFacts(Statement statement)throws SQLException{
        Set<String> existing=new HashSet<>();
        try(ResultSet rows=statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('link_reward_seen','link_reward_origins')")){while(rows.next())existing.add(rows.getString(1));}
        if(existing.size()==1)throw new SQLException("Incomplete first-link observation schema");
        statement.execute("CREATE TABLE IF NOT EXISTS link_reward_seen(kind TEXT NOT NULL CHECK(kind IN ('minecraft','discord')),identity TEXT NOT NULL,PRIMARY KEY(kind,identity))");
        statement.execute("CREATE TABLE IF NOT EXISTS link_reward_origins(version TEXT PRIMARY KEY NOT NULL,uuid TEXT NOT NULL,discord TEXT NOT NULL)");
        for(String table:List.of("link_reward_seen","link_reward_origins")){
            Set<String> columns=new HashSet<>();Map<String,Integer> key=new HashMap<>();
            try(ResultSet rows=statement.executeQuery("PRAGMA table_info("+table+")")){while(rows.next()){columns.add(rows.getString("name"));if(rows.getInt("pk")>0)key.put(rows.getString("name"),rows.getInt("pk"));}}
            Map<String,Integer> expected=table.equals("link_reward_seen")?Map.of("kind",1,"identity",2):Map.of("version",1);
            Set<String> required=table.equals("link_reward_seen")?Set.of("kind","identity"):Set.of("version","uuid","discord");
            if(!key.equals(expected)||!columns.containsAll(required))throw new SQLException("Invalid first-link observation schema");
        }
        // Existing accounts are baseline observations, never new reward origins.
        statement.execute("INSERT OR IGNORE INTO link_reward_seen SELECT 'minecraft',uuid FROM links");
        statement.execute("INSERT OR IGNORE INTO link_reward_seen SELECT 'discord',discord FROM links");
    }
    public synchronized boolean firstRewardEligible(Link link)throws SQLException{
        try(PreparedStatement statement=db.prepareStatement("SELECT 1 FROM link_reward_origins WHERE version=? AND uuid=? AND discord=?")){
            statement.setString(1,link.version());statement.setString(2,link.uuid().toString());statement.setString(3,link.discord());
            try(ResultSet rows=statement.executeQuery()){return rows.next();}
        }
    }
    private void observeRewardOrigin(Link link)throws SQLException{
        int minecraft,discord;
        try(PreparedStatement statement=db.prepareStatement("INSERT OR IGNORE INTO link_reward_seen VALUES(?,?)")){
            statement.setString(1,"minecraft");statement.setString(2,link.uuid().toString());minecraft=statement.executeUpdate();
            statement.setString(1,"discord");statement.setString(2,link.discord());discord=statement.executeUpdate();
        }
        if(minecraft==1&&discord==1)try(PreparedStatement statement=db.prepareStatement("INSERT INTO link_reward_origins VALUES(?,?,?)")){
            statement.setString(1,link.version());statement.setString(2,link.uuid().toString());statement.setString(3,link.discord());statement.executeUpdate();
        }
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
        try(PreparedStatement s=db.prepareStatement("INSERT INTO links(uuid,discord,name,linked_at,version,discord_name) VALUES(?,?,?,?,?,?)")){s.setString(1,uuid.toString());s.setString(2,discord);s.setString(3,name);s.setString(4,now.toString());s.setString(5,link.version);s.setString(6,discordName);s.executeUpdate();observeRewardOrigin(link);try(PreparedStatement dirty=db.prepareStatement("UPDATE role_targets SET revision=revision+1,dirty=1,due=0,status='QUEUED' WHERE discord=?")){dirty.setString(1,discord);dirty.executeUpdate();}if(!valid.getAsBoolean())throw new Revoked();db.commit();linkChanged(discord);announce(link,true);return link;}
        catch(SQLException|RuntimeException e){try{db.rollback();}catch(SQLException rollback){writable=false;e.addSuppressed(rollback);}if(e instanceof SQLException sql&&!(sql instanceof Revoked)&&sql.getErrorCode()!=19)writable=false;throw e;}
        finally{try{db.setAutoCommit(true);}catch(SQLException e){writable=false;throw e;}}
    }
    public synchronized boolean unlink(Link expected)throws SQLException{
        requireWrite();db.setAutoCommit(false);
        try(PreparedStatement s=db.prepareStatement("DELETE FROM links WHERE uuid=? AND version=?")){
            s.setString(1,expected.uuid.toString());s.setString(2,expected.version);boolean removed=s.executeUpdate()==1;
            if(removed)try(PreparedStatement dirty=db.prepareStatement("UPDATE role_targets SET revision=revision+1,dirty=1,due=0,status='QUEUED' WHERE discord=?")){dirty.setString(1,expected.discord());dirty.executeUpdate();}
            db.commit();if(removed){linkChanged(expected.discord());announce(expected,false);}return removed;
        }catch(SQLException e){try{db.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}writable=false;throw e;}
        finally{db.setAutoCommit(true);}
    }
    public synchronized Map<UUID,String> preferences()throws SQLException{
        Map<UUID,String> map=new HashMap<>();try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT * FROM player_preferences")){while(r.next())map.put(UUID.fromString(r.getString(1)),r.getString(2));}
        try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT uuid,language FROM player_languages")){while(r.next())map.put(UUID.fromString(r.getString(1)),r.getString(2));}return map;
    }
    /** English, Turkish and auto stay in the 1.0 table; newer languages live beside it, so an older JAR reads them as auto. */
    public synchronized void preference(UUID uuid,String language)throws SQLException{
        requireWrite();boolean legacy=Set.of("en","tr","auto").contains(language);
        try{
            db.setAutoCommit(false);
            try(PreparedStatement s=db.prepareStatement("INSERT INTO player_preferences VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET language=excluded.language")){s.setString(1,uuid.toString());s.setString(2,legacy?language:"auto");s.executeUpdate();}
            if(legacy)try(PreparedStatement s=db.prepareStatement("DELETE FROM player_languages WHERE uuid=?")){s.setString(1,uuid.toString());s.executeUpdate();}
            else try(PreparedStatement s=db.prepareStatement("INSERT INTO player_languages VALUES(?,?) ON CONFLICT(uuid) DO UPDATE SET language=excluded.language")){s.setString(1,uuid.toString());s.setString(2,language);s.executeUpdate();}
            db.commit();
        }catch(SQLException e){try{db.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}writable=false;throw e;}
        finally{db.setAutoCommit(true);}
    }
    /** The live status card message, as channel and message ID. */
    public synchronized Optional<String[]> statusCard()throws SQLException{try(Statement s=db.createStatement();ResultSet r=s.executeQuery("SELECT channel,message FROM status_card WHERE id=1")){return r.next()?Optional.of(new String[]{r.getString(1),r.getString(2)}):Optional.empty();}}
    public synchronized void statusCard(String channel,String message)throws SQLException{requireWrite();try(PreparedStatement s=db.prepareStatement("INSERT INTO status_card VALUES(1,?,?) ON CONFLICT(id) DO UPDATE SET channel=excluded.channel,message=excluded.message")){s.setString(1,channel);s.setString(2,message);s.executeUpdate();}}
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
        requireWrite();Optional<Link> previous=byUuid(uuid);try(PreparedStatement s=db.prepareStatement("UPDATE links SET name=? WHERE uuid=? AND name<>?")){s.setString(1,name);s.setString(2,uuid.toString());s.setString(3,name);if(s.executeUpdate()>0&&previous.isPresent())linkChanged(previous.get().discord());}
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
    public synchronized List<Link> linksPage(String cursor,int limit)throws SQLException{
        if(limit<1||limit>100)throw new IllegalArgumentException();List<Link> out=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("SELECT * FROM links WHERE uuid>? ORDER BY uuid LIMIT ?")){
            s.setString(1,cursor);s.setInt(2,limit);try(ResultSet r=s.executeQuery()){while(r.next())out.add(new Link(UUID.fromString(r.getString("uuid")),r.getString("discord"),r.getString("name"),Instant.parse(r.getString("linked_at")),r.getString("version"),r.getString("discord_name")));}
        }return List.copyOf(out);
    }
    public synchronized Set<String> roleHistory(String guild)throws SQLException{
        Set<String> out=new HashSet<>();try(PreparedStatement s=db.prepareStatement("SELECT role FROM role_history WHERE guild=?")){s.setString(1,guild);try(ResultSet r=s.executeQuery()){while(r.next())out.add(r.getString(1));}}return Set.copyOf(out);
    }
    public synchronized void roleRemember(String guild,Set<String> ids)throws SQLException{
        requireWrite();Set<String> all=new HashSet<>(roleHistory(guild));all.addAll(ids);if(all.size()>2048)throw new SQLException("Role history capacity reached");
        db.setAutoCommit(false);try(PreparedStatement s=db.prepareStatement("INSERT OR IGNORE INTO role_history VALUES(?,?)")){
            for(String id:ids){s.setString(1,guild);s.setString(2,id);s.addBatch();}s.executeBatch();db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public synchronized void roleQueue(String guild,String discord,Instant now,boolean cleanup,long batch)throws SQLException{
        requireWrite();try(PreparedStatement s=db.prepareStatement("INSERT INTO role_targets VALUES(?,?,1,1,?,'QUEUED',?,?) ON CONFLICT(guild,discord) DO UPDATE SET revision=revision+1,dirty=1,due=excluded.due,status='QUEUED',cleanup=excluded.cleanup,batch=CASE WHEN excluded.batch>0 THEN excluded.batch ELSE role_targets.batch END")){
            s.setString(1,guild);s.setString(2,discord);s.setLong(3,now.toEpochMilli());s.setInt(4,cleanup?1:0);s.setLong(5,batch);s.executeUpdate();
        }
    }
    public synchronized Optional<RoleTarget> roleNext(Instant now,boolean enabled)throws SQLException{
        return roleNext(now,enabled,"");
    }
    public synchronized Optional<RoleTarget> roleNext(Instant now,boolean enabled,String guild)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT * FROM role_targets WHERE dirty=1 AND due<=? AND (?=1 OR cleanup=1) AND (?='' OR guild=?) ORDER BY due,guild,discord LIMIT 1")){
            s.setLong(1,now.toEpochMilli());s.setInt(2,enabled?1:0);s.setString(3,guild);s.setString(4,guild);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(roleTarget(r)):Optional.empty();}
        }
    }
    private static RoleTarget roleTarget(ResultSet r)throws SQLException{return new RoleTarget(r.getString("guild"),r.getString("discord"),r.getLong("revision"),Instant.ofEpochMilli(r.getLong("due")),r.getString("status"),r.getInt("cleanup")==1,r.getLong("batch"));}
    public synchronized boolean roleCurrent(RoleTarget target)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT revision,dirty,cleanup FROM role_targets WHERE guild=? AND discord=?")){
            s.setString(1,target.guild());s.setString(2,target.discord());try(ResultSet r=s.executeQuery()){return r.next()&&r.getLong(1)==target.revision()&&r.getInt(2)==1&&(r.getInt(3)==1)==target.cleanup();}
        }
    }
    public synchronized boolean roleFinish(RoleTarget target,String status,Instant due,boolean dirty)throws SQLException{
        requireWrite();if(!Set.of("SUCCESS","PENDING","FAILED","ABSENT").contains(status))throw new IllegalArgumentException();
        try(PreparedStatement s=db.prepareStatement("UPDATE role_targets SET status=?,due=?,dirty=? WHERE guild=? AND discord=? AND revision=?")){
            s.setString(1,status);s.setLong(2,due.toEpochMilli());s.setInt(3,dirty?1:0);s.setString(4,target.guild());s.setString(5,target.discord());s.setLong(6,target.revision());return s.executeUpdate()==1;
        }
    }
    public synchronized List<RoleTarget> roleTargetsPage(String guild,String cursor,int limit)throws SQLException{
        if(limit<1||limit>100)throw new IllegalArgumentException();List<RoleTarget> out=new ArrayList<>();
        try(PreparedStatement s=db.prepareStatement("SELECT * FROM role_targets WHERE guild=? AND discord>? ORDER BY discord LIMIT ?")){s.setString(1,guild);s.setString(2,cursor);s.setInt(3,limit);try(ResultSet r=s.executeQuery()){while(r.next())out.add(roleTarget(r));}}return List.copyOf(out);
    }
    public synchronized Map<String,Long> roleCounts(String guild,long batch)throws SQLException{
        Map<String,Long> counts=new HashMap<>();try(PreparedStatement s=db.prepareStatement("SELECT status,count(*) FROM role_targets WHERE guild=? AND (?=0 OR batch=?) GROUP BY status")){s.setString(1,guild);s.setLong(2,batch);s.setLong(3,batch);try(ResultSet r=s.executeQuery()){while(r.next())counts.put(r.getString(1),r.getLong(2));}}return Map.copyOf(counts);
    }
    public synchronized long roleCleanupCount(String guild)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT count(*) FROM (SELECT discord FROM links UNION SELECT discord FROM role_targets WHERE guild=?)")){s.setString(1,guild);try(ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):0;}}
    }
    public synchronized Optional<RoleSweep> roleSweep(String guild)throws SQLException{
        try(PreparedStatement s=db.prepareStatement("SELECT * FROM role_sweeps WHERE guild=?")){s.setString(1,guild);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(new RoleSweep(guild,r.getInt("cleanup")==1,r.getString("link_cursor"),r.getString("target_cursor"),r.getInt("phase"),r.getLong("batch"))):Optional.empty();}}
    }
    public synchronized Set<String> roleCleanupRoles(String guild)throws SQLException{
        Set<String> roles=new HashSet<>();try(PreparedStatement s=db.prepareStatement("SELECT role FROM role_cleanup_roles WHERE guild=?")){s.setString(1,guild);try(ResultSet r=s.executeQuery()){while(r.next())roles.add(r.getString(1));}}return Set.copyOf(roles);
    }
    public synchronized void roleSweepStart(String guild,boolean cleanup,long batch,Set<String> roles)throws SQLException{
        requireWrite();db.setAutoCommit(false);
        try(PreparedStatement s=db.prepareStatement("INSERT INTO role_sweeps VALUES(?,?,'','',0,?) ON CONFLICT(guild) DO UPDATE SET cleanup=excluded.cleanup,link_cursor='',target_cursor='',phase=0,batch=excluded.batch")){
            s.setString(1,guild);s.setInt(2,cleanup?1:0);s.setLong(3,batch);s.executeUpdate();
            try(PreparedStatement clear=db.prepareStatement("DELETE FROM role_cleanup_roles WHERE guild=?")){clear.setString(1,guild);clear.executeUpdate();}
            if(cleanup)try(PreparedStatement insert=db.prepareStatement("INSERT INTO role_cleanup_roles VALUES(?,?)")){for(String role:roles){insert.setString(1,guild);insert.setString(2,role);insert.addBatch();}insert.executeBatch();}
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public synchronized void roleSweepProgress(RoleSweep sweep)throws SQLException{
        requireWrite();try(PreparedStatement s=db.prepareStatement("UPDATE role_sweeps SET link_cursor=?,target_cursor=?,phase=? WHERE guild=? AND batch=? AND cleanup=?")){
            s.setString(1,sweep.linkCursor());s.setString(2,sweep.targetCursor());s.setInt(3,sweep.phase());s.setString(4,sweep.guild());s.setLong(5,sweep.batch());s.setInt(6,sweep.cleanup()?1:0);s.executeUpdate();
        }
    }
    public synchronized void roleSweepDone(String guild,boolean cleanup)throws SQLException{
        requireWrite();db.setAutoCommit(false);try{
            if(cleanup)roleForgetHistory(guild);
            try(PreparedStatement s=db.prepareStatement("DELETE FROM role_sweeps WHERE guild=?")){s.setString(1,guild);s.executeUpdate();}
            try(PreparedStatement s=db.prepareStatement("DELETE FROM role_cleanup_roles WHERE guild=?")){s.setString(1,guild);s.executeUpdate();}
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public synchronized void roleCancelCleanup(String guild)throws SQLException{
        requireWrite();db.setAutoCommit(false);try{
            try(PreparedStatement s=db.prepareStatement("UPDATE role_targets SET cleanup=0,dirty=1,revision=revision+1,due=0,status='QUEUED' WHERE guild=? AND cleanup=1")){s.setString(1,guild);s.executeUpdate();}
            try(PreparedStatement s=db.prepareStatement("DELETE FROM role_sweeps WHERE guild=? AND cleanup=1")){s.setString(1,guild);s.executeUpdate();}
            try(PreparedStatement s=db.prepareStatement("DELETE FROM role_cleanup_roles WHERE guild=?")){s.setString(1,guild);s.executeUpdate();}
            db.commit();
        }catch(SQLException e){db.rollback();throw e;}finally{db.setAutoCommit(true);}
    }
    public synchronized void roleForgetHistory(String guild)throws SQLException{
        requireWrite();try(PreparedStatement pending=db.prepareStatement("SELECT count(*) FROM role_targets WHERE guild=? AND (dirty=1 OR status NOT IN ('SUCCESS','ABSENT'))")){pending.setString(1,guild);try(ResultSet r=pending.executeQuery()){if(r.next()&&r.getLong(1)!=0)throw new SQLException("Role cleanup incomplete");}}
        try(PreparedStatement s=db.prepareStatement("DELETE FROM role_history WHERE guild=?")){s.setString(1,guild);s.executeUpdate();}
    }
    @Override public synchronized void close()throws SQLException{db.close();}
}
