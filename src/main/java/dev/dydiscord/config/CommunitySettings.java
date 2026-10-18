package dev.dydiscord.config;

import java.net.URI;
import java.util.*;

/** Explicitly configured public facts; addresses are never inferred from network interfaces. */
public record CommunitySettings(String javaAddress,String bedrockAddress,List<Link> links,List<Field> fields,Set<String> informationChannels) {
    public CommunitySettings { links=List.copyOf(links);fields=List.copyOf(fields);informationChannels=Set.copyOf(informationChannels); }
    public record Link(String name,String url) {}
    public record Field(String name,String value) {}
    public boolean allows(String action,String channel){return !Set.of("players","server","ip","links").contains(action)||informationChannels.isEmpty()||informationChannels.contains(channel);}
    public static CommunitySettings read(Map<String,Object> config,Map<String,Object> bot){
        if(!(Settings.get(config,"community",Map.of()) instanceof Map<?,?>)||!(Settings.get(bot,"community",Map.of()) instanceof Map<?,?>))throw Settings.invalid("community");
        List<Link> links=new ArrayList<>();for(Map<?,?> entry:entries(config,"community.links")){
            String name=text(entry,"name",80),url=text(entry,"url",512);
            try{URI uri=URI.create(url);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getPort()!=-1||url.matches(".*[\\s<>\\\\].*"))throw Settings.invalid("community.links.url");}
            catch(IllegalArgumentException e){throw Settings.invalid("community.links.url");}links.add(new Link(name,url));
        }
        int linkSize=0;for(Link link:links){int length=link.url().replace("@","%40").replace("(","%28").replace(")","%29").length();if(length>1024)throw Settings.invalid("community.links.url");linkSize+=link.name().length()*2+length;}if(linkSize>5400)throw Settings.invalid("community.links");
        List<Field> fields=new ArrayList<>();for(Map<?,?> entry:entries(config,"community.server-fields"))fields.add(new Field(text(entry,"name",80),text(entry,"value",160)));
        Object raw=Settings.get(bot,"community.information-channel-ids",List.of());if(!(raw instanceof List<?> channels)||channels.size()>100)throw Settings.invalid("community.information-channel-ids");
        Set<String> ids=new LinkedHashSet<>();for(Object value:channels){if(value instanceof String blank&&blank.isEmpty())continue;if(!(value instanceof String id)||!Settings.snowflake(id))throw Settings.invalid("community.information-channel-ids");ids.add(id);}
        return new CommunitySettings(address(config,"community.java-address"),address(config,"community.bedrock-address"),links,fields,ids);
    }
    private static List<Map<?,?>> entries(Map<String,Object> config,String key){Object raw=Settings.get(config,key,List.of());if(!(raw instanceof List<?> list)||list.size()>10)throw Settings.invalid(key);List<Map<?,?>> entries=new ArrayList<>();for(Object value:list){if(!(value instanceof Map<?,?> entry))throw Settings.invalid(key);entries.add(entry);}return entries;}
    private static String text(Map<?,?> entry,String key,int max){Object value=entry.get(key);if(!(value instanceof String text)||text.isBlank()||text.length()>max||text.chars().anyMatch(Character::isISOControl))throw Settings.invalid("community."+key);return text;}
    private static String address(Map<String,Object> config,String key){String value=Settings.bounded(config,key,"",255);if(value.isEmpty())return value;
        if(!value.matches("(?:[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?|\\[[0-9A-Fa-f:]+\\])(?::[0-9]{1,5})?"))throw Settings.invalid(key);
        int split=value.lastIndexOf(':');if(split>value.lastIndexOf(']')&&split>=0){int port=Integer.parseInt(value.substring(split+1));if(port<1||port>65535)throw Settings.invalid(key);}return value;
    }
}
