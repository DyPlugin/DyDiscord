package dev.dydiscord;

import dev.dydiscord.chat.ChatEvents;
import dev.dydiscord.config.Configuration;
import dev.dydiscord.console.ConsoleBridge;
import dev.dydiscord.discord.Bot;
import dev.dydiscord.storage.Store;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatCompatibilityTest {
    @TempDir Path folder;
    DyDiscord plugin; Configuration config; ChatEvents events;
    final List<RegisteredListener> registrations=new ArrayList<>();
    @BeforeEach void setup()throws Exception {
        config=TestSupport.config(folder);config.change("bot.yml","chat.minecraft-to-discord",true);
        plugin=mock(DyDiscord.class);when(plugin.settings()).thenAnswer(i->config.active().settings());
        when(plugin.messages()).thenAnswer(i->config.active().messages());when(plugin.clock()).thenReturn(new TestSupport.Time());
        var store=mock(Store.class);when(store.panel()).thenReturn(Optional.empty());when(store.writable()).thenReturn(true);when(plugin.store()).thenReturn(store);
        var console=mock(ConsoleBridge.class);when(console.attached()).thenReturn(true);when(plugin.console()).thenReturn(console);
        when(plugin.getDescription()).thenReturn(new PluginDescriptionFile("DyDiscord","1.0.1","dev.dydiscord.DyDiscord"));
        events=new ChatEvents(plugin);when(plugin.chatEvents()).thenReturn(events);
    }
    @AfterEach void clean(){registrations.forEach(AsyncPlayerChatEvent.getHandlerList()::unregister);}
    private void handler(String name){Plugin other=mock(Plugin.class);when(other.getName()).thenReturn(name);RegisteredListener registration=new RegisteredListener(new Listener(){},(l,e)->{},EventPriority.NORMAL,other,false);registrations.add(registration);AsyncPlayerChatEvent.getHandlerList().register(registration);}
    @Test void doctorShowsCurrentBlockerBeforeAnyPlayerChatsAndRecoversWhenItIsRemoved()throws Exception {
        handler("ExampleChat");Bot bot=new Bot(plugin);
        var lines=bot.diagnostics(true,"tr");
        assertTrue(lines.stream().anyMatch(s->s.contains("duraklatıldı")&&s.contains("MC → DC")));
        assertTrue(lines.stream().anyMatch(s->s.contains("ExampleChat")&&s.contains("kanal değiştirmek")));
        clean();registrations.clear();
        assertEquals("",events.problem("tr"));
        assertTrue(bot.diagnostics(true,"en").stream().noneMatch(s->s.contains("paused")));
    }
    @Test void blockerNamesAreDeduplicatedBoundedAndCannotInsertMinecraftFormatting() {
        handler("ExampleChat");handler("ExampleChat");handler("Zed\n&cChat");
        String result=events.problem("en");assertEquals(1,result.split("ExampleChat",-1).length-1);
        assertTrue(result.contains("Zed Chat"));assertFalse(result.contains("\n"));assertFalse(result.contains("§"));
        for(int i=0;i<12;i++)handler("Chat"+i);
        String bounded=events.problem("en");assertTrue(bounded.contains("…"));assertTrue(bounded.length()<800);
    }
    @Test void disabledDirectionDoesNotReportAnActiveCompatibilityProblem()throws Exception {
        handler("ExampleChat");config.change("bot.yml","chat.minecraft-to-discord",false);
        assertTrue(new Bot(plugin).diagnostics(true,"en").stream().noneMatch(s->s.contains("ExampleChat")||s.contains("paused")));
    }
    @Test void brokenAdapterIsReportedInBothLanguages()throws Exception {
        var field=ChatEvents.class.getDeclaredField("adapterFailed");field.setAccessible(true);field.set(events,true);
        assertTrue(events.problem("en").contains("adapter failed"));assertTrue(events.problem("tr").contains("sohbeti okunamadı"));
        assertTrue(new Bot(plugin).diagnostics(false,"en").stream().anyMatch(s->s.contains("paused")));
    }
    @Test void additiveReloadPreservesCustomMessagesAndSelectedLanguage()throws Exception {
        Path messages=folder.resolve("messages_tr.yml");
        java.nio.file.Files.writeString(messages,java.nio.file.Files.readString(messages).replaceAll("(?m)^chat-compatibility-blocked:.*$","chat-compatibility-blocked: \"Özel açıklama: {plugins}\""));
        config.load();handler("ExampleChat");assertEquals("Özel açıklama: ExampleChat",events.problem("tr"));
        assertFalse(config.active().messages().get(false,"tr","setup-chat-conflict").contains("Minecraft → Discord chat is paused"));
    }
}
