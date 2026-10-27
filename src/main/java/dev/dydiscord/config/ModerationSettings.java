package dev.dydiscord.config;

import java.util.*;

/** 1.2.0 staff tools: required linking, cheat checks, player reports, nickname and ban sync. */
public record ModerationSettings(boolean requireLink,boolean bypassOps,boolean freeze,int reminderSeconds,String sound,Set<String> allowedCommands,List<String> quitCommands,
        String voiceChannel,String noticeMode,String noticeChannel,String checkLogChannel,int reportCooldown,String reportChannel,String reportRole,
        boolean nicknameSync,String nicknameFormat,String banSync) {
    public static final Set<String> NOTIFY=Set.of("dm","channel","both");
    static final Set<String> QUIT_BLOCKED=Set.of("op","deop","stop","restart","reload","execute","sudo");
    public static final Set<String> BAN_SYNC=Set.of("off","minecraft-to-discord","discord-to-minecraft","both");
    public ModerationSettings{allowedCommands=Set.copyOf(allowedCommands);quitCommands=List.copyOf(quitCommands);}
    public static ModerationSettings defaults(){return new ModerationSettings(false,true,true,15,"block.note_block.pling",Set.of("msg","tell","r","w","dydiscord","discord","link"),List.of(),"","dm","","",60,"","",false,"{name}","off");}
    public boolean banToDiscord(){return banSync.equals("minecraft-to-discord")||banSync.equals("both");}
    public boolean banFromDiscord(){return banSync.equals("discord-to-minecraft")||banSync.equals("both");}
    public boolean directMessage(){return !noticeMode.equals("channel");}
    public boolean channelNotice(){return !noticeMode.equals("dm");}
    /** The nickname a linked member gets: the format with the Minecraft name, cut to Discord's 32 characters. */
    public String nickname(String name){String value=nicknameFormat.replace("{name}",name);return value.length()>32?value.substring(0,32):value;}
    @Override public String toString(){return "ModerationSettings[requireLink="+requireLink+", banSync="+banSync+", quitCommands=REDACTED]";}

    public static ModerationSettings read(Map<String,Object> c,Map<String,Object> b,String console){
        String sound=Settings.bounded(c,"cheat-check.sound","block.note_block.pling",100);
        if(!sound.isEmpty()&&!sound.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+"))throw Settings.invalid("cheat-check.sound");
        Set<String> allowed=new LinkedHashSet<>();Object raw=Settings.get(c,"cheat-check.allowed-commands",List.of());
        if(!(raw instanceof List<?> list)||list.size()>50)throw Settings.invalid("cheat-check.allowed-commands");
        for(Object entry:list){if(entry instanceof String blank&&blank.isEmpty())continue;if(!(entry instanceof String word)||!word.strip().replaceFirst("^/","").toLowerCase(Locale.ROOT).matches("(?:[a-z0-9_.-]+:)?[a-z0-9_.-]{1,32}"))throw Settings.invalid("cheat-check.allowed-commands");allowed.add(word.strip().replaceFirst("^/","").toLowerCase(Locale.ROOT));}
        // The reward command grammar; kick and ban are the point here, so only server-control verbs are refused.
        List<String> quit=BoostSettings.commands(c,"cheat-check.quit-commands",QUIT_BLOCKED);
        String voice=Settings.id(b,"cheat-check.voice-channel-id"),notice=Settings.id(b,"cheat-check.channel-id"),log=Settings.id(b,"cheat-check.log-channel-id"),reports=Settings.id(b,"reports.channel-id");
        for(String path:List.of("cheat-check.channel-id","cheat-check.log-channel-id","reports.channel-id"))if(!console.isEmpty()&&console.equals(Settings.id(b,path)))throw Settings.invalid(path);
        String format=Settings.bounded(b,"moderation.nickname-format","{name}",32);if(!format.contains("{name}"))throw Settings.invalid("moderation.nickname-format");
        return new ModerationSettings(Settings.bool(c,"linking.require-to-join",false),Settings.bool(c,"linking.require-bypass-ops",true),
            Settings.bool(c,"cheat-check.freeze",true),Settings.number(c,"cheat-check.reminder-seconds",15,0,600),sound,allowed,quit,
            voice,Settings.choice(b,"cheat-check.notify","dm",NOTIFY),notice,log,Settings.number(c,"reports.cooldown-seconds",60,0,3600),reports,Settings.id(b,"reports.ping-role-id"),
            Settings.bool(b,"moderation.nickname-sync",false),format,Settings.choice(b,"moderation.ban-sync","off",BAN_SYNC));
    }
}
