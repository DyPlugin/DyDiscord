package dev.dydiscord;

import dev.dydiscord.chat.ChatText;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatTextIdentityTest {
    @Test void preservesIdentityAndLiteralPunctuationWithoutInterpretingItAsMinecraftMarkup() {
        var text=ChatText.minecraft("{role}{name} >> {message}","VIP_EU|1",0x123456,"the_claww","x_y + a*b | c~d");
        assertEquals("VIP_EU|1 the_claww >> x_y + a*b | c~d",text.toPlainText());
        assertTrue(text.getExtra().stream().noneMatch(p->p.isBold()));
    }
    @Test void stripsActualMessageFormattingInsteadOfDeletingEverySpecialCharacter() {
        assertEquals("Name: bold italic code spoiler",ChatText.minecraft("{name}: {message}","",0,"**Name**","**bold** _italic_ `code` ||spoiler||").toPlainText());
    }
    @Test void replyAuthorRetainsUnderscoresAndBars() {
        assertEquals("↪ the_claww|EU — x_y",ChatText.reply("the_claww|EU","x_y"));
    }
    @Test void formattingNeverRestoresMinecraftColorsOrControlCharacters() {
        String result=ChatText.minecraft("{role}{name}: {message}","VIP_1\n&cRed",0,"the_claww\u202e","**Hi** §4red &x&f&f&0&0&0&0end").toPlainText();
        assertTrue(result.contains("VIP_1"));assertTrue(result.contains("the_claww"));
        assertFalse(result.contains("§"));assertFalse(result.contains("\n"));assertFalse(result.contains("\u202e"));
    }
}
