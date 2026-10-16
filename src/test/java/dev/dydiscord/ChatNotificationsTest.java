package dev.dydiscord;

import dev.dydiscord.chat.ChatNotifications;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatNotificationsTest {
    @Test void fullCooldownCacheNeverResetsRecentPlayersAndExpiredSlotsCanBeReused(){
        var time=new TestSupport.Time();var notifications=new ChatNotifications(time);UUID first=UUID.randomUUID();
        assertTrue(notifications.admit(first));
        for(int i=1;i<2048;i++)assertTrue(notifications.admit(UUID.randomUUID()));
        UUID overflow=UUID.randomUUID();assertFalse(notifications.admit(overflow));assertFalse(notifications.admit(first));
        time.advance(15);assertTrue(notifications.admit(overflow));assertTrue(notifications.admit(first));
        notifications.leave(first);assertTrue(notifications.admit(first));
    }
}
