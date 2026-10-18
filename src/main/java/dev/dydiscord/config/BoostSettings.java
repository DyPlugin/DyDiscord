package dev.dydiscord.config;

import java.util.*;

/** Opt-in commands from the server owner; no economy or website provider is assumed. */
public record BoostSettings(boolean enabled,List<String> commands,List<String> removeCommands,
        int recheckSeconds,int cooldownHours) {
    private static final Set<String> BLOCKED=Set.of("op","deop","stop","restart","reload",
        "kick","ban","ban-ip","pardon","pardon-ip","execute","sudo");
    public BoostSettings { commands=List.copyOf(commands);removeCommands=List.copyOf(removeCommands); }
    @Override public String toString(){return "BoostSettings[enabled="+enabled+", commands=REDACTED, removeCommands=REDACTED]";}
    public static BoostSettings disabled(){return new BoostSettings(false,List.of(),List.of(),300,168);}
    public static BoostSettings read(Map<String,Object> values){
        if(!(Settings.get(values,"boost-rewards",Map.of()) instanceof Map<?,?>))throw Settings.invalid("boost-rewards");
        return new BoostSettings(Settings.bool(values,"boost-rewards.enabled",false),
            commands(values,"boost-rewards.commands"),commands(values,"boost-rewards.remove-commands"),
            Settings.number(values,"boost-rewards.recheck-seconds",300,30,3600),
            Settings.number(values,"boost-rewards.cooldown-hours",168,168,8760));
    }
    private static List<String> commands(Map<String,Object> values,String path){
        Object raw=Settings.get(values,path,List.of());
        if(!(raw instanceof List<?> entries)||entries.size()>10)throw Settings.invalid(path);
        List<String> commands=new ArrayList<>();
        for(Object entry:entries){ if(entry instanceof String blank&&blank.isEmpty())continue;
            if(!(entry instanceof String text)||text.isBlank()||text.length()>256||text.codePoints().anyMatch(BoostSettings::unsafeCharacter))throw Settings.invalid(path);
            String command=text.strip();if(command.startsWith("/"))command=command.substring(1);
            int separator=command.indexOf(' ');
            if(separator<1)throw Settings.invalid(path);
            String verb=command.substring(0,separator).toLowerCase(Locale.ROOT);
            if(!verb.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_.-]+"))throw Settings.invalid(path);
            int namespace=verb.indexOf(':');if(namespace>=0)verb=verb.substring(namespace+1);
            if(BLOCKED.contains(verb))throw Settings.invalid(path);
            String args=command.substring(separator+1);
            if(!args.contains("{player}")&&!args.contains("{uuid}"))throw Settings.invalid(path);
            String remaining=args.replace("{player}","").replace("{uuid}","");
            if(remaining.indexOf('{')>=0||remaining.indexOf('}')>=0)throw Settings.invalid(path);
            commands.add(command);
        }
        return List.copyOf(commands);
    }
    private static boolean unsafeCharacter(int codePoint){
        int type=Character.getType(codePoint);
        return Character.isISOControl(codePoint)||type==Character.FORMAT||type==Character.LINE_SEPARATOR||type==Character.PARAGRAPH_SEPARATOR;
    }
}
