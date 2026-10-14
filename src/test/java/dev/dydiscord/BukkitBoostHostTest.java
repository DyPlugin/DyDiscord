package dev.dydiscord;

import dev.dydiscord.boost.*;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BukkitBoostHostTest {
    @Test void changedPolicyRejectsScheduledCommandBeforeActualDispatch()throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);
        AtomicReference<Runnable> queued=new AtomicReference<>();doAnswer(call->{queued.set(call.getArgument(0));return null;}).when(plugin).onMain(any());
        var future=new BukkitBoostHost(plugin).dispatch("lp user Test parent add booster",()->false);queued.get().run();
        assertFalse(future.get());verify(server,never()).dispatchCommand(any(),any());
    }
    @Test void actualConsoleOutcomeIsReportedWithoutClaimingProviderSuccess()throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);ConsoleCommandSender console=mock(ConsoleCommandSender.class);
        when(plugin.getServer()).thenReturn(server);when(server.getConsoleSender()).thenReturn(console);when(server.dispatchCommand(console,"sitecredit add Alice 20")).thenReturn(true);
        doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(plugin).onMain(any());
        assertTrue(new BukkitBoostHost(plugin).dispatch("sitecredit add Alice 20",()->true).get());verify(server).dispatchCommand(console,"sitecredit add Alice 20");
    }
    @Test void canceledDeliveryCannotRunLate()throws Exception{
        DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);
        AtomicReference<Runnable> queued=new AtomicReference<>();doAnswer(call->{queued.set(call.getArgument(0));return null;}).when(plugin).onMain(any());
        var future=new BukkitBoostHost(plugin).dispatch("money give Alice 10",()->true);future.cancel(false);queued.get().run();verify(server,never()).dispatchCommand(any(),any());
    }
}
