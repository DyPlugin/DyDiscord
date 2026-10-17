package dev.dydiscord.command;
import java.util.*;
import java.util.function.Predicate;
public record Definition(String path,String permission,boolean player,int arguments,boolean remainder,String key) {
    public String[] words(){return path.isEmpty()?new String[0]:path.split(" ");}
    public String usage(){return path+(arguments==0?"":switch(key){case "giveaway-start"->" <seconds> <winners> <prize>";case "chat-inbound","chat-to-minecraft","chat-to-discord","chat-both"->" <on|off>";case "prefix"->" <text>";case "invite"->" <url>";case "language","global-language","bot-language"->" <en|tr|auto>";case "admin-unlink"->" <uuid> <reason>";case "roles-preview","roles-sync"->" <uuid>";case "roles-cleanup-confirm"->" <token>";default->" <id>";});}
    public boolean match(String[] args){String[] words=words();if(args.length!=words.length+arguments&&!(remainder&&args.length>=words.length+arguments))return false;for(int i=0;i<words.length;i++)if(!Words.matches(words[i],args[i]))return false;return true;}
    public static final List<Definition> ALL=List.of(
        d("","dydiscord.invite",false,0,"invite-show"),d("help","dydiscord.help",false,0,"help"),d("link","dydiscord.link",true,0,"link"),d("status","dydiscord.status",true,0,"status"),d("unlink","dydiscord.unlink",true,0,"unlink"),d("language","dydiscord.language",true,1,"language"),
        d("setinvite","dydiscord.admin.invite",false,1,"invite"),
        d("admin invite","dydiscord.admin.invite",false,1,"invite"),d("admin language","dydiscord.admin.language",false,1,"global-language"),d("admin reload","dydiscord.admin.reload",false,0,"reload"),d("admin status","dydiscord.admin.status",false,0,"admin-status"),d("admin doctor","dydiscord.admin.doctor",false,0,"doctor"),
        new Definition("admin prefix","dydiscord.admin.prefix",false,1,true,"prefix"),
        d("admin bot guild","dydiscord.admin.bot.guild",false,1,"bot-guild"),
        d("admin bot token","dydiscord.admin.bot.token",true,0,"bot-token"),
        d("admin bot language","dydiscord.admin.language",false,1,"bot-language"),
        d("admin bot enable","dydiscord.admin.bot",false,0,"bot-enable"),d("admin bot disable","dydiscord.admin.bot",false,0,"bot-disable"),d("admin bot restart","dydiscord.admin.bot",false,0,"bot-restart"),
        d("admin panel channel","dydiscord.admin.panel.channel",false,1,"panel-channel"),d("admin panel send","dydiscord.admin.panel.send",false,0,"panel-send"),d("admin panel refresh","dydiscord.admin.panel.refresh",false,0,"panel-refresh"),
        d("admin panel enable","dydiscord.admin.panel.toggle",false,0,"panel-enable"),d("admin panel disable","dydiscord.admin.panel.toggle",false,0,"panel-disable"),
        d("admin console channel","dydiscord.admin.console.channel",false,1,"console-channel"),d("admin console enable","dydiscord.admin.console.toggle",false,0,"console-enable"),d("admin console disable","dydiscord.admin.console.toggle",false,0,"console-disable"),
        d("admin console commands enable","dydiscord.admin.console.commands",false,0,"commands-enable"),d("admin console commands disable","dydiscord.admin.console.commands",false,0,"commands-disable"),
        new Definition("admin unlink","dydiscord.admin.unlink",false,2,true,"admin-unlink"),
        d("admin roles status","dydiscord.admin.roles.status",false,0,"roles-status"),
        d("admin roles preview","dydiscord.admin.roles.preview",false,1,"roles-preview"),
        d("admin roles sync all","dydiscord.admin.roles.sync.all",false,0,"roles-sync-all"),
        d("admin roles sync","dydiscord.admin.roles.sync",false,1,"roles-sync"),
        d("admin roles cleanup preview","dydiscord.admin.roles.cleanup.preview",false,0,"roles-cleanup-preview"),
        d("admin roles cleanup confirm","dydiscord.admin.roles.cleanup.confirm",false,1,"roles-cleanup-confirm"),
        d("chat","dydiscord.chat.preferences",true,1,"chat-inbound"),
        d("admin chat discord-minecraft","dydiscord.admin.chat",false,1,"chat-to-minecraft"),
        d("admin chat minecraft-discord","dydiscord.admin.chat",false,1,"chat-to-discord"),
        d("admin chat both","dydiscord.admin.chat",false,1,"chat-both"),
        d("admin chat channel","dydiscord.admin.chat.channel",false,1,"chat-channel"),
        new Definition("admin giveaway start","dydiscord.admin.giveaway.start",false,3,true,"giveaway-start"),
        d("admin giveaway end","dydiscord.admin.giveaway.end",false,1,"giveaway-end"),
        d("admin giveaway cancel","dydiscord.admin.giveaway.cancel",false,1,"giveaway-cancel"),
        d("admin giveaway list","dydiscord.admin.giveaway.list",false,0,"giveaway-list")
    );
    static Definition d(String path,String permission,boolean player,int arguments,String key){return new Definition(path,permission,player,arguments,false,key);}
    public static boolean adminVisible(Predicate<String> allowed){return ALL.stream().anyMatch(d->d.path.startsWith("admin ")&&allowed.test(d.permission));}
    public static List<String> tabs(String[] args,String lang,boolean player,Predicate<String> allowed){
        if(args.length==0)return List.of();int pos=args.length-1;Set<String> out=new LinkedHashSet<>();
        for(Definition d:ALL){if(!allowed.test(d.permission)||d.player&&!player)continue;String[] words=d.words();boolean match=true;for(int i=0;i<pos;i++){if(i>=words.length||!Words.matches(words[i],args[i])){match=false;break;}}if(!match)continue;
            if(pos<words.length)out.add(Words.display(words[pos],lang));else if(pos==words.length&&(d.key.equals("language")||d.key.equals("global-language")||d.key.equals("bot-language")))out.addAll(List.of("en","tr","auto"));else if(pos==words.length&&Set.of("chat-inbound","chat-to-minecraft","chat-to-discord","chat-both").contains(d.key))out.addAll(List.of(Words.display("on",lang),Words.display("off",lang)));
        }
        if(pos==0){if(adminVisible(allowed))out.add(Words.display("admin",lang));if(allowed.test("dydiscord.admin.invite"))out.add(Words.display("setinvite",lang));}
        return out.stream().filter(v->Words.plain(v).startsWith(Words.plain(args[pos]))).toList();
    }
}
