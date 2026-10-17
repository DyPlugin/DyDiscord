package dev.dydiscord.language;

import dev.dydiscord.config.Configuration;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class Messages {
    private final Map<String,Map<String,String>> packs;
    private final Set<String> missing;
    private Messages(Map<String,Map<String,String>> packs,Set<String> missing){this.packs=Map.copyOf(packs);this.missing=Set.copyOf(missing);}
    public Set<String> missing(){return missing;}
    /** Newly introduced keys migrate in the selected language; existing owner text is untouched. */
    public static boolean releaseKey(String key){
        if(Set.of("account-date","account-roles","copy-code-hint","confirm-click-hint","invite-click-hint","help-admin-topics","help-bot-language","invite-auto-unavailable").contains(key))return true;
        return List.of("token-entry-","chat-personal-","chat-already-","chat-enabled","chat-disabled","setting-unchanged","help-prefix","help-bot-guild","help-bot-token","help-topic-","setup-","startup-","help-page","help-card-","giveaway-","players-","addresses-","address-","links-","notice-","help-chat-","help-announce","help-giveaway-").stream().anyMatch(key::startsWith)
            ||Set.of("slash-players","slash-ip","slash-links","help-community","server-started","information-channel-only","link-welcome","community-notice","report-saved","validation-result","setup-guide","reward-preview","help-maintenance","help-restart-notice","help-support-report","help-config-validate","help-translations-validate","help-setup","help-rewards-preview").contains(key);
    }
    public void validatePanel(String serverName){for(String lang:List.of("en","tr")){
        // Validate structured help before publishing a reload, rather than failing on Discord.
        try{dev.dydiscord.discord.HelpView.card(this,lang,true);dev.dydiscord.discord.HelpView.card(this,lang,false);}catch(IllegalArgumentException invalid){throw new IllegalArgumentException("Invalid message: discord/messages_"+lang+".yml:help-card");}
        String serverTitle=render(true,lang,"server-info-title",Map.of("server_name",serverName));
        if(serverTitle.isBlank()||serverTitle.length()>256||get(true,lang,"server-info-players").isBlank()||get(true,lang,"server-info-players").length()>256||get(true,lang,"server-info-version").isBlank()||get(true,lang,"server-info-version").length()>256||Math.max(get(true,lang,"help").length(),get(true,lang,"commands-help").length())+2+get(true,lang,"help-server").length()>2000)throw new IllegalArgumentException("Invalid server/help text: discord/messages_"+lang+".yml");
        Map<String,String> giveawayBounds=Map.of("prize","*".repeat(200),"winners",String.join(", ",Collections.nCopies(20,"*".repeat(32))),"ends","x".repeat(48));
        for(String key:List.of("giveaway-open","giveaway-drawn","giveaway-cancelled"))if(render(true,lang,key,giveawayBounds).length()>4096)throw new IllegalArgumentException("Invalid message: discord/messages_"+lang+".yml:"+key);
        String title=render(true,lang,"panel-title",Map.of("server_name",serverName)),body=get(true,lang,"panel-description"),privacy=get(true,lang,"panel-privacy");
        if(title.isBlank()||body.isBlank()||privacy.isBlank()||title.length()>256||6+title.length()+body.length()+privacy.length()>4000)throw new IllegalArgumentException("Invalid panel text: discord/messages_"+lang+".yml");
    }}
    public static String language(String global,String preference,String locale){
        if(!global.equals("auto"))return global;if(preference!=null&&!preference.equals("auto"))return preference;
        return locale!=null&&locale.toLowerCase(Locale.ROOT).matches("tr([_-].*)?")?"tr":"en";
    }
    public String get(boolean discord,String lang,String key){return packs.get((discord?"discord/":"")+lang).getOrDefault(key,key);}
    public String render(boolean discord,String lang,String key,Map<String,String> values){
        String template=get(discord,lang,key);
        // Format only templates; externally supplied values never become color/mention markup.
        if(!discord)template=template.replaceAll("&([0-9a-fk-orA-FK-OR])","§$1");
        Matcher m=Pattern.compile("\\{([a-z_]+)}").matcher(template);StringBuffer out=new StringBuffer();
        while(m.find()){String v=values.getOrDefault(m.group(1),m.group());v=discord?safeDiscord(v):safeMinecraft(v);m.appendReplacement(out,Matcher.quoteReplacement(v));}m.appendTail(out);return out.toString();
    }
    public static String safeMinecraft(String s){return s.replace("§","").replaceAll("[\\p{Cntrl}]"," ");}
    public static String safeDiscord(String s){return s.replace("\\","\\\\").replace("@","@\u200b").replace("`","ˋ").replace("*","\\*").replace("_","\\_").replace("~","\\~").replace("|","\\|").replace("[","\\[").replace("]","\\]").replace("<","‹").replaceAll("[\\p{Cntrl}]"," ");}
    public static Set<String> placeholders(String s){Set<String> set=new HashSet<>();Matcher m=Pattern.compile("\\{([a-z_]+)}").matcher(s);while(m.find())set.add(m.group(1));return set;}
    private static boolean compatible(String surface,String lang,String key,String value,String bundled)throws IOException{
        if(placeholders(value).equals(placeholders(bundled)))return true;
        if(!surface.isEmpty()&&key.equals("account")&&placeholders(value).contains("name")&&Set.of("name","discord","date").containsAll(placeholders(value)))return true;
        // Existing owner translations may retain the previous account/confirmation fields.
        if(key.equals("already-linked")&&placeholders(value).isEmpty())return true;
        if(!Set.of("linked","account","confirm").contains(key))return false;
        try(InputStream in=Messages.class.getClassLoader().getResourceAsStream("legacy/release-101/"+surface+"messages_"+lang+".yml")){
            Object old=Configuration.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).get(key);
            return old instanceof String text&&placeholders(value).equals(placeholders(text));
        }
    }
    public static Messages load(Path folder)throws IOException{
        Map<String,Map<String,String>> packs=new HashMap<>();Set<String> missing=new HashSet<>();
        for(String surface:List.of("","discord/"))for(String lang:List.of("en","tr")){
            String file=surface+"messages_"+lang+".yml";
            Map<String,Object> bundled;try(InputStream in=Messages.class.getClassLoader().getResourceAsStream(surface+"messages_en.yml")){if(in==null)throw new IOException("Missing messages");bundled=Configuration.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            Map<String,Object> custom=new HashMap<>(Configuration.read(folder.resolve(file)));Map<String,String> result=new HashMap<>();
            upgradeDefaults(surface,lang,custom);
            for(var entry:bundled.entrySet()){if(!custom.containsKey(entry.getKey()))missing.add(file+":"+entry.getKey());Object v=custom.getOrDefault(entry.getKey(),entry.getValue());int max=4000;if(!surface.isEmpty()){String key=entry.getKey();max=key.startsWith("slash-")?100:switch(key){case "panel-title","giveaway-title","players-title","addresses-title","links-title","address-java","address-bedrock","players-more"->256;case "giveaway-enter"->80;case "modal-title","code-label"->45;case "link","account-button","unlink","confirm-button","cancel-button"->80;case "footer"->100;case "help","status","private-prompt","commands-prompt","commands-help","panel-disabled","panel-missing","console-prefix","console-rejected","console-result"->1600;default->3500;};}if(!(v instanceof String s)||s.length()>max||!compatible(surface,lang,entry.getKey(),s,(String)entry.getValue()))throw new IOException("Invalid message: "+file+":"+entry.getKey());result.put(entry.getKey(),s);}
            packs.put(surface+lang,Map.copyOf(result));
        } return new Messages(packs,missing);
    }
    /** Upgrade only shipped legacy values. Owner-written translations retain strict validation. */
    private static void upgradeDefaults(String surface,String lang,Map<String,Object> custom)throws IOException{
        // New server facts retain the selected language even before migration writes.
        try(InputStream stream=Messages.class.getClassLoader().getResourceAsStream(surface+"messages_"+lang+".yml")){
            if(stream==null)throw new IOException("Missing server messages");var current=Configuration.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            for(String key:(surface.isEmpty()?List.of("panel-disabled","help-panel-enable","help-panel-disable"):List.of("server-info-title","server-info-players","server-info-version","slash-server","help-server","panel-disabled","commands-prompt","commands-help")))custom.putIfAbsent(key,current.get(key));
        }
        boolean tr=lang.equals("tr");Map<String,String> legacy=new HashMap<>();
        if(!surface.isEmpty()&&Set.of("DyDiscord v{version}: {state}","DyDiscord v{version} • {state}").contains(custom.getOrDefault("status","")))custom.put("status",tr?"DyDiscord {version} • {state}":"DyDiscord {version}: {state}");
        if(surface.isEmpty()){
            if(tr)legacy.put("bot-setup-incomplete","&eBotu açmadan önce bot.yml içinde token ve guild-id ayarlayın, sonra /dydiscord yönetim yenile çalıştırın.");
            legacy.put("link-code",tr?"&7Eşleme kodunuz: &e{code} &7({minutes} dakika sonra geçersiz olur).":"&7Your linking code: &e{code} &7(expires in {minutes} minutes).");
            legacy.put("account",tr?"&7Minecraft: &f{name} &7| Discord: &f{discord} &7| Eşleme (UTC): &f{date}":"&7Minecraft: &f{name} &7| Discord: &f{discord} &7| Linked (UTC): &f{date}");
            legacy.put("linked",tr?"&aMinecraft ve Discord hesaplarınız eşlendi!":"&aYour Minecraft and Discord accounts are now linked!");
        }else{
            legacy.put("account",tr?"Minecraft: {name}\nDiscord: {discord}\nEşleme: {date}":"Minecraft: {name}\nDiscord: {discord}\nLinked: {date}");
            legacy.put("linked",tr?"Hesaplarınız eşlendi.":"Your accounts are now linked.");
            legacy.put("panel-description",tr?"Minecraft ve Discord hesaplarınızı bağlayın.\nOyunda /discord eşle yazın, ardından Hesap Eşle butonuna basıp kodunuzu girin. Hesap bilgilerinizi yalnızca siz görürsünüz.":"Connect your Minecraft and Discord accounts.\nRun /discord link in-game, then select Link Account and enter your code. Only you can see your account details.");
        }
        try(InputStream in=Messages.class.getClassLoader().getResourceAsStream(surface+"messages_"+lang+".yml")){
            if(in==null)throw new IOException("Missing messages");Map<String,Object> defaults=Configuration.parse(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            try(InputStream previous=Messages.class.getClassLoader().getResourceAsStream("legacy/simplify-101/"+surface+"messages_"+lang+".yml")){
                if(previous!=null){Map<String,Object> shipped=Configuration.parse(new String(previous.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));shipped.forEach((key,value)->{if(defaults.containsKey(key)&&Objects.equals(value,custom.get(key)))custom.put(key,defaults.get(key));});}
            }
            try(InputStream previous=Messages.class.getClassLoader().getResourceAsStream("legacy/release-101/"+surface+"messages_"+lang+".yml")){
                if(previous!=null){Map<String,Object> shipped=Configuration.parse(new String(previous.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));shipped.forEach((key,value)->{if(defaults.containsKey(key)&&Objects.equals(value,custom.get(key)))custom.put(key,defaults.get(key));});}
            }
            defaults.forEach((key,value)->{if(key.startsWith("state-")||key.startsWith("language-name-")||key.equals("update-available")||key.startsWith("chat-")||key.equals("server-stopping")||releaseKey(key))custom.putIfAbsent(key,value);});
            if(tr)try(InputStream previous=Messages.class.getClassLoader().getResourceAsStream("legacy/"+surface+"messages_tr.yml")){
                if(previous==null)throw new IOException("Missing legacy messages");
                Map<String,Object> shipped=Configuration.parse(new String(previous.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                boolean complete=custom.keySet().containsAll(shipped.keySet());
                shipped.forEach((key,value)->{if(Objects.equals(value,custom.get(key)))custom.put(key,defaults.get(key));});
                if(complete)defaults.forEach(custom::putIfAbsent);
            }
            String numbered=tr?"Minecraft ve Discord hesaplarınızı üç adımda bağlayın.\n\n1. Oyunda **/discord eşle** yazın.\n2. Aşağıdaki **Hesap Eşle** butonuna basın.\n3. Özel kodunuzu açılan forma girin.":"Connect your Minecraft and Discord accounts in three steps.\n\n1. Run **/discord link** in Minecraft.\n2. Select **Link Account** below.\n3. Enter your private code in the form.";
            String compact=tr?"Minecraft hesabınızı Discord hesabınızla eşleştirmek için oyunda `/discord eşle` komutuyla kodunuzu alın ve aşağıdaki **Hesap Eşle** butonundan kodunuzu girin.\n**Hesap Eşle:** Oyun içindeki kodla hesaplarınızı bağlar.\n**Hesabım:** Eşlenen Minecraft ve Discord hesaplarınızı gösterir.\n**Eşlemeyi Kaldır:** Onayınızla mevcut bağlantıyı kaldırır.":"To connect your Minecraft and Discord accounts, get your code in-game with `/discord link`, then select **Link Account** below and enter your code.\n**Link Account:** Connects your accounts using your in-game code.\n**My Account:** Shows your linked Minecraft and Discord accounts.\n**Unlink:** Removes your existing link after confirmation.";
            boolean oldPanel=!surface.isEmpty()&&(Objects.equals(custom.get("panel-description"),legacy.get("panel-description"))||Objects.equals(custom.get("panel-description"),numbered)||Objects.equals(custom.get("panel-description"),compact));
            boolean oldAccount=Objects.equals(custom.get("account"),legacy.get("account"))||Objects.equals(custom.get("linked"),legacy.get("linked"));
            legacy.forEach((key,value)->{if(value.equals(custom.get(key)))custom.put(key,defaults.get(key));});
            if(tr&&surface.isEmpty()&&Objects.equals(custom.get("link-code"),"&7Eşleme kodunuz: &e{code}"))custom.put("link-code",defaults.get("link-code"));
            if(oldPanel)custom.put("panel-description",defaults.get("panel-description"));
            if(!surface.isEmpty()&&oldPanel)custom.putIfAbsent("panel-privacy",defaults.get("panel-privacy"));
            if(surface.isEmpty()&&oldAccount)custom.putIfAbsent("discord-account",defaults.get("discord-account"));
        }
    }
}
