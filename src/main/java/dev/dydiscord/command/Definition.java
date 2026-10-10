package dev.dydiscord.command;
import java.util.*;
import java.util.function.Predicate;
public record Definition(String path,String permission,boolean player,int arguments,boolean remainder,String key) {
    public String[] words(){return path.isEmpty()?new String[0]:path.split(" ");}
    public String usage(){return path+(arguments==0?"":switch(key){case "invite"->" <url>";case "language","global-language"->" <en|tr|auto>";case "admin-unlink"->" <uuid> <reason>";default->" <id>";});}
    public boolean match(String[] args){String[] words=words();if(args.length!=words.length+arguments&&!(remainder&&args.length>=words.length+arguments))return false;for(int i=0;i<words.length;i++)if(!Words.matches(words[i],args[i]))return false;return true;}
    public static final List<Definition> ALL=List.of(
        d("","dydiscord.invite",false,0,"invite-show"),d("help","dydiscord.help",false,0,"help"),d("link","dydiscord.link",true,0,"link"),d("status","dydiscord.status",true,0,"status"),d("unlink","dydiscord.unlink",true,0,"unlink"),d("language","dydiscord.language",true,1,"language"),
        d("setinvite","dydiscord.admin.invite",false,1,"invite"),
        d("admin invite","dydiscord.admin.invite",false,1,"invite"),d("admin language","dydiscord.admin.language",false,1,"global-language"),d("admin reload","dydiscord.admin.reload",false,0,"reload"),d("admin status","dydiscord.admin.status",false,0,"admin-status"),d("admin doctor","dydiscord.admin.doctor",false,0,"doctor"),
        d("admin bot enable","dydiscord.admin.bot",false,0,"bot-enable"),d("admin bot disable","dydiscord.admin.bot",false,0,"bot-disable"),d("admin bot restart","dydiscord.admin.bot",false,0,"bot-restart"),
        d("admin panel channel","dydiscord.admin.panel.channel",false,1,"panel-channel"),d("admin panel send","dydiscord.admin.panel.send",false,0,"panel-send"),d("admin panel refresh","dydiscord.admin.panel.refresh",false,0,"panel-refresh"),
        d("admin console channel","dydiscord.admin.console.channel",false,1,"console-channel"),d("admin console enable","dydiscord.admin.console.toggle",false,0,"console-enable"),d("admin console disable","dydiscord.admin.console.toggle",false,0,"console-disable"),
        d("admin console commands enable","dydiscord.admin.console.commands",false,0,"commands-enable"),d("admin console commands disable","dydiscord.admin.console.commands",false,0,"commands-disable"),
        d("admin console role add","dydiscord.admin.console.roles",false,1,"role-add"),d("admin console role remove","dydiscord.admin.console.roles",false,1,"role-remove"),d("admin console role list","dydiscord.admin.console.roles",false,0,"role-list"),
        d("admin console administrator on","dydiscord.admin.console.administrator",false,0,"administrator-on"),d("admin console administrator off","dydiscord.admin.console.administrator",false,0,"administrator-off"),
        new Definition("admin unlink","dydiscord.admin.unlink",false,2,true,"admin-unlink")
    );
    static Definition d(String path,String permission,boolean player,int arguments,String key){return new Definition(path,permission,player,arguments,false,key);}
    public static boolean adminVisible(Predicate<String> allowed){return ALL.stream().anyMatch(d->d.path.startsWith("admin ")&&allowed.test(d.permission));}
    public static List<String> tabs(String[] args,String lang,boolean player,Predicate<String> allowed){
        if(args.length==0)return List.of();int pos=args.length-1;Set<String> out=new LinkedHashSet<>();
        for(Definition d:ALL){if(!allowed.test(d.permission)||d.player&&!player)continue;String[] words=d.words();boolean match=true;for(int i=0;i<pos;i++){if(i>=words.length||!Words.matches(words[i],args[i])){match=false;break;}}if(!match)continue;
            if(pos<words.length)out.add(Words.display(words[pos],lang));else if(pos==words.length&&(d.key.equals("language")||d.key.equals("global-language")))out.addAll(List.of("en","tr","auto"));
        }
        if(pos==0){if(adminVisible(allowed))out.add(Words.display("admin",lang));if(allowed.test("dydiscord.admin.invite"))out.add(Words.display("setinvite",lang));}
        return out.stream().filter(v->Words.plain(v).startsWith(Words.plain(args[pos]))).toList();
    }
}
