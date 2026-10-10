package dev.dydiscord.config;

import java.util.Map;

/** Three channel purposes are disjoint, even when one direction is disabled. */
public record ChatSettings(String channel,boolean toDiscord,boolean toMinecraft,boolean join,boolean quit,boolean death,int joinColor,int quitColor,int deathColor) {
    public static ChatSettings read(Map<String,Object> values,String panel,String console){
        String channel=Settings.id(values,"chat.channel-id");if(!channel.isEmpty()&&(channel.equals(panel)||channel.equals(console)))throw Settings.invalid("chat.channel-id");
        return new ChatSettings(channel,Settings.bool(values,"chat.minecraft-to-discord",false),Settings.bool(values,"chat.discord-to-minecraft",false),Settings.bool(values,"chat.events.join.enabled",false),Settings.bool(values,"chat.events.quit.enabled",false),Settings.bool(values,"chat.events.death.enabled",false),color(values,"join","#55AA55"),color(values,"quit","#AAAAAA"),color(values,"death","#FF5555"));
    }
    private static int color(Map<String,Object> values,String event,String fallback){String path="chat.events."+event+".color",text=Settings.string(values,path,fallback);if(!text.matches("#[0-9a-fA-F]{6}"))throw Settings.invalid(path);return Integer.parseInt(text.substring(1),16);}
}
