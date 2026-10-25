package dev.dydiscord.discord;

import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.MessageEmbed;
import java.util.*;

/**
 * Every Discord message except relayed chat is sent as a Components V2 container. Cards are still
 * built as embeds where that is convenient and tested, then turned into containers here.
 */
public final class Cards {
    public static final int BLURPLE=0x5865F2,CONSOLE=0x4F545C;
    /** Discord allows 4000 characters of text per V2 message; stay safely below it. */
    private static final int LIMIT=3900;
    private Cards(){}
    /** A plain text card, for short private replies. */
    public static Container text(String body){return text(body,BLURPLE);}
    public static Container text(String body,int color){return Container.of(TextDisplay.of(bound(body.isBlank()?"​":body,LIMIT))).withAccentColor(color);}
    /** Server output keeps its code block inside a neutral card. */
    public static Container console(String output){return text(output,CONSOLE);}
    /** Title and description, then fields (inline fields share a line), then footer and timestamp. */
    public static Container of(MessageEmbed embed){
        List<ContainerChildComponent> parts=new ArrayList<>();int budget=LIMIT;
        StringBuilder head=new StringBuilder();
        if(embed.getTitle()!=null&&!embed.getTitle().isBlank())head.append("### ").append(embed.getTitle());
        if(embed.getDescription()!=null&&!embed.getDescription().isBlank()){if(!head.isEmpty())head.append('\n');head.append(embed.getDescription());}
        StringBuilder fields=new StringBuilder();List<String> inline=new ArrayList<>();
        for(MessageEmbed.Field field:embed.getFields()){
            String name=Objects.toString(field.getName(),""),value=Objects.toString(field.getValue(),"");
            if(field.isInline()&&!value.contains("\n")){inline.add("**"+name+":** "+value);continue;}
            flush(fields,inline);if(!fields.isEmpty())fields.append("\n\n");fields.append("**").append(name).append("**\n").append(value);
        }
        flush(fields,inline);
        StringBuilder foot=new StringBuilder();
        if(embed.getFooter()!=null&&embed.getFooter().getText()!=null&&!embed.getFooter().getText().isBlank())foot.append("-# ").append(embed.getFooter().getText());
        if(embed.getTimestamp()!=null){if(!foot.isEmpty())foot.append('\n');foot.append("-# <t:").append(embed.getTimestamp().toEpochSecond()).append(":R>");}
        for(StringBuilder block:List.of(head,fields,foot)){
            if(block.isEmpty()||budget<=0)continue;
            if(!parts.isEmpty())parts.add(Separator.createDivider(Separator.Spacing.SMALL));
            String text=bound(block.toString(),budget);budget-=text.length();parts.add(TextDisplay.of(text));
        }
        if(parts.isEmpty())parts.add(TextDisplay.of("​"));
        int color=embed.getColorRaw();
        return Container.of(parts).withAccentColor(color==net.dv8tion.jda.api.entities.Role.DEFAULT_COLOR_RAW?BLURPLE:color&0xFFFFFF);
    }
    private static void flush(StringBuilder fields,List<String> inline){if(inline.isEmpty())return;if(!fields.isEmpty())fields.append("\n\n");fields.append(String.join(" · ",inline));inline.clear();}
    /** Cuts at a line break where possible and never inside a Markdown escape. */
    static String bound(String text,int limit){
        if(text.length()<=limit)return text;String cut=text.substring(0,Math.max(0,limit-1));int line=cut.lastIndexOf('\n');if(line>limit/2)cut=cut.substring(0,line);
        while(cut.endsWith("\\"))cut=cut.substring(0,cut.length()-1);return cut+"…";
    }
}
