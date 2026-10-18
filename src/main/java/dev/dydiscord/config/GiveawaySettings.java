package dev.dydiscord.config;

import java.util.*;

/** Optional community giveaways; policy is snapshotted for each new giveaway. */
public record GiveawaySettings(boolean enabled,String channel,boolean requireLinked,Set<String> allowedRoles) {
    public GiveawaySettings {
        allowedRoles=Set.copyOf(allowedRoles);
        if(!channel.isEmpty()&&!Settings.snowflake(channel)||enabled&&channel.isEmpty()||allowedRoles.size()>20||allowedRoles.stream().anyMatch(id->!Settings.snowflake(id)))throw Settings.invalid("giveaways");
    }
    public static GiveawaySettings defaults(){return new GiveawaySettings(false,"",false,Set.of());}
    public static GiveawaySettings read(Map<String,Object> b){
        String channel=Settings.id(b,"giveaways.channel-id");
        if(!channel.isEmpty()&&(channel.equals(Settings.id(b,"panel.channel-id"))||channel.equals(Settings.id(b,"console.channel-id"))))throw Settings.invalid("giveaways.channel-id");
        Object raw=Settings.get(b,"giveaways.allowed-role-ids",List.of());
        if(!(raw instanceof List<?> list)||list.size()>20)throw Settings.invalid("giveaways.allowed-role-ids");
        Set<String> roles=new HashSet<>();for(Object value:list){if(value instanceof String blank&&blank.isEmpty())continue;if(!(value instanceof String id)||!Settings.snowflake(id))throw Settings.invalid("giveaways.allowed-role-ids");roles.add(id);}
        return new GiveawaySettings(Settings.bool(b,"giveaways.enabled",false),channel,Settings.bool(b,"giveaways.require-linked",false),roles);
    }
}
