package dev.dydiscord.config;

import java.util.*;

/** Owner configured commands; welcome messages are private and never imply provider success. */
public record LinkRewardSettings(boolean enabled,List<String> commands,boolean welcomeMinecraft,boolean welcomeDiscord) {
    public LinkRewardSettings { commands=List.copyOf(commands); }
    public static LinkRewardSettings disabled(){return new LinkRewardSettings(false,List.of(),false,false);}
    public static LinkRewardSettings read(Map<String,Object> config,Map<String,Object> bot){
        if(!(Settings.get(bot,"link-rewards",Map.of()) instanceof Map<?,?>))throw Settings.invalid("link-rewards");
        if(!(Settings.get(bot,"link-welcome",Map.of()) instanceof Map<?,?>))throw Settings.invalid("link-welcome");
        List<String> commands;
        // The same bounded command grammar and administrative primitive filter as boost rewards.
        try{Map<String,Object> reward=new HashMap<>();reward.put("commands",Settings.get(bot,"link-rewards.commands",List.of()));
            commands=BoostSettings.read(Map.of("boost-rewards",reward)).commands();}
        catch(IllegalArgumentException error){throw Settings.invalid("link-rewards.commands");}
        return new LinkRewardSettings(Settings.bool(bot,"link-rewards.enabled",false),commands,
            Settings.bool(bot,"link-welcome.minecraft",false),Settings.bool(bot,"link-welcome.discord",false));
    }
    @Override public String toString(){return "LinkRewardSettings[enabled="+enabled+", commands=REDACTED]";}
}
