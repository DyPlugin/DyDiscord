package dev.dydiscord.config;

import java.net.URI;
import java.util.*;

public record Settings(String prefix, String language, String serverName, String invite,
        boolean linking, int codeLength, int codeExpiry, int cooldown, int attempts, int attemptWindow,
        int confirmationTimeout, boolean botEnabled, String token, String tokenSource, String guild,
        String botLanguage, String botPrefix, boolean prefixEnabled, String activity, String panelChannel,
        boolean consoleEnabled, String consoleChannel, boolean commandsEnabled, boolean administrator,
        Set<String> roles,String panelImage,boolean updateChecks,ChatSettings chat,RoleSettings roleSync,BoostSettings boostRewards,boolean panelEnabled,CommunitySettings community,LinkRewardSettings linkRewards,ToolsSettings tools,GiveawaySettings giveaways) {
    @Override public String toString() { return "Settings[language="+language+", botLanguage="+botLanguage+", token=REDACTED]"; }
    public boolean needsRestart(Settings other) {
        return !token.equals(other.token) || !guild.equals(other.guild) || !botLanguage.equals(other.botLanguage)
            || prefixEnabled != other.prefixEnabled || commandsEnabled != other.commandsEnabled || chat.toMinecraft()!=other.chat.toMinecraft()
            || memberIntentRequested()!=other.memberIntentRequested();
    }
    public boolean memberIntentRequested(){return roleSync.enabled()&&roleSync.memberEvents()||boostRewards.enabled();}
    public static Settings read(Map<String,Object> c, Map<String,Object> b, String environment) {
        number(c,"config-version",1,1,2); number(b,"config-version",1,1,2);
        String lang=choice(c,"lang","auto",Set.of("en","tr","auto"));
        String invite=string(c,"discord.invite-url",""); validateInvite(invite);
        String panel=id(b,"panel.channel-id"), console=id(b,"console.channel-id");
        if (!panel.isEmpty() && panel.equals(console)) throw invalid("console.channel-id");
        Object raw=get(b,"console.commands.allowed-role-ids",List.of());
        if (!(raw instanceof List<?> list) || list.size()>100) throw invalid("console.commands.allowed-role-ids");
        Set<String> roles=new HashSet<>(); for(Object v:list) { if(!(v instanceof String s)||!snowflake(s))throw invalid("console.commands.allowed-role-ids"); roles.add(s); }
        String token=string(b,"token",""); String source="file";
        if(environment!=null&&!environment.isBlank()){token=environment;source="environment";}
        if(token.length()>512 || token.chars().anyMatch(Character::isISOControl))throw invalid("token");
        return new Settings(bounded(c,"prefix","&7[&9DyDiscord&7]",128),lang,bounded(c,"server-name","Minecraft Server",100),invite,
            bool(c,"linking.enabled",true),number(c,"linking.code-length",8,8,16),number(c,"linking.code-expiry-seconds",300,30,900),
            number(c,"linking.generation-cooldown-seconds",30,5,300),number(c,"linking.max-attempts",5,1,10),
            number(c,"linking.attempt-window-seconds",300,30,900),number(c,"linking.confirmation-timeout-seconds",30,10,120),
            bool(b,"enabled",false),token,source,id(b,"guild-id"),choice(b,"bot.language","en",Set.of("en","tr")),
            choice(b,"bot.prefix","dy!",Set.of("dy!")),bool(b,"bot.prefix-commands-enabled",true),"",
            panel,bool(b,"console.enabled",false),console,bool(b,"console.commands.enabled",false),bool(b,"console.commands.allow-administrator",true),Set.copyOf(roles),image(b),bool(c,"updates.enabled",true),ChatSettings.read(b,panel,console),RoleSettings.read(b,id(b,"guild-id"),roles),BoostSettings.read(b),bool(b,"panel.enabled",true),CommunitySettings.read(c,b),LinkRewardSettings.read(c,b),ToolsSettings.read(c,b),GiveawaySettings.read(b));
    }
    private static String image(Map<String,Object> values){String value=bounded(values,"panel.image-url","",2048);if(value.isEmpty())return value;try{URI uri=URI.create(value);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getPort()!=-1||value.chars().anyMatch(Character::isISOControl))throw invalid("panel.image-url");return value;}catch(IllegalArgumentException e){throw invalid("panel.image-url");}}
    public static void validateInvite(String value) {
        if(value.isEmpty())return;
        try { URI u=new URI(value); String p=u.getRawPath();
            if(value.length()>200 || !"https".equals(u.getScheme()) || u.getUserInfo()!=null || u.getPort()!=-1 || u.getQuery()!=null || u.getFragment()!=null
                || !("discord.gg".equals(u.getHost()) && p.matches("/[A-Za-z0-9-]{2,64}")
                || "discord.com".equals(u.getHost()) && p.matches("/invite/[A-Za-z0-9-]{2,64}"))) throw invalid("discord.invite-url");
        } catch(java.net.URISyntaxException e){throw invalid("discord.invite-url");}
    }
    public static boolean snowflake(String s){return s!=null&&s.matches("[1-9][0-9]{16,19}")&&new java.math.BigInteger(s).bitLength()<=64;}
    public static Object get(Map<String,Object> map,String path,Object fallback){Object v=map;for(String k:path.split("\\.")){if(!(v instanceof Map<?,?> m)||!m.containsKey(k))return fallback;v=m.get(k);}return v;}
    static String string(Map<String,Object> m,String p,String d){Object v=get(m,p,d);if(!(v instanceof String s))throw invalid(p);return s;}
    static String bounded(Map<String,Object> m,String p,String d,int limit){String s=string(m,p,d);if(s.length()>limit||s.chars().anyMatch(Character::isISOControl))throw invalid(p);return s;}
    static String id(Map<String,Object> m,String p){String s=string(m,p,"");if(!s.isEmpty()&&!snowflake(s))throw invalid(p);return s;}
    static String choice(Map<String,Object> m,String p,String d,Set<String> values){String s=string(m,p,d);if(!values.contains(s))throw invalid(p);return s;}
    static boolean bool(Map<String,Object> m,String p,boolean d){Object v=get(m,p,d);if(!(v instanceof Boolean b))throw invalid(p);return b;}
    static int number(Map<String,Object> m,String p,int d,int min,int max){Object v=get(m,p,d);if(!(v instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<min||n.intValue()>max)throw invalid(p);return n.intValue();}
    public static IllegalArgumentException invalid(String path){return new IllegalArgumentException("Invalid setting: "+path);}
}
