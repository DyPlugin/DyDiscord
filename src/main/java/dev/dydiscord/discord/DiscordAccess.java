package dev.dydiscord.discord;

import com.fasterxml.jackson.databind.*;
import dev.dydiscord.config.Settings;
import net.dv8tion.jda.api.Permission;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Security checks use current REST permission data; URLs and errors never contain credentials. */
public final class DiscordAccess {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json=new ObjectMapper();
    private volatile long retryAfterNanos;
    public record Channel(JsonNode channel,JsonNode roles,String guild,String owner) {
        public Channel(JsonNode channel,JsonNode roles,String guild){this(channel,roles,guild,"");}
        public long base(Set<String> ids){long result=0;for(JsonNode role:roles)if(role.path("id").asText().equals(guild)||ids.contains(role.path("id").asText()))result|=bits(role.path("permissions").asText());return result;}
        public boolean administrator(Set<String> ids){return (base(ids)&Permission.ADMINISTRATOR.getRawValue())!=0;}
        public long permissions(String actor,Set<String> ids){long base=base(ids);if(!owner.isEmpty()&&owner.equals(actor)||administrator(ids))return -1L;JsonNode overwrites=channel.path("permission_overwrites");
            for(JsonNode o:overwrites)if(o.path("id").asText().equals(guild))base=(base&~bits(o.path("deny").asText()))|bits(o.path("allow").asText());
            long deny=0,allow=0;for(JsonNode o:overwrites)if(o.path("type").asInt()==0&&ids.contains(o.path("id").asText())){deny|=bits(o.path("deny").asText());allow|=bits(o.path("allow").asText());}base=(base&~deny)|allow;
            for(JsonNode o:overwrites)if(o.path("type").asInt()==1&&o.path("id").asText().equals(actor))base=(base&~bits(o.path("deny").asText()))|bits(o.path("allow").asText());return base;
        }
        public boolean privateTo(Settings settings,String bot,Set<String> botRoles){long view=Permission.VIEW_CHANNEL.getRawValue();if((permissions("",Set.of())&view)!=0)return false;
            for(JsonNode role:roles){String id=role.path("id").asText();if(id.equals(guild)||settings.roles().contains(id)||botRoles.contains(id)&&role.path("managed").asBoolean()||administrator(Set.of(id)))continue;if((permissions("",Set.of(id))&view)!=0)return false;}
            for(JsonNode o:channel.path("permission_overwrites"))if(o.path("type").asInt()==1&&!o.path("id").asText().equals(bot)&&(bits(o.path("allow").asText())&view)!=0)return false;
            return true;
        }
        private static long bits(String value){try{return Long.parseUnsignedLong(value);}catch(NumberFormatException e){throw new IllegalArgumentException("Malformed Discord permissions");}}
    }
    private HttpRequest request(Settings settings,String path)throws Exception{
        if(System.nanoTime()<retryAfterNanos)throw new SetupProblem(SetupProblem.Code.RATE_LIMITED);
        return HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/"+path)).timeout(Duration.ofSeconds(8)).header("Authorization","Bot "+settings.token()).header("User-Agent","DyDiscord/1.0.1 (JDA permission verification)").GET().build();
    }
    private JsonNode get(Settings settings,String path)throws Exception{return response(client.send(request(settings,path),HttpResponse.BodyHandlers.ofString()));}
    private JsonNode response(HttpResponse<String> response)throws Exception{
        if(response.statusCode()==429){double seconds=60;try{seconds=json.readTree(response.body()).path("retry_after").asDouble(60);}catch(Exception ignored){/* A malformed 429 fails closed with a bounded fallback. */}if(!Double.isFinite(seconds))seconds=60;seconds=Math.max(1,Math.min(seconds,3600));retryAfterNanos=System.nanoTime()+(long)(seconds*1_000_000_000L);throw new SetupProblem(SetupProblem.Code.RATE_LIMITED);}
        if(response.statusCode()==401||response.statusCode()==403)throw new SetupProblem(SetupProblem.Code.ACCESS_DENIED);
        if(response.statusCode()!=200)throw new IllegalStateException("Discord permission verification HTTP "+response.statusCode());if(response.body().length()>1024*1024)throw new IllegalStateException("Discord permission response too large");return json.readTree(response.body());
    }
    public Channel channel(Settings settings,String id)throws Exception{
        if(!Settings.snowflake(id)||!Settings.snowflake(settings.guild()))throw new IllegalArgumentException("Missing Discord channel ID");
        // These independent fresh reads overlap; no permission result is cached or omitted.
        List<CompletableFuture<HttpResponse<String>>> reads=new ArrayList<>();
        try{
            for(String path:List.of("channels/"+id,"guilds/"+settings.guild()+"/roles","guilds/"+settings.guild()))reads.add(client.sendAsync(request(settings,path),HttpResponse.BodyHandlers.ofString()));
            CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).get(9,TimeUnit.SECONDS);
            JsonNode channel=response(reads.get(0).join()),roles=response(reads.get(1).join()),guild=response(reads.get(2).join());
            if(!channel.path("guild_id").asText().equals(settings.guild())||channel.path("type").asInt(-1)!=0)throw new IllegalArgumentException("Wrong guild or channel type");
            if(!roles.isArray())throw new IllegalStateException("Malformed Discord roles");return new Channel(channel,roles,settings.guild(),guild.path("owner_id").asText(""));
        }finally{reads.forEach(read->read.cancel(true));}
    }
    public boolean role(Settings settings,String id)throws Exception{return Settings.snowflake(id)&&!id.equals(settings.guild())&&get(settings,"guilds/"+settings.guild()+"/roles").findValuesAsText("id").contains(id);}
    public JsonNode roles(Settings settings)throws Exception{JsonNode roles=get(settings,"guilds/"+settings.guild()+"/roles");if(!roles.isArray())throw new IllegalStateException("Malformed Discord roles");return roles;}
}
