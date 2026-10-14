package dev.dydiscord;
import dev.dydiscord.discord.*;
import dev.dydiscord.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PanelTest {
    @Test void disabledPanelKeepsRecordAndReenableUsesSameMessage()throws Exception{var original=service.refresh("g","c",remote);service.disable(remote);assertEquals(original,service.current());assertEquals(original,store.panel().orElseThrow());service.refresh("g","c",remote);assertEquals(1,remote.creates);assertEquals(1,remote.disables);}
    @TempDir Path folder;Store store;PanelService service;Remote remote;
    static class Remote implements PanelService.Transport{int creates,updates,disables;boolean exists=true,denied;List<String> found=new ArrayList<>();public boolean exists(Store.Panel panel)throws Exception{if(denied)throw new java.io.IOException("403");return exists;}public void update(Store.Panel panel){updates++;}public void disable(Store.Panel panel){disables++;}public List<String> recover(String channel){return found;}public String create(String channel){creates++;String id="123456789012345678";found.add(id);return id;}}
    @BeforeEach void setup()throws Exception{store=new Store(folder.resolve("db"));service=new PanelService(store);remote=new Remote();}
    @AfterEach void close()throws Exception{store.close();}
    @Test void sendsAndReconnectUseSingleMessage()throws Exception{service.refresh("g","c",remote);service.refresh("g","c",remote);PanelService restarted=new PanelService(store);restarted.refresh("g","c",remote);assertEquals(1,remote.creates);assertEquals(2,remote.updates);}
    @Test void parallelSendHasSingleCreator()throws Exception{ExecutorService pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->service.refresh("g","c",remote));var b=pool.submit(()->service.refresh("g","c",remote));assertEquals(a.get().message(),b.get().message());assertEquals(1,remote.creates);}finally{pool.shutdownNow();}}
    @Test void permissionErrorsNeverGenerateNewMessage()throws Exception{service.refresh("g","c",remote);remote.denied=true;assertThrows(Exception.class,()->service.refresh("g","c",remote));assertEquals(1,remote.creates);}
    @Test void confirmedDeletedMessageCanBeRecreated()throws Exception{service.refresh("g","c",remote);remote.exists=false;remote.found.clear();service.refresh("g","c",remote);assertEquals(2,remote.creates);}
    @Test void sentButUnsavedMessageRecoveredWithoutDuplicate()throws Exception{Store spy=spy(store);doAnswer(invocation->{Store.Panel p=invocation.getArgument(0);if(!p.message().equals("pending"))throw new java.sql.SQLException("disk full");return invocation.callRealMethod();}).when(spy).panel(any(Store.Panel.class));PanelService first=new PanelService(spy);assertThrows(Exception.class,()->first.refresh("g","c",remote));assertEquals("pending",store.panel().orElseThrow().message());service=new PanelService(store);service.refresh("g","c",remote);assertEquals(1,remote.creates);}
    @Test void ambiguousPendingSendRequiresRepair()throws Exception{store.panel(new Store.Panel("g","c","pending"));assertThrows(Exception.class,()->service.refresh("g","c",remote));assertEquals(0,remote.creates);}
    @Test void channelChangeDisablesOldButtons()throws Exception{service.refresh("g","c",remote);remote.found.clear();service.refresh("g","new",remote);assertEquals(1,remote.disables);assertEquals("new",store.panel().orElseThrow().channel());}
}
