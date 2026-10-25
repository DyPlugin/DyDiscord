package dev.dydiscord;
import dev.dydiscord.config.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public final class TestSupport {
    /** All text of a Components V2 message, for checking what a container card says. */
    public static String v2Text(net.dv8tion.jda.api.utils.messages.MessageCreateData data){if(data==null||!data.isUsingComponentsV2())return "";StringBuilder out=new StringBuilder();for(var top:data.getComponents())if(top.getType()==net.dv8tion.jda.api.components.Component.Type.CONTAINER)for(var child:top.asContainer().getComponents())if(child instanceof net.dv8tion.jda.api.components.textdisplay.TextDisplay text)out.append(text.getContent()).append('\n');return out.toString();}
    /** Matches a V2 container message whose text contains the given text. */
    public static org.mockito.ArgumentMatcher<net.dv8tion.jda.api.utils.messages.MessageCreateData> v2(String text){return data->v2Text(data).contains(text);}
    public static Configuration config(Path path)throws Exception{Configuration c=new Configuration(path);c.load();return c;}
    public static Settings settings()throws Exception{try(var c=TestSupport.class.getResourceAsStream("/config.yml");var b=TestSupport.class.getResourceAsStream("/bot.yml")){return Settings.read(Configuration.parse(new String(c.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)),Configuration.parse(new String(b.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)),null);}}
    public static final class Time extends Clock {private Instant now=Instant.parse("2026-10-07T00:00:00Z");public void advance(long seconds){now=now.plusSeconds(seconds);}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
