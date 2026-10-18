package dev.dydiscord.language;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.Configuration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.*;

/** Localized diagnostics accept only known filenames, setting paths and event IDs. */
public final class ConsoleDiagnostics {
    private ConsoleDiagnostics(){}
    private static final Pattern FILE=Pattern.compile("(?:(?:discord/)?messages_(?:en|tr)|config|bot)\\.yml");
    private static final Pattern SETTING=Pattern.compile("Invalid (setting|message|file): ([a-zA-Z0-9_./:-]+)");
    private static final class Bundled {
        static final Map<String,Map<String,Object>> TEXT=load();
        static Map<String,Map<String,Object>> load(){
            Map<String,Map<String,Object>> packs=new HashMap<>();
            for(String lang:List.of("en","tr"))try(InputStream in=ConsoleDiagnostics.class.getClassLoader().getResourceAsStream("messages_"+lang+".yml")){
                packs.put(lang,Configuration.parse(new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8)));
            }catch(IOException failure){throw new IllegalStateException("Bundled diagnostics unavailable",failure);}
            return Map.copyOf(packs);
        }
    }
    /** Called on the I/O executor before parsing; never changes or validates owner files. */
    public static String detectLanguage(Path folder){
        String global=scalar(read(folder.resolve("config.yml")),"^lang"),bot=scalar(read(folder.resolve("bot.yml")),"^  language");
        return Set.of("en","tr").contains(global)?global:Set.of("en","tr").contains(bot)?bot:"en";
    }
    private static String read(Path file){try{return Files.size(file)<=262144?Files.readString(file):"";}catch(IOException ignored){return "";}}
    private static String scalar(String text,String key){Matcher match=Pattern.compile("(?m)"+key+":\\s*[\"']?(en|tr|auto)[\"']?\\s*(?:#.*)?$").matcher(text);return match.find()?match.group(1):"auto";}
    public static String language(DyDiscord plugin){
        try{var settings=plugin.settings();if(settings!=null)return Messages.language(settings.language(),"auto",settings.botLanguage());}catch(RuntimeException ignored){}
        return "tr".equals(plugin.diagnosticLanguage())?"tr":"en";
    }
    public static String text(DyDiscord plugin,String key,Map<String,String> values){
        Messages messages=null;try{messages=plugin.messages();}catch(RuntimeException ignored){}
        return text(messages,language(plugin),key,values);
    }
    public static String text(Messages messages,String language,String key,Map<String,String> values){
        String lang="tr".equals(language)?"tr":"en";
        String template=messages==null?null:messages.get(false,lang,key);
        if(template==null||template.equals(key))template=String.valueOf(Bundled.TEXT.get(lang).getOrDefault(key,key));
        Matcher slots=Pattern.compile("\\{([a-z_]+)}").matcher(template);StringBuffer out=new StringBuffer();
        while(slots.find())slots.appendReplacement(out,Matcher.quoteReplacement(Messages.safeMinecraft(values.getOrDefault(slots.group(1),slots.group()))));
        slots.appendTail(out);return out.toString().replaceAll("(?i)[&§][0-9a-fk-or]","");
    }
    public static String reason(DyDiscord plugin,Throwable failure){
        List<Throwable> chain=new ArrayList<>();for(Throwable at=failure;at!=null&&chain.size()<16&&!chain.contains(at);at=at.getCause())chain.add(at);
        String file="";for(Throwable at:chain){String message=at.getMessage();
            if(at instanceof Configuration.FileFailure||message!=null&&message.startsWith("Invalid file: ")){Matcher match=FILE.matcher(message==null?"":message);file=match.find()?match.group():"configuration";break;}
        }
        if(!file.isEmpty()){
            String position="";for(Throwable at:chain)if(at instanceof Configuration.SyntaxFailure syntax&&syntax.line()>0){position=text(plugin,"error-config-position",Map.of("line",Integer.toString(syntax.line()),"column",Integer.toString(syntax.column())));break;}
            return text(plugin,"error-config-file",Map.of("file",file,"position",position));
        }
        for(Throwable at:chain){String message=at.getMessage();Matcher setting=SETTING.matcher(message==null?"":message);
            if(setting.matches())return text(plugin,"error-config-setting",Map.of("setting",setting.group(2)));
            if(message!=null&&message.startsWith("Unmanaged slash name collision"))return text(plugin,"error-command-collision",Map.of());
            if(at instanceof TimeoutException||at instanceof java.net.SocketTimeoutException)return text(plugin,"error-timeout",Map.of());
        }
        return text(plugin,"error-unexpected",Map.of());
    }
}
