package dev.dydiscord.command;
import java.util.*;
import java.util.function.Predicate;
public record Definition(String path,String permission,boolean player,int arguments,boolean remainder,String key) {
    public String[] words(){return path.isEmpty()?new String[0]:path.split(" ");}
    public String usage(){return path+(arguments==0?"":switch(key){case "giveaway-start"->" <seconds> <winners> <prize>";case "announce-set"->" <template> <text>";case "announce-send","announce-preview"->" <template>";case "chat-pause","chat-resume"->" <inbound|outbound|both>";case "chat-inbound","chat-outbound","chat-notify"->" <on|off>";case "announcements-pause"->" <minutes>";case "rewards-preview"->" <uuid>";case "invite"->" <url>";case "language","global-language"->" <en|tr|auto>";case "admin-unlink"->" <uuid> <reason>";case "roles-groups-add"->" <group> <id> <priority>";case "roles-groups-remove"->" <group> <id>";case "roles-preview","roles-sync"->" <uuid>";case "roles-cleanup-confirm"->" <token>";default->" <id>";});}
    public boolean match(String[] args){String[] words=words();if(args.length!=words.length+arguments&&!(remainder&&args.length>=words.length+arguments))return false;for(int i=0;i<words.length;i++)if(!Words.matches(words[i],args[i]))return false;return true;}
    public static final List<Definition> ALL=List.of(
        d("","dydiscord.invite",false,0,"invite-show"),d("help","dydiscord.help",false,0,"help"),d("link","dydiscord.link",true,0,"link"),d("status","dydiscord.status",true,0,"status"),d("unlink","dydiscord.unlink",true,0,"unlink"),d("language","dydiscord.language",true,1,"language"),
        d("setinvite","dydiscord.admin.invite",false,1,"invite"),
        d("admin invite","dydiscord.admin.invite",false,1,"invite"),d("admin language","dydiscord.admin.language",false,1,"global-language"),d("admin reload","dydiscord.admin.reload",false,0,"reload"),d("admin status","dydiscord.admin.status",false,0,"admin-status"),d("admin doctor","dydiscord.admin.doctor",false,0,"doctor"),
        d("admin bot enable","dydiscord.admin.bot",false,0,"bot-enable"),d("admin bot disable","dydiscord.admin.bot",false,0,"bot-disable"),d("admin bot restart","dydiscord.admin.bot",false,0,"bot-restart"),
        d("admin panel channel","dydiscord.admin.panel.channel",false,1,"panel-channel"),d("admin panel send","dydiscord.admin.panel.send",false,0,"panel-send"),d("admin panel refresh","dydiscord.admin.panel.refresh",false,0,"panel-refresh"),
        d("admin panel enable","dydiscord.admin.panel.toggle",false,0,"panel-enable"),d("admin panel disable","dydiscord.admin.panel.toggle",false,0,"panel-disable"),
        d("admin console channel","dydiscord.admin.console.channel",false,1,"console-channel"),d("admin console enable","dydiscord.admin.console.toggle",false,0,"console-enable"),d("admin console disable","dydiscord.admin.console.toggle",false,0,"console-disable"),
        d("admin console commands enable","dydiscord.admin.console.commands",false,0,"commands-enable"),d("admin console commands disable","dydiscord.admin.console.commands",false,0,"commands-disable"),
        d("admin console role add","dydiscord.admin.console.roles",false,1,"role-add"),d("admin console role remove","dydiscord.admin.console.roles",false,1,"role-remove"),d("admin console role list","dydiscord.admin.console.roles",false,0,"role-list"),
        d("admin console administrator on","dydiscord.admin.console.administrator",false,0,"administrator-on"),d("admin console administrator off","dydiscord.admin.console.administrator",false,0,"administrator-off"),
        new Definition("admin unlink","dydiscord.admin.unlink",false,2,true,"admin-unlink"),
        d("admin roles status","dydiscord.admin.roles.status",false,0,"roles-status"),
        d("admin roles linked list","dydiscord.admin.roles.linked.list",false,0,"roles-linked-list"),
        d("admin roles linked add","dydiscord.admin.roles.linked.add",false,1,"roles-linked-add"),
        d("admin roles linked remove","dydiscord.admin.roles.linked.remove",false,1,"roles-linked-remove"),
        d("admin roles groups list","dydiscord.admin.roles.groups.list",false,0,"roles-groups-list"),
        d("admin roles groups add","dydiscord.admin.roles.groups.add",false,3,"roles-groups-add"),
        d("admin roles groups remove","dydiscord.admin.roles.groups.remove",false,2,"roles-groups-remove"),
        d("admin roles preview","dydiscord.admin.roles.preview",false,1,"roles-preview"),
        d("admin roles sync all","dydiscord.admin.roles.sync.all",false,0,"roles-sync-all"),
        d("admin roles sync","dydiscord.admin.roles.sync",false,1,"roles-sync"),
        d("admin roles cleanup preview","dydiscord.admin.roles.cleanup.preview",false,0,"roles-cleanup-preview"),
        d("admin roles cleanup confirm","dydiscord.admin.roles.cleanup.confirm",false,1,"roles-cleanup-confirm"),
        d("chat inbound","dydiscord.chat.preferences",true,1,"chat-inbound"),
        d("chat outbound","dydiscord.chat.preferences",true,1,"chat-outbound"),
        d("chat notify","dydiscord.chat.preferences",true,1,"chat-notify"),
        d("admin chat pause","dydiscord.admin.chat.pause",false,1,"chat-pause"),
        d("admin chat resume","dydiscord.admin.chat.pause",false,1,"chat-resume"),
        d("admin announce preview","dydiscord.admin.announce.preview",false,1,"announce-preview"),
        d("admin announce send","dydiscord.admin.announce.send",false,1,"announce-send"),
        new Definition("admin announce set","dydiscord.admin.announce.edit",false,2,true,"announce-set"),
        d("admin announcements pause","dydiscord.admin.announcements.pause",false,1,"announcements-pause"),
        d("admin announcements resume","dydiscord.admin.announcements.pause",false,0,"announcements-resume"),
        d("admin maintenance","dydiscord.admin.announce.send",false,0,"maintenance"),
        d("admin restartnotice","dydiscord.admin.announce.send",false,0,"restart-notice"),
        d("admin report","dydiscord.admin.report",false,0,"support-report"),
        d("admin config validate","dydiscord.admin.config.validate",false,0,"config-validate"),
        d("admin translations validate","dydiscord.admin.translations.validate",false,0,"translations-validate"),
        d("admin setup","dydiscord.admin.setup",false,0,"setup"),
        d("admin rewards preview","dydiscord.admin.rewards.preview",false,1,"rewards-preview"),
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
            if(pos<words.length)out.add(Words.display(words[pos],lang));else if(pos==words.length&&(d.key.equals("language")||d.key.equals("global-language")))out.addAll(List.of("en","tr","auto"));else if(pos==words.length&&Set.of("chat-inbound","chat-outbound","chat-notify").contains(d.key))out.addAll(List.of(Words.display("on",lang),Words.display("off",lang)));else if(pos==words.length&&Set.of("chat-pause","chat-resume").contains(d.key))for(String direction:List.of("inbound","outbound","both"))out.add(Words.display(direction,lang));
        }
        if(pos==0){if(adminVisible(allowed))out.add(Words.display("admin",lang));if(allowed.test("dydiscord.admin.invite"))out.add(Words.display("setinvite",lang));}
        return out.stream().filter(v->Words.plain(v).startsWith(Words.plain(args[pos]))).toList();
    }
}
