package dev.dydiscord.config;

import java.util.*;

/** 1.1.0 integrations: placeholders, statistics, the live status card and logs from other DyPlugin plugins. */
public record IntegrationSettings(boolean placeholders,boolean metrics,boolean statusCard,String statusChannel,int statusInterval,String logChannel,Set<String> logEvents) {
    /** Events that can be logged; the names match the bot.yml switches. */
    public static final List<String> EVENTS=List.of("dyclaim.claims","dyclaim.trust","dyclaim.transfers","dyrtp.teleports","dyhomes.warps","dyhomes.public-homes","dyhomes.teleports");
    /** Switches that stay off unless the owner turns them on, because they can be frequent. */
    static final Set<String> QUIET=Set.of("dyhomes.teleports");
    public IntegrationSettings{logEvents=Set.copyOf(logEvents);}
    public static IntegrationSettings defaults(){Set<String> events=new HashSet<>(EVENTS);events.removeAll(QUIET);return new IntegrationSettings(true,true,false,"",60,"",events);}
    public boolean logs(String event){return !logChannel.isEmpty()&&logEvents.contains(event);}
    public static IntegrationSettings read(Map<String,Object> c,Map<String,Object> b){
        String status=Settings.id(b,"status-card.channel-id"),logs=Settings.id(b,"plugin-logs.channel-id"),console=Settings.id(b,"console.channel-id");
        boolean card=Settings.bool(b,"status-card.enabled",false);
        if(card&&status.isEmpty())throw Settings.invalid("status-card.channel-id");
        if(!status.isEmpty()&&status.equals(console))throw Settings.invalid("status-card.channel-id");
        if(!logs.isEmpty()&&logs.equals(console))throw Settings.invalid("plugin-logs.channel-id");
        Set<String> events=new HashSet<>();for(String event:EVENTS)if(Settings.bool(b,"plugin-logs."+event,!QUIET.contains(event)))events.add(event);
        return new IntegrationSettings(Settings.bool(c,"integrations.placeholderapi",true),Settings.bool(c,"metrics.enabled",true),card,status,Settings.number(b,"status-card.interval-seconds",60,30,3600),logs,events);
    }
}
