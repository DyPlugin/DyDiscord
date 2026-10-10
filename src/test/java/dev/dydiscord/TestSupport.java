package dev.dydiscord;
import dev.dydiscord.config.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
public final class TestSupport {
    public static Configuration config(Path path)throws Exception{Configuration c=new Configuration(path);c.load();return c;}
    public static Settings settings()throws Exception{try(var c=TestSupport.class.getResourceAsStream("/config.yml");var b=TestSupport.class.getResourceAsStream("/bot.yml")){return Settings.read(Configuration.parse(new String(c.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)),Configuration.parse(new String(b.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)),null);}}
    public static final class Time extends Clock {private Instant now=Instant.parse("2026-10-07T00:00:00Z");public void advance(long seconds){now=now.plusSeconds(seconds);}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
