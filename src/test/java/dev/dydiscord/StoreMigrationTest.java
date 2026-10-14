package dev.dydiscord;

import dev.dydiscord.storage.Store;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreMigrationTest {
    @TempDir Path folder;
    static final UUID UUID_ONE=UUID.fromString("5992f081-e6e5-4558-82af-a3515b5a35bf");
    static final String DISCORD_ONE="123456789012345678",DISCORD_TWO="223456789012345678",VERSION="original-record-version";
    static final Instant LINKED_AT=Instant.parse("2026-10-07T00:00:00Z");

    private Path legacy(boolean brokenReceipts)throws Exception{
        Path path=folder.resolve("legacy.db");Class.forName("org.sqlite.JDBC");
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+path);Statement s=c.createStatement()){
            s.execute("CREATE TABLE schema_version(version INTEGER NOT NULL)");s.execute("INSERT INTO schema_version VALUES(1)");
            s.execute("CREATE TABLE links(uuid TEXT PRIMARY KEY,discord TEXT NOT NULL UNIQUE,name TEXT NOT NULL,linked_at TEXT NOT NULL,version TEXT NOT NULL UNIQUE)");
            try(PreparedStatement insert=c.prepareStatement("INSERT INTO links VALUES(?,?,?,?,?)")){insert.setString(1,UUID_ONE.toString());insert.setString(2,DISCORD_ONE);insert.setString(3,"Alice");insert.setString(4,LINKED_AT.toString());insert.setString(5,VERSION);insert.executeUpdate();}
            s.execute("CREATE TABLE player_preferences(uuid TEXT PRIMARY KEY,language TEXT NOT NULL)");
            try(PreparedStatement insert=c.prepareStatement("INSERT INTO player_preferences VALUES(?,'tr')")){insert.setString(1,UUID_ONE.toString());insert.executeUpdate();}
            if(brokenReceipts)s.execute("CREATE TABLE console_receipts(message TEXT PRIMARY KEY)");
        }return path;
    }
    @Test void legacyMigrationPreservesLinkIdentityDateVersionAndPreferences()throws Exception{
        Path path=legacy(false);Store.Link expected=new Store.Link(UUID_ONE,DISCORD_ONE,"Alice",LINKED_AT,VERSION);
        try(Store store=new Store(path)){assertEquals(expected,store.byUuid(UUID_ONE).orElseThrow());assertEquals(expected,store.byDiscord(DISCORD_ONE).orElseThrow());assertEquals("tr",store.preferences().get(UUID_ONE));}
        try(Store reopened=new Store(path)){assertEquals(expected,reopened.byUuid(UUID_ONE).orElseThrow());}
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+path);Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT version FROM schema_version")){assertTrue(r.next());assertEquals(3,r.getInt(1));assertFalse(r.next());}
    }
    @Test void failedMigrationRollsBackSchemaAndPreservesLegacyLink()throws Exception{
        Path path=legacy(true);assertThrows(SQLException.class,()->new Store(path));
        try(Connection c=DriverManager.getConnection("jdbc:sqlite:"+path);Statement s=c.createStatement()){
            try(ResultSet r=s.executeQuery("SELECT version FROM schema_version")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
            try(ResultSet r=s.executeQuery("PRAGMA table_info(links)")){Set<String> columns=new HashSet<>();while(r.next())columns.add(r.getString("name"));assertFalse(columns.contains("discord_name"));}
            try(ResultSet r=s.executeQuery("SELECT discord,version FROM links WHERE uuid='"+UUID_ONE+"'")){assertTrue(r.next());assertEquals(DISCORD_ONE,r.getString(1));assertEquals(VERSION,r.getString(2));}
        }
    }
    @Test void schemaTwoUpgradeBacksUpCommittedWalAndPreservesAccount()throws Exception{
        Path path=folder.resolve("schema-two.db");Store.Link link;
        try(Store store=new Store(path)){link=store.link(UUID_ONE,DISCORD_ONE,"Alice","DiscordAlice",LINKED_AT);}
        try(Connection writer=DriverManager.getConnection("jdbc:sqlite:"+path);Statement statement=writer.createStatement()){
            statement.execute("PRAGMA journal_mode=WAL");statement.execute("PRAGMA wal_autocheckpoint=0");
            for(String table:List.of("role_history","role_targets","role_sweeps","role_cleanup_roles"))statement.execute("DROP TABLE "+table);
            statement.execute("UPDATE schema_version SET version=2");statement.execute("UPDATE links SET name='FromWal'");
            try(Store upgraded=new Store(path)){var preserved=upgraded.byUuid(UUID_ONE).orElseThrow();assertEquals(link.version(),preserved.version());assertEquals("FromWal",preserved.name());assertEquals("DiscordAlice",preserved.discordName());}
            Path backup;try(var files=java.nio.file.Files.list(folder)){backup=files.filter(p->p.getFileName().toString().startsWith("schema-two.db.backup-schema2-")).findFirst().orElseThrow();}
            try(Connection original=DriverManager.getConnection("jdbc:sqlite:"+backup);Statement s=original.createStatement();ResultSet r=s.executeQuery("SELECT name,version,(SELECT version FROM schema_version) AS schema FROM links")){assertTrue(r.next());assertEquals("FromWal",r.getString(1));assertEquals(link.version(),r.getString(2));assertEquals(2,r.getInt(3));}
        }
    }
    @Test void usernameCachePersistsWithoutChangingIdentityDateOrVersion()throws Exception{
        Path path=folder.resolve("new.db");Store.Link first,updated;
        try(Store store=new Store(path)){
            first=store.link(UUID_ONE,DISCORD_ONE,"Alice","the_claww",LINKED_AT);
            assertEquals("the_claww",first.discordName());
            updated=store.updateDiscordName(first,"claww_new").orElseThrow();
            assertEquals(first.uuid(),updated.uuid());assertEquals(first.discord(),updated.discord());assertEquals(first.linkedAt(),updated.linkedAt());assertEquals(first.version(),updated.version());
        }
        try(Store reopened=new Store(path)){assertEquals(updated,reopened.byUuid(UUID_ONE).orElseThrow());}
    }
    @Test void staleCacheRefreshCannotTouchReplacementLink()throws Exception{
        try(Store store=new Store(folder.resolve("new.db"))){
            Store.Link first=store.link(UUID_ONE,DISCORD_ONE,"Alice","first",LINKED_AT);assertTrue(store.unlink(first));
            Store.Link replacement=store.link(UUID_ONE,DISCORD_TWO,"Alice","replacement",LINKED_AT.plusSeconds(1));
            assertTrue(store.updateDiscordName(first,"stale").isEmpty());assertEquals(replacement,store.byUuid(UUID_ONE).orElseThrow());
        }
    }
}
