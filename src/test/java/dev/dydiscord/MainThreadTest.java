package dev.dydiscord;
import org.bukkit.Server;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MainThreadTest {
    private DyDiscord host(BukkitScheduler scheduler)throws Exception{DyDiscord plugin=mock(DyDiscord.class);Server server=mock(Server.class);when(plugin.getServer()).thenReturn(server);when(server.getScheduler()).thenReturn(scheduler);doCallRealMethod().when(plugin).onMain(any(Runnable.class));doCallRealMethod().when(plugin).mainValue(any(Callable.class));return plugin;}
    @Test void timedOutDispatchCannotRunLaterWhenSchedulerRecovers()throws Exception{BukkitScheduler scheduler=mock(BukkitScheduler.class);DyDiscord plugin=host(scheduler);AtomicReference<Runnable> pending=new AtomicReference<>();when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call->{pending.set(call.getArgument(1));return mock(BukkitTask.class);});AtomicInteger dispatched=new AtomicInteger();assertThrows(TimeoutException.class,()->plugin.mainValue(dispatched::incrementAndGet));assertNotNull(pending.get());pending.get().run();assertEquals(0,dispatched.get());}
    @Test void dispatchRunsThroughSchedulerRatherThanCallingThread()throws Exception{BukkitScheduler scheduler=mock(BukkitScheduler.class);DyDiscord plugin=host(scheduler);ExecutorService scheduled=Executors.newSingleThreadExecutor(r->new Thread(r,"test-server-thread"));try{when(scheduler.runTask(eq(plugin),any(Runnable.class))).thenAnswer(call->{scheduled.execute(call.getArgument(1));return mock(BukkitTask.class);});String thread=plugin.mainValue(()->Thread.currentThread().getName());assertEquals("test-server-thread",thread);}finally{scheduled.shutdownNow();}}
}
