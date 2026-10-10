package dev.dydiscord.console;
import java.util.*;
import java.util.regex.*;

/** Appender enqueue never performs disk or network I/O. UTF-16 pairs remain intact. */
public final class LogBuffer {
    private final Deque<String> lines=new ArrayDeque<>();private int bytes;private long dropped;private volatile String token="";
    public void token(String token){this.token=token;}
    public String sanitize(String text){
        String t=text.replaceAll("\u001b\\[[0-9;]*[a-zA-Z]","").replaceAll("[§&][0-9a-fk-orA-FK-OR]","");
        if(!token.isEmpty())t=t.replace(token,"[REDACTED]");
        t=t.replaceAll("(?i)(authorization\\s*[:=]\\s*)([^\\r\\n]+)","$1[REDACTED]")
            .replaceAll("\\b[A-Za-z0-9_-]{20,}\\.[A-Za-z0-9_-]{6,}\\.[A-Za-z0-9_-]{20,}\\b","[REDACTED]")
            .replaceAll("(?i)(https://[^\\s]+/(?:webhooks|interactions)/[0-9]+/)[^\\s]+","$1[REDACTED]")
            .replaceAll("(?i)(token\\s*[:=]\\s*)[^\\s]+","$1[REDACTED]")
            .replaceAll("\\b[A-HJ-NP-Z2-9]{8,16}\\b","[REDACTED]");
        return t.replace("`","ˋ").replace("@","@\u200b").replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","");
    }
    public synchronized void offer(String text){
        String safe=sanitize(text);if(safe.length()>16384)safe=safe.substring(0,Character.isHighSurrogate(safe.charAt(16383))?16383:16384);
        for(String line:safe.split("\n",-1))for(String part:split(line,1500)){
            int size=part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            while(!lines.isEmpty()&&(lines.size()>=2000||bytes+size>1024*1024)){String removed=lines.removeFirst();bytes-=removed.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;dropped++;}
            lines.addLast(part);bytes+=size;
        }
    }
    public synchronized String drain(){StringBuilder out=new StringBuilder();if(dropped>0){out.append("[DyDiscord: ").append(dropped).append(" lines dropped]\n");dropped=0;}
        while(!lines.isEmpty()&&out.length()+lines.peekFirst().length()+1<=1900){String line=lines.removeFirst();bytes-=line.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;out.append(line).append('\n');}return out.isEmpty()?"":"```text\n"+out+"```";
    }
    public synchronized int size(){return lines.size();}
    public synchronized void clear(){lines.clear();bytes=0;dropped=0;}
    public static List<String> split(String line,int limit){List<String> parts=new ArrayList<>();if(line.isEmpty())return List.of("");for(int i=0;i<line.length();){int end=Math.min(i+limit,line.length());if(end<line.length()&&Character.isHighSurrogate(line.charAt(end-1)))end--;parts.add(line.substring(i,end));i=end;}return parts;}
}
