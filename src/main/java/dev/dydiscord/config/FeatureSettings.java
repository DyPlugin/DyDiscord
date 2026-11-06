package dev.dydiscord.config;

import java.util.*;

/** 1.4.0 features: support tickets, health alerts, the /whois staff command and Discord-to-Minecraft groups. All start off. */
public record FeatureSettings(Tickets tickets,Alerts alerts,Whois whois,Groups groups) {
    /** /ticket opens a thread in channel; staff replies there reach the player in game. */
    public record Tickets(boolean enabled,String channel,boolean privateThreads,String pingRole,int cooldown) {}
    /** Thresholds of 0 turn a rule off; a blank channel turns every alert off. */
    public record Alerts(String channel,int lowTps,int memoryPercent,List<Integer> milestones,int cooldownMinutes,String pingRole) {
        public Alerts{milestones=List.copyOf(milestones);}
        public boolean enabled(){return !channel.isEmpty()&&(lowTps>0||memoryPercent>0||!milestones.isEmpty());}
    }
    /** An empty role list limits /whois to members with Manage Server or Administrator. */
    public record Whois(boolean enabled,Set<String> roles) {public Whois{roles=Set.copyOf(roles);}}
    /** One Discord role grants one LuckPerms group; several rules may share a role. */
    public record Rule(String role,String group) {}
    public record Groups(boolean enabled,List<Rule> rules,Set<String> staffGroups,int recheckSeconds) {
        public Groups{rules=List.copyOf(rules);staffGroups=Set.copyOf(staffGroups);}
        /** Groups whose role the member holds, in rule order. */
        public Set<String> desired(Set<String> memberRoles){Set<String> groups=new LinkedHashSet<>();for(Rule rule:rules)if(memberRoles.contains(rule.role()))groups.add(rule.group());return groups;}
        public Set<String> managed(){Set<String> groups=new LinkedHashSet<>();for(Rule rule:rules)groups.add(rule.group());return groups;}
        /** Staff-like names need explicit trust in staff-groups, even though the rule names them. */
        public boolean trusted(String group){return !staffName(group)||staffGroups.contains(group);}
    }
    private static final java.util.regex.Pattern STAFF=java.util.regex.Pattern.compile("^(op|ops|owner|co-?owner|root)$|admin|operator|moderator|mod$|^mod|staff|helper|developer|^dev|manager");
    /** Group names that usually carry staff or operator permissions. */
    public static boolean staffName(String group){return STAFF.matcher(group).find();}
    public static FeatureSettings defaults(){return new FeatureSettings(new Tickets(false,"",true,"",60),new Alerts("",15,90,List.of(),30,""),new Whois(false,Set.of()),new Groups(false,List.of(),Set.of(),300));}

    public static FeatureSettings read(Map<String,Object> b,String console,String panel,String chat,Set<String> syncedRoles,String guild){
        String tickets=Settings.id(b,"tickets.channel-id"),alerts=Settings.id(b,"alerts.channel-id");boolean ticketsOn=Settings.bool(b,"tickets.enabled",false);
        if(ticketsOn&&tickets.isEmpty())throw Settings.invalid("tickets.channel-id");
        if(!tickets.isEmpty()&&(tickets.equals(console)||tickets.equals(panel)||tickets.equals(chat)))throw Settings.invalid("tickets.channel-id");
        if(!alerts.isEmpty()&&alerts.equals(console))throw Settings.invalid("alerts.channel-id");
        int tps=Settings.number(b,"alerts.low-tps",15,0,19),memory=Settings.number(b,"alerts.memory-percent",90,0,99);
        if(memory>0&&memory<50)throw Settings.invalid("alerts.memory-percent");
        Object rawMilestones=Settings.get(b,"alerts.player-milestones",List.of());if(!(rawMilestones instanceof List<?> values)||values.size()>10)throw Settings.invalid("alerts.player-milestones");
        TreeSet<Integer> milestones=new TreeSet<>();for(Object value:values){if(!(value instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>100000)throw Settings.invalid("alerts.player-milestones");milestones.add(n.intValue());}
        Object rawRoles=Settings.get(b,"whois.allowed-role-ids",List.of());Set<String> whoisRoles=ids(rawRoles,"whois.allowed-role-ids");
        if(!(Settings.get(b,"discord-groups",Map.of()) instanceof Map<?,?>))throw Settings.invalid("discord-groups");
        Object rawRules=Settings.get(b,"discord-groups.rules",List.of());if(!(rawRules instanceof List<?> entries)||entries.size()>50)throw Settings.invalid("discord-groups.rules");
        List<Rule> rules=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Object entry:entries){
            if(!(entry instanceof Map<?,?> m))throw Settings.invalid("discord-groups.rules");
            if(!(m.get("role-id") instanceof String role)||!Settings.snowflake(role)||role.equals(guild))throw Settings.invalid("discord-groups.rules.role-id");
            // A role that Minecraft -> Discord sync manages would keep granting the group that gives it; refuse the loop.
            if(syncedRoles.contains(role))throw Settings.invalid("discord-groups.rules.role-id");
            if(!(m.get("group") instanceof String group)||!group.matches("[a-z0-9_.-]{1,36}")||group.equals("default"))throw Settings.invalid("discord-groups.rules.group");
            if(seen.add(role+"/"+group))rules.add(new Rule(role,group));
        }
        Object rawStaff=Settings.get(b,"discord-groups.staff-groups",List.of());if(!(rawStaff instanceof List<?> staffEntries)||staffEntries.size()>50)throw Settings.invalid("discord-groups.staff-groups");
        Set<String> staff=new LinkedHashSet<>();for(Object value:staffEntries){if(value instanceof String blank&&blank.isEmpty())continue;if(!(value instanceof String group)||!group.matches("[a-z0-9_.-]{1,36}"))throw Settings.invalid("discord-groups.staff-groups");staff.add(group);}
        return new FeatureSettings(new Tickets(ticketsOn,tickets,Settings.bool(b,"tickets.private-threads",true),Settings.id(b,"tickets.ping-role-id"),Settings.number(b,"tickets.cooldown-seconds",60,0,3600)),
            new Alerts(alerts,tps,memory,List.copyOf(milestones),Settings.number(b,"alerts.cooldown-minutes",30,5,1440),Settings.id(b,"alerts.ping-role-id")),
            new Whois(Settings.bool(b,"whois.enabled",false),whoisRoles),
            new Groups(Settings.bool(b,"discord-groups.enabled",false),rules,staff,Settings.number(b,"discord-groups.recheck-seconds",300,60,3600)));
    }
    private static Set<String> ids(Object raw,String path){
        if(!(raw instanceof List<?> entries)||entries.size()>25)throw Settings.invalid(path);
        Set<String> ids=new LinkedHashSet<>();for(Object entry:entries){if(entry instanceof String blank&&blank.isEmpty())continue;if(!(entry instanceof String id)||!Settings.snowflake(id))throw Settings.invalid(path);ids.add(id);}return ids;
    }
}
