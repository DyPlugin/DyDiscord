package dev.dydiscord.update;

import com.fasterxml.jackson.databind.*;
import okhttp3.*;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class Modrinth implements AutoCloseable {
    public static final String PROJECT="dydiscord",URL="https://modrinth.com/plugin/dydiscord";
    public record Response(int status,String body,long retryAfter) {}
    public record Release(String id,String version) {public String url(){return URL+"/version/"+id;}}
    private final OkHttpClient client=new OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(10,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build();
    public Response fetch(String version,String minecraft,String platform)throws IOException {
        String loaders=new ObjectMapper().writeValueAsString(loaders(platform));
        String endpoint="https://api.modrinth.com/v2/project/"+PROJECT+"/version?include_changelog=false&loaders="+encode(loaders)+"&game_versions="+encode("[\""+minecraft+"\"]");
        Request request=new Request.Builder().url(endpoint).header("User-Agent","DyDiscord/"+version+" (Claww; "+URL+")").header("Accept","application/json").build();
        try(okhttp3.Response response=client.newCall(request).execute()){
            long retry=900;try{String value=response.header("Retry-After");if(value!=null)retry=Math.max(900,Long.parseLong(value));}catch(NumberFormatException ignored){}
            if(response.code()!=200)return new Response(response.code(),"",retry);
            ResponseBody body=response.body();if(body==null)throw new IOException("Empty Modrinth response");
            byte[] bytes=body.byteStream().readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("Oversized Modrinth response");return new Response(200,new String(bytes,StandardCharsets.UTF_8),retry);
        }
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    public static Set<String> loaders(String platform){return switch(platform.toLowerCase(Locale.ROOT)){case "bukkit","craftbukkit"->Set.of("bukkit");case "spigot"->Set.of("spigot","bukkit");case "paper"->Set.of("paper","spigot","bukkit");case "purpur"->Set.of("purpur","paper","spigot","bukkit");case "folia"->Set.of("folia","paper","spigot","bukkit");default->Set.of();};}
    public static Optional<Release> newer(String json,String current,String minecraft,Set<String> loaders)throws IOException {
        return newer(json,current,minecraft,loaders,VersionNumber.parse(current).map(v->!v.prerelease().isEmpty()).orElse(false));
    }
    public static Optional<Release> newer(String json,String current,String minecraft,Set<String> loaders,boolean includeBeta)throws IOException {
        VersionNumber installed=VersionNumber.parse(current).orElseThrow(()->new IOException("Unrecognized installed version"));
        if(json.length()>1_048_576)throw new IOException("Oversized Modrinth response");JsonNode list=new ObjectMapper().readTree(json);if(list==null||!list.isArray()||list.size()>5000)throw new IOException("Invalid Modrinth version list");
        Release latest=null;VersionNumber best=installed;
        for(JsonNode item:list){
            String status=item.path("status").asText(),channel=item.path("version_type").asText(),id=item.path("id").asText(),version=item.path("version_number").asText();
            if(!status.equals("listed")||!(channel.equals("release")||channel.equals("beta")&&includeBeta)||!id.matches("[A-Za-z0-9]{1,32}"))continue;
            if(!contains(item.path("game_versions"),Set.of(minecraft))||!contains(item.path("loaders"),loaders))continue;
            Optional<VersionNumber> number=VersionNumber.parse(version);if(number.isEmpty()||!includeBeta&&!number.get().prerelease().isEmpty())continue;
            // The owner renamed the historical 1.0.0-1.0.2 beta series to 1.0.0.
            // Keep semantic ordering intact; never recommend those superseded artifacts.
            if(installed.prerelease().isEmpty()&&number.get().prerelease().equals(List.of("beta"))&&number.get().core().get(0).equals(java.math.BigInteger.ONE)&&number.get().core().get(1).signum()==0&&number.get().core().get(2).compareTo(java.math.BigInteger.valueOf(2))<=0)continue;
            if(number.get().compareTo(best)>0){latest=new Release(id,version);best=number.get();}
        }return Optional.ofNullable(latest);
    }
    private static boolean contains(JsonNode node,Set<String> values){if(!node.isArray())return false;for(JsonNode value:node)if(value.isTextual()&&values.contains(value.textValue()))return true;return false;}
    @Override public void close(){client.dispatcher().cancelAll();client.connectionPool().evictAll();client.dispatcher().executorService().shutdownNow();}
}
