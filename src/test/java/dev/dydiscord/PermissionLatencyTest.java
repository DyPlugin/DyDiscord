package dev.dydiscord;

import dev.dydiscord.discord.DiscordAccess;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionLatencyTest {
    @Test void independentFreshPermissionRequestsStartWithoutWaitingForEachOther()throws Exception{
        var access=new DiscordAccess();var client=mock(HttpClient.class);var field=DiscordAccess.class.getDeclaredField("client");field.setAccessible(true);field.set(access,client);
        CountDownLatch started=new CountDownLatch(3);ConcurrentHashMap<String,CompletableFuture<HttpResponse<String>>> requests=new ConcurrentHashMap<>();
        when(client.sendAsync(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(call->{HttpRequest request=call.getArgument(0);var future=new CompletableFuture<HttpResponse<String>>();requests.put(request.uri().getPath(),future);started.countDown();return future;});
        var executor=Executors.newSingleThreadExecutor();
        try{
            var c=new java.util.HashMap<String,Object>();var b=new java.util.HashMap<String,Object>();b.put("guild-id","123456789012345678");var settings=dev.dydiscord.config.Settings.read(c,b,null);
            var result=executor.submit(()->access.channel(settings,"223456789012345678"));
            assertTrue(started.await(2,TimeUnit.SECONDS),"Three fresh lookups must be issued before any response completes");
            requests.forEach((path,future)->{var response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn(path.endsWith("/roles")?"[]":path.contains("/channels/")?"{\"guild_id\":\"123456789012345678\",\"type\":0}":"{\"owner_id\":\"323456789012345678\"}");future.complete(response);});
            assertNotNull(result.get(2,TimeUnit.SECONDS));
        }finally{requests.values().forEach(f->f.cancel(true));executor.shutdownNow();}
    }
}
