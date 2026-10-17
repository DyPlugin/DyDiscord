package dev.dydiscord;

import dev.dydiscord.chat.ChatPreferences;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ChatPreferencesTest {
    @TempDir Path folder;
    @Test void preferencesSurviveRestartAndDoNotDefaultBeforeLoad()throws Exception{
        UUID id=UUID.randomUUID();Path file=folder.resolve("chat.db");
        try(var prefs=new ChatPreferences(file)){
            assertNull(prefs.peek(id));
            assertTrue(prefs.get(id).get(5,TimeUnit.SECONDS).inbound());
            prefs.set(id,"inbound",false).get(5,TimeUnit.SECONDS);
            assertThrows(IllegalArgumentException.class,()->prefs.set(id,"outbound",false));
            assertFalse(prefs.peek(id).inbound());
            assertTrue(prefs.peek(id).outbound());
        }
        try(var prefs=new ChatPreferences(file)){
            assertNull(prefs.peek(id));
            var value=prefs.get(id).get(5,TimeUnit.SECONDS);
            assertFalse(value.inbound());assertTrue(value.outbound());assertFalse(value.notifications());
            prefs.leave(id);assertNull(prefs.peek(id));
        }
    }
    @Test void invalidPreferenceNeverChangesPersistedState()throws Exception{
        try(var prefs=new ChatPreferences(folder.resolve("chat.db"))){
            UUID id=UUID.randomUUID();
            assertThrows(IllegalArgumentException.class,()->prefs.set(id,"not-a-preference",false));
            assertTrue(prefs.get(id).get(5,TimeUnit.SECONDS).outbound());
        }
    }
    @Test void leavingDuringBlockedWriteDoesNotRepopulatePlayerCache()throws Exception{
        UUID id=UUID.randomUUID();Path file=folder.resolve("chat.db");
        try(var prefs=new ChatPreferences(file)){
            prefs.get(id).get(5,TimeUnit.SECONDS);
            try(var lock=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var statement=lock.createStatement()){
                statement.execute("BEGIN EXCLUSIVE");
                var pending=prefs.set(id,"inbound",false);prefs.leave(id);
                statement.execute("COMMIT");pending.get(5,TimeUnit.SECONDS);
                assertNull(prefs.peek(id));
                assertFalse(prefs.get(id).get(5,TimeUnit.SECONDS).inbound());
            }
        }
    }
    @Test void unreadableStorageNeverActivatesDefaultOrChangedPreferences()throws Exception{
        try(var prefs=new ChatPreferences(folder)){
            UUID id=UUID.randomUUID();assertThrows(Exception.class,()->prefs.get(id).get(5,TimeUnit.SECONDS));assertNull(prefs.peek(id));
            assertThrows(Exception.class,()->prefs.set(id,"inbound",false).get(5,TimeUnit.SECONDS));assertNull(prefs.peek(id));
        }
    }
    @Test void unchangedPreferenceDoesNotWriteAndRetiredChoicesAreRejected()throws Exception{
        UUID id=UUID.randomUUID();Path file=folder.resolve("same.db");
        try(var prefs=new ChatPreferences(file)){
            prefs.get(id).get(5,TimeUnit.SECONDS);
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var sql=db.createStatement()){
                sql.execute("CREATE TRIGGER deny_write BEFORE INSERT ON chat_preferences BEGIN SELECT RAISE(ABORT,'unexpected write'); END");
                assertTrue(prefs.set(id,"inbound",true).get(5,TimeUnit.SECONDS).inbound());
                assertThrows(IllegalArgumentException.class,()->prefs.set(id,"notify",true));
                assertThrows(IllegalArgumentException.class,()->prefs.set(id,"outbound",false));
            }
        }
    }
    @Test void closeSettlesQueuedWorkAndReleasesSqliteBeforeReturning()throws Exception{
        Path file=folder.resolve("close.db");ChatPreferences prefs=new ChatPreferences(file);UUID id=UUID.randomUUID();prefs.get(id).get(5,TimeUnit.SECONDS);
        try(var db=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());var sql=db.createStatement()){
            sql.execute("BEGIN EXCLUSIVE");var pending=prefs.set(id,"inbound",false);prefs.close();assertTrue(pending.isDone());assertNull(prefs.peek(id));sql.execute("COMMIT");
        }finally{prefs.close();}
        java.nio.file.Files.move(file,folder.resolve("closed.db"));
    }}
