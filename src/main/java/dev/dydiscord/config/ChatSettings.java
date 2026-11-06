package dev.dydiscord.config;

import java.util.Map;

/** Three channel purposes are disjoint, even when one direction is disabled. */
public record ChatSettings(String channel,boolean toDiscord,boolean toMinecraft,boolean join,boolean quit,boolean death,int joinColor,int quitColor,int deathColor,
                           boolean firstJoin,int firstJoinColor,boolean advancement,int advancementColor,boolean webhook,String avatar,boolean attachments) {
    /** Player heads by UUID; {uuid} and {name} are replaced. */
    public static final String AVATAR="https://mc-heads.net/avatar/{uuid}/64";
    public ChatSettings(String channel,boolean toDiscord,boolean toMinecraft,boolean join,boolean quit,boolean death,int joinColor,int quitColor,int deathColor){
        this(channel,toDiscord,toMinecraft,join,quit,death,joinColor,quitColor,deathColor,false,0x55FFFF,false,0xFFAA00,false,AVATAR,false);
    }
    public static ChatSettings read(Map<String,Object> values,String panel,String console){
        String channel=Settings.id(values,"chat.channel-id");if(!channel.isEmpty()&&(channel.equals(panel)||channel.equals(console)))throw Settings.invalid("chat.channel-id");
        String avatar=Settings.string(values,"chat.webhook.avatar-url",AVATAR).trim();if(!avatar.matches("https://\\S{1,500}"))throw Settings.invalid("chat.webhook.avatar-url");
        return new ChatSettings(channel,Settings.bool(values,"chat.minecraft-to-discord",true),Settings.bool(values,"chat.discord-to-minecraft",true),Settings.bool(values,"chat.events.join.enabled",true),Settings.bool(values,"chat.events.quit.enabled",true),Settings.bool(values,"chat.events.death.enabled",true),color(values,"join","#55AA55"),color(values,"quit","#AAAAAA"),color(values,"death","#FF5555"),
                Settings.bool(values,"chat.events.first-join.enabled",false),color(values,"first-join","#55FFFF"),Settings.bool(values,"chat.events.advancement.enabled",false),color(values,"advancement","#FFAA00"),Settings.bool(values,"chat.webhook.enabled",false),avatar,Settings.bool(values,"chat.attachments",false));
    }
    private static int color(Map<String,Object> values,String event,String fallback){String path="chat.events."+event+".color",text=Settings.string(values,path,fallback);if(!text.matches("#[0-9a-fA-F]{6}"))throw Settings.invalid(path);return Integer.parseInt(text.substring(1),16);}
}
