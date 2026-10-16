package dev.dydiscord;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunitySettingsTest {
    private Object read(Map<String,Object> c,Map<String,Object> b)throws Exception {
        try{return Class.forName("dev.dydiscord.config.CommunitySettings").getMethod("read",Map.class,Map.class).invoke(null,c,b);}
        catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw e;}
    }
    @Test void rejectsUnsafeLinksAndUnboundedPublicFields()throws Exception {
        for(String url:List.of("http://example.com","https://user:secret@example.com","https://example.com/\n@everyone","javascript:alert(1)"))
            assertThrows(IllegalArgumentException.class,()->read(Map.of("community",Map.of("links",List.of(Map.of("name","Website","url",url)))),Map.of()));
        assertThrows(IllegalArgumentException.class,()->read(Map.of("community",Map.of("server-fields",Collections.nCopies(11,Map.of("name","Info","value","Text")))),Map.of()));
    }
    @Test void parsesConfiguredAddressesAndRejectsMalformedChannelIds()throws Exception {
        Object settings=read(Map.of("community",Map.of("java-address","play.example.com:25565","bedrock-address","bedrock.example.com:19132")),Map.of());
        assertEquals("play.example.com:25565",settings.getClass().getMethod("javaAddress").invoke(settings));
        assertThrows(IllegalArgumentException.class,()->read(Map.of(),Map.of("community",Map.of("information-channel-ids",List.of("@everyone")))));
        for(String address:List.of("https://example.com","name @everyone","host:99999","127.0.0.1\nsecret"))assertThrows(IllegalArgumentException.class,()->read(Map.of("community",Map.of("java-address",address)),Map.of()));
    }
}
