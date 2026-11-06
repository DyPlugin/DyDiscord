package dev.dydiscord.chat;

import dev.dydiscord.language.Messages;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import java.util.*;
import java.util.regex.*;

/** Format the trusted template once. Values cannot create markup, colors or new lines. */
public final class ChatText {
    private static final Pattern SLOT=Pattern.compile("\\{([a-z_]+)}");
    // Compiled once: these run for every relayed chat line, event and rank lookup. Same passes, same order as before.
    private static final Pattern HEX_X=Pattern.compile("(?i)[&§]x(?:[&§][0-9a-f]){6}"),HEX=Pattern.compile("(?i)[&§]#[0-9a-f]{6}"),CODE=Pattern.compile("(?i)[&§][0-9a-fk-or]"),CONTROL=Pattern.compile("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]"),SPACE=Pattern.compile("\\s+"),
        MARKDOWN=Pattern.compile("([#>+!()\\-])"),NEWLINES=Pattern.compile("[\\r\\n]"),DISCORD_WORD=Pattern.compile("(?i)discord"),CLYDE=Pattern.compile("(?i)clyde");
    /** Files and stickers relayed from Discord; only Discord's own HTTPS media hosts are ever made clickable. */
    public record Attachment(String kind,String name,String url) {}
    public static final int MAX_ATTACHMENTS=5;
    private static final Set<String> MEDIA_HOSTS=Set.of("cdn.discordapp.com","media.discordapp.net");
    public static String plain(String text,int limit){
        if(text==null)return "";String value=SPACE.matcher(CONTROL.matcher(CODE.matcher(HEX.matcher(HEX_X.matcher(text).replaceAll("")).replaceAll("")).replaceAll("")).replaceAll(" ").replace('§',' ')).replaceAll(" ").trim();
        if(limit<=0)return "";
        if(value.codePointCount(0,value.length())>limit){String cut=value.substring(0,value.offsetByCodePoints(0,limit-1));int space=cut.lastIndexOf(' ');if(space>=Math.max(1,cut.length()/3))cut=cut.substring(0,space);value=cut.stripTrailing()+"…";}return value;
    }
    public static String discord(String template,String rank,String name,String message){
        String prefix=plain(rank,64);Map<String,String> values=Map.of("rank",prefix.isEmpty()?"":"**"+markdown(prefix)+"** ","name",markdown(plain(name,64)),"message",markdown(plain(message,800)));
        return substitute(template,values);
    }
    private static String markdown(String value){return MARKDOWN.matcher(Messages.safeDiscord(value)).replaceAll("\\\\$1");}
    public static String event(String template,String name,String death){return substitute(template,Map.of("name",markdown(plain(name,64)),"death",markdown(plain(death,1200))));}
    public static String advancement(String template,String name,String title){return substitute(template,Map.of("name",markdown(plain(name,64)),"advancement",markdown(plain(title,128))));}
    /** Webhook display name: rank and player name, 1-80 characters; Discord rejects names containing "discord" or "clyde". */
    public static String webhookName(String rank,String name){
        String prefix=plain(rank,32),value=plain((prefix.isEmpty()?"":prefix+" ")+name,80);
        return CLYDE.matcher(DISCORD_WORD.matcher(value).replaceAll("Dc")).replaceAll("Cly");
    }
    public static String webhookBody(String message){return markdown(plain(message,800));}
    public static String avatar(String template,UUID id,String name){return template.replace("{uuid}",id.toString()).replace("{name}",java.net.URLEncoder.encode(plain(name,64),java.nio.charset.StandardCharsets.UTF_8).replace("+","%20"));}
    public static String substitute(String template,Map<String,String> values){Matcher m=SLOT.matcher(template);StringBuffer out=new StringBuffer();while(m.find())m.appendReplacement(out,Matcher.quoteReplacement(values.getOrDefault(m.group(1),"")));m.appendTail(out);return out.toString();}
    public static String reply(String author,String message){String name=identity(author);return name.isEmpty()?body(message):plain("↪ "+name+" — "+body(message),800);}
    private static String body(String value){return plain(MarkdownSanitizer.sanitize(plain(value,800)),800);}
    private static String identity(String value){return plain(value,64).replace("**","");}
    public static boolean names(String message,String name){return name!=null&&!name.isBlank()&&Pattern.compile("(?i)(?<![a-z0-9_])"+Pattern.quote(name)+"(?![a-z0-9_])").matcher(message).find();}
    public static TextComponent minecraft(String template,String role,int color,String name,String message){
        return minecraft(template,role,color,name,message,"");
    }
    public static TextComponent minecraft(String template,String role,int color,String name,String message,String replyAuthor){
        Map<String,String> values=Map.of("role",identity(role),"name",identity(name),"message",reply(replyAuthor,message));TextComponent result=new TextComponent();Matcher slots=SLOT.matcher(template);int end=0;ChatColor current=ChatColor.WHITE;
        while(slots.find()){
            String literal=NEWLINES.matcher(template.substring(end,slots.start())).replaceAll(" ");BaseComponent[] parts=TextComponent.fromLegacyText(dev.dydiscord.util.Colors.color(literal));for(BaseComponent part:parts){part.setBold(false);result.addExtra(part);current=part.getColor();}
            String value=values.getOrDefault(slots.group(1),"");if(slots.group(1).equals("role")&&!value.isEmpty())value+=" ";TextComponent part=new TextComponent(value);part.setBold(false);part.setColor(slots.group(1).equals("role")?readableColor(color):current);result.addExtra(part);end=slots.end();
        }
        for(BaseComponent part:TextComponent.fromLegacyText(dev.dydiscord.util.Colors.color(template.substring(end)))){part.setBold(false);result.addExtra(part);}return result;
    }
    public static ChatColor readableColor(int rgb){return rgb==0?ChatColor.GRAY:ChatColor.of(new java.awt.Color(rgb&0xffffff));}
    /** True only for HTTPS links on Discord's attachment and sticker hosts, without credentials or custom ports. */
    public static boolean discordMedia(String url){
        if(url==null||url.length()>2048||url.chars().anyMatch(c->c<=' '))return false;
        try{java.net.URI uri=new java.net.URI(url);return "https".equals(uri.getScheme())&&uri.getHost()!=null&&MEDIA_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))&&uri.getRawUserInfo()==null&&uri.getPort()==-1;}catch(java.net.URISyntaxException invalid){return false;}
    }
    /**
     * Appends one clickable label per attachment, such as [image], after the chat line. {@code labels} gives the
     * trusted template text for a kind (image, file or sticker); the hover text names the file, as plain text.
     */
    public static TextComponent attachments(TextComponent line,List<Attachment> items,java.util.function.Function<String,String> labels,String hover){
        int shown=0;
        for(Attachment item:items){
            if(shown>=MAX_ATTACHMENTS||!discordMedia(item.url()))continue;
            TextComponent link=new TextComponent();for(BaseComponent part:TextComponent.fromLegacyText(dev.dydiscord.util.Colors.color(NEWLINES.matcher(labels.apply(item.kind())).replaceAll(" "))))link.addExtra(part);
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,item.url()));
            link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,new net.md_5.bungee.api.chat.hover.content.Text(substitute(dev.dydiscord.util.Colors.color(hover),Map.of("name",plain(item.name(),64))))));
            line.addExtra(new TextComponent(" "));line.addExtra(link);shown++;
        }
        return line;
    }
}
