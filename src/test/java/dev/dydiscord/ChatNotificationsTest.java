package dev.dydiscord;
import dev.dydiscord.command.Definition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatNotificationsTest {
 @Test void separateNotificationFeatureHasNoCommandOrRuntimeService(){
  assertFalse(Definition.ALL.stream().anyMatch(d->d.key().equals("chat-notify")));
  assertThrows(ClassNotFoundException.class,()->Class.forName("dev.dydiscord.chat.ChatNotifications"));
 }
}
