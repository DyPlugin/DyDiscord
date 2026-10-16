package dev.dydiscord.command;

import dev.dydiscord.DyDiscord;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class Router implements CommandExecutor,TabCompleter {
    private final DyDiscord plugin;private final boolean shortcut;
    public Router(DyDiscord plugin,boolean shortcut){this.plugin=plugin;this.shortcut=shortcut;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] raw){
        String[] args=shortcut?prepend(raw):raw;
        if(!plugin.ready()){plugin.message(sender,"busy",Map.of());return true;}
        if(args.length==1&&Words.matches("cancel",args[0])){plugin.cancel(sender);return true;}
        if(args.length==2&&Words.matches("confirm",args[0])){plugin.confirm(sender,args[1]);return true;}
        if((args.length==1&&Words.matches("admin",args[0]))||(args.length==2&&Words.matches("admin",args[0])&&Words.matches("help",args[1]))){
            if(!Definition.adminVisible(sender::hasPermission))plugin.message(sender,"no-permission",Map.of());else help(sender,true);return true;
        }
        Optional<Definition> found=Definition.ALL.stream().filter(d->d.match(args)).findFirst();
        if(found.isEmpty()){plugin.message(sender,"usage",Map.of());return true;}
        Definition d=found.get();if(!sender.hasPermission(d.permission())){plugin.message(sender,"no-permission",Map.of());return true;}
        if(d.player()&&!(sender instanceof Player)){plugin.message(sender,"player-only",Map.of());return true;}
        if(d.key().equals("help")){help(sender,false);return true;}
        plugin.execute(sender,d,Arrays.copyOfRange(args,d.words().length,args.length));return true;
    }
    private void help(CommandSender sender,boolean admin){
        String lang=plugin.language(sender);plugin.helpHeader(sender);
        for(Definition d:Definition.ALL){if(d.path().equals("setinvite"))continue;if(d.path().startsWith("admin ")!=admin||!sender.hasPermission(d.permission()))continue;
            String root="/dydiscord"+(d.path().isEmpty()?"":" ");String playerOnly=d.player()&&!(sender instanceof Player)?" ("+plugin.messages().get(false,lang,"help-player-only")+")":"";sender.sendMessage("§6"+root+Words.displayPath(d.usage(),lang)+" §7- "+plugin.messages().get(false,lang,"help-"+d.key())+playerOnly);
        }
        if(!admin&&(sender.hasPermission("dydiscord.unlink")||sender.hasPermission("dydiscord.admin.unlink"))){for(String word:List.of("confirm","cancel"))sender.sendMessage("§6/dydiscord "+Words.display(word,lang)+(word.equals("confirm")?(lang.equals("tr")?" <kod>":" <token>"):"")+" §7- "+plugin.messages().get(false,lang,"help-"+word));}
        if(!admin&&Definition.adminVisible(sender::hasPermission))sender.sendMessage("§6/dydiscord "+Words.display("admin",lang)+" "+Words.display("help",lang)+" §7- "+plugin.messages().get(false,lang,"help-admin"));
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(shortcut||!plugin.ready())return List.of();List<String> result=new ArrayList<>(Definition.tabs(args,plugin.language(sender),sender instanceof Player,sender::hasPermission));
        if(args.length==1){if(sender.hasPermission("dydiscord.unlink")||sender.hasPermission("dydiscord.admin.unlink")){for(String word:List.of("confirm","cancel")){String shown=Words.display(word,plugin.language(sender));if(Words.plain(shown).startsWith(Words.plain(args[0])))result.add(shown);}}}
        for(Definition definition:Definition.ALL){if(!Set.of("announce-send","announce-preview","announce-set").contains(definition.key())||!sender.hasPermission(definition.permission())||args.length!=definition.words().length+1)continue;boolean matches=true;for(int i=0;i<definition.words().length;i++)if(!Words.matches(definition.words()[i],args[i]))matches=false;if(matches)for(String name:plugin.settings().tools().templates().keySet())if(Words.plain(name).startsWith(Words.plain(args[args.length-1])))result.add(name);}
        return List.copyOf(new LinkedHashSet<>(result));
    }
    private static String[] prepend(String[] raw){String[] out=new String[raw.length+1];out[0]="link";System.arraycopy(raw,0,out,1,raw.length);return out;}
}
