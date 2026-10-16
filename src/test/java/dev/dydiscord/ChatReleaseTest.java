package dev.dydiscord;

import dev.dydiscord.chat.ChatBridge;
import dev.dydiscord.chat.ChatText;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatReleaseTest {
    @Test void truncationFitsItsLimitAndKeepsReadableWholeWords(){
        assertEquals("Hello…",ChatText.plain("Hello wonderful world",12));
        String value=ChatText.plain("😀".repeat(30),12);
        assertTrue(value.codePointCount(0,value.length())<=12);
    }
}
