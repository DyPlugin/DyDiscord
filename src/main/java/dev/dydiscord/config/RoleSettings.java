package dev.dydiscord.config;

import java.util.*;

/** Minecraft groups project onto this bounded, explicitly managed Discord role set. */
public record RoleSettings(boolean enabled,Set<String> linkedRoles,List<Rule> rules,
        Map<String,String> context,String selection,int recheckSeconds,boolean memberEvents) {
    public RoleSettings {linkedRoles=Set.copyOf(linkedRoles);rules=List.copyOf(rules);context=Map.copyOf(context);}
    public record Rule(String group,Set<String> roleIds,int priority) {
        public Rule {roleIds=Set.copyOf(roleIds);}
    }
    public static RoleSettings disabled(){return new RoleSettings(false,Set.of(),List.of(),Map.of(),"all",300,true);}
    public Set<String> groupRoleIds(){Set<String> ids=new HashSet<>();for(Rule rule:rules)ids.addAll(rule.roleIds());return Set.copyOf(ids);}
    public Set<String> managedIds(){Set<String> ids=new HashSet<>(linkedRoles);ids.addAll(groupRoleIds());return Set.copyOf(ids);}
    public static RoleSettings read(Map<String,Object> values,String guild,Set<String> consoleRoles){
        if(!(Settings.get(values,"roles",Map.of()) instanceof Map<?,?>))throw Settings.invalid("roles");
        Set<String> linked=ids(Settings.get(values,"roles.linked-role-ids",List.of()),"roles.linked-role-ids",false);
        Object raw=Settings.get(values,"roles.groups",List.of());
        if(!(raw instanceof List<?> entries)||entries.size()>100)throw Settings.invalid("roles.groups");
        List<Rule> rules=new ArrayList<>();Set<String> groups=new HashSet<>();
        for(Object entry:entries){
            if(!(entry instanceof Map<?,?> m))throw Settings.invalid("roles.groups");
            Object group=m.get("group");if(!(group instanceof String name)||!key(name)||!groups.add(name))throw Settings.invalid("roles.groups.group");
            Object priority=m.containsKey("priority")?m.get("priority"):0;
            if(!(priority instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<-1_000_000||n.intValue()>1_000_000)throw Settings.invalid("roles.groups.priority");
            rules.add(new Rule(name,ids(m.get("role-ids"),"roles.groups.role-ids",true),n.intValue()));
        }
        Object rawContext=Settings.get(values,"roles.context",Map.of());
        if(!(rawContext instanceof Map<?,?> entriesContext)||entriesContext.size()>16)throw Settings.invalid("roles.context");
        Map<String,String> context=new LinkedHashMap<>();
        for(var entry:entriesContext.entrySet()){
            if(!(entry.getKey() instanceof String k)||!key(k)||!(entry.getValue() instanceof String v)||v.isBlank()||v.length()>128||v.chars().anyMatch(Character::isISOControl))throw Settings.invalid("roles.context");
            context.put(k,v);
        }
        RoleSettings result=new RoleSettings(Settings.bool(values,"roles.enabled",false),linked,rules,context,
            Settings.choice(values,"roles.selection","all",Set.of("all","highest")),Settings.number(values,"roles.recheck-seconds",300,30,3600),Settings.bool(values,"roles.member-events",true));
        Set<String> managed=result.managedIds();
        if(managed.size()>100)throw Settings.invalid("roles");
        for(String id:managed)if(id.equals(guild)||consoleRoles.contains(id))throw Settings.invalid("roles.role-ids");
        return result;
    }
    private static boolean key(String value){return value.matches("[a-z0-9_.-]{1,64}");}
    private static Set<String> ids(Object raw,String path,boolean required){
        if(!(raw instanceof List<?> entries)||entries.size()>100||required&&entries.isEmpty())throw Settings.invalid(path);
        Set<String> ids=new LinkedHashSet<>();for(Object entry:entries){if(!(entry instanceof String id)||!Settings.snowflake(id))throw Settings.invalid(path);ids.add(id);}return Set.copyOf(ids);
    }
}
