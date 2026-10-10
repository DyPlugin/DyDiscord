package dev.dydiscord.chat;

import dev.dydiscord.language.Messages;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import java.util.*;
import java.util.regex.*;

/** Format the trusted template once. Values cannot create markup, colors or new lines. */
public final class ChatText {
    private static final Pattern SLOT=Pattern.compile("\\{([a-z_]+)}");
    public static String plain(String text,int limit){
        if(text==null)return "";String value=text.replaceAll("(?i)[&§]x(?:[&§][0-9a-f]){6}","").replaceAll("(?i)[&§]#[0-9a-f]{6}","").replaceAll("(?i)[&§][0-9a-fk-or]","").replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]"," ").replace('§',' ').replaceAll("\\s+"," ").trim();
        if(value.codePointCount(0,value.length())>limit)value=value.substring(0,value.offsetByCodePoints(0,limit))+"…";return value;
    }
    public static String discord(String template,String rank,String name,String message){
        String prefix=plain(rank,64);Map<String,String> values=Map.of("rank",prefix.isEmpty()?"":"**"+markdown(prefix)+"** ","name",markdown(plain(name,64)),"message",markdown(plain(message,800)));
        return substitute(template,values);
    }
    private static String markdown(String value){return Messages.safeDiscord(value).replaceAll("([#>+!()\\-])","\\\\$1");}
    public static String event(String template,String name,String death){return substitute(template,Map.of("name",markdown(plain(name,64)),"death",markdown(plain(death,1200))));}
    public static String substitute(String template,Map<String,String> values){Matcher m=SLOT.matcher(template);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(values.getOrDefault(m.group(1),"")));m.appendTail(out);return out.toString();}
    public static TextComponent minecraft(String template,String role,int color,String name,String message){
        Map<String,String> values=Map.of("role",plain(role,64).replaceAll("[*_`~|]",""),"name",plain(name,64).replaceAll("[*_`~|]",""),"message",plain(message,800).replaceAll("[*_`~|]",""));TextComponent result=new TextComponent();Matcher slots=SLOT.matcher(template);int end=0;ChatColor current=ChatColor.WHITE;
        while(slots.find()){
            String literal=template.substring(end,slots.start()).replaceAll("[\\r\\n]"," ");BaseComponent[] parts=TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&',literal));for(BaseComponent part:parts){part.setBold(false);result.addExtra(part);current=part.getColor();}
            String value=values.getOrDefault(slots.group(1),"");if(slots.group(1).equals("role")&&!value.isEmpty())value+=" ";TextComponent part=new TextComponent(value);part.setBold(false);part.setColor(slots.group(1).equals("role")?readableColor(color):current);result.addExtra(part);end=slots.end();
        }
        for(BaseComponent part:TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&',template.substring(end)))){part.setBold(false);result.addExtra(part);}return result;
    }
    public static ChatColor readableColor(int rgb){int brightness=(((rgb>>16)&255)*299+((rgb>>8)&255)*587+(rgb&255)*114)/1000;return rgb==0||brightness<96?ChatColor.GRAY:ChatColor.of(new java.awt.Color(rgb));}
}
