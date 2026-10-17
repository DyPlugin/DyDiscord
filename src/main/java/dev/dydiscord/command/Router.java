package dev.dydiscord.command;

import dev.dydiscord.DyDiscord;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class Router implements CommandExecutor,TabCompleter {
    private final DyDiscord plugin;private final boolean shortcut;private final boolean adminShortcut;
    public Router(DyDiscord plugin,boolean shortcut){this(plugin,shortcut,false);}
    public Router(DyDiscord plugin,boolean shortcut,boolean adminShortcut){this.plugin=plugin;this.shortcut=shortcut;this.adminShortcut=adminShortcut;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] raw){
        String[] args=shortcut?prepend(raw,"link"):adminShortcut?prepend(raw,"admin"):raw;
        if(!plugin.ready()){plugin.message(sender,"busy",Map.of());return true;}
        if(args.length==1&&Words.matches("cancel",args[0])){plugin.cancel(sender);return true;}
        if(args.length==2&&Words.matches("confirm",args[0])){plugin.confirm(sender,args[1]);return true;}
        if(args.length>=1&&Words.matches("admin",args[0])&&(args.length==1||Words.matches("help",args[1]))){
            if(!Definition.adminVisible(sender::hasPermission)){plugin.message(sender,"no-permission",Map.of());return true;}
            int page=1;try{if(args.length>3)throw new NumberFormatException();if(args.length==3){if(!args[2].matches("[1-9][0-9]{0,4}"))throw new NumberFormatException();page=Integer.parseInt(args[2]);}}catch(NumberFormatException invalid){plugin.message(sender,"help-page-invalid",Map.of());return true;}
            help(sender,true,page);return true;
        }
        Optional<Definition> found=Definition.ALL.stream().filter(d->d.match(args)).findFirst();
        if(found.isEmpty()){plugin.message(sender,"usage",Map.of());return true;}
        Definition d=found.get();if(!sender.hasPermission(d.permission())){plugin.message(sender,"no-permission",Map.of());return true;}
        if(d.player()&&!(sender instanceof Player)){plugin.message(sender,"player-only",Map.of());return true;}
        if(d.key().equals("help")){help(sender,false,1);return true;}
        plugin.execute(sender,d,Arrays.copyOfRange(args,d.words().length,args.length));return true;
    }
    private void help(CommandSender sender,boolean admin,int page){
        String lang=plugin.language(sender);
        List<Definition> visible=Definition.ALL.stream().filter(d->!d.path().equals("setinvite")&&!d.key().equals("help")&&d.path().startsWith("admin ")==admin&&sender.hasPermission(d.permission())).toList();
        int pages=admin?Math.max(1,(visible.size()+7)/8):1;if(page>pages){plugin.message(sender,"help-page-invalid",Map.of());return;}
        plugin.helpHeader(sender);
        for(Definition d:admin?visible.subList((page-1)*8,Math.min(page*8,visible.size())):visible){
            String root="/dydiscord"+(d.path().isEmpty()?"":" ");String playerOnly=d.player()&&!(sender instanceof Player)?" ("+plugin.messages().get(false,lang,"help-player-only")+")":"";sender.sendMessage("§6"+root+Words.displayPath(d.usage(),lang)+" §7- "+plugin.messages().get(false,lang,"help-"+d.key())+playerOnly);
        }
        if(admin)sender.sendMessage(plugin.messages().render(false,lang,"help-page",Map.of("page",Integer.toString(page),"pages",Integer.toString(pages),"command","/dydiscord admin "+Words.display("help",lang))));
        if(!admin&&(sender.hasPermission("dydiscord.unlink")||sender.hasPermission("dydiscord.admin.unlink"))){for(String word:List.of("confirm","cancel"))sender.sendMessage("§6/dydiscord "+Words.display(word,lang)+(word.equals("confirm")?(lang.equals("tr")?" <kod>":" <token>"):"")+" §7- "+plugin.messages().get(false,lang,"help-"+word));}
        if(!admin&&Definition.adminVisible(sender::hasPermission))sender.sendMessage("§6/dydiscord "+Words.display("admin",lang)+" "+Words.display("help",lang)+" §7- "+plugin.messages().get(false,lang,"help-admin"));
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] raw){
        String[] args=adminShortcut?prepend(raw,"admin"):raw;
        if(shortcut||!plugin.ready())return List.of();List<String> result=new ArrayList<>(Definition.tabs(args,plugin.language(sender),sender instanceof Player,sender::hasPermission));
        if(args.length==3&&Words.matches("admin",args[0])&&Words.matches("help",args[1])&&Definition.adminVisible(sender::hasPermission)){
            long total=Definition.ALL.stream().filter(d->d.path().startsWith("admin ")&&sender.hasPermission(d.permission())).count();for(int i=1;i<=Math.max(1,(total+7)/8);i++)if(Integer.toString(i).startsWith(args[2]))result.add(Integer.toString(i));
        }
        if(args.length==2&&Words.matches("admin",args[0])&&Definition.adminVisible(sender::hasPermission)&&Words.plain(Words.display("help",plugin.language(sender))).startsWith(Words.plain(args[1])))result.add(Words.display("help",plugin.language(sender)));
        if(args.length==1){if(sender.hasPermission("dydiscord.unlink")||sender.hasPermission("dydiscord.admin.unlink")){for(String word:List.of("confirm","cancel")){String shown=Words.display(word,plugin.language(sender));if(Words.plain(shown).startsWith(Words.plain(args[0])))result.add(shown);}}}
        return List.copyOf(new LinkedHashSet<>(result));
    }
    private static String[] prepend(String[] raw,String word){String[] out=new String[raw.length+1];out[0]=word;System.arraycopy(raw,0,out,1,raw.length);return out;}
}
