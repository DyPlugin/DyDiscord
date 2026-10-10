package dev.dydiscord.console;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.io.*;
import java.util.*;

public final class Audit {
    private final Path folder;private final Clock clock;
    public Audit(Path folder,Clock clock)throws IOException{this.folder=folder;this.clock=clock;Files.createDirectories(folder);}
    public synchronized void write(String actor,String action,String target,String result)throws IOException{
        Instant now=clock.instant();Path file=folder.resolve("audit-"+now.toString().substring(0,10)+".log");
        byte[] line=(now+" actor="+safe(actor)+" action="+safe(action)+" target="+safe(target)+" result="+safe(result)+"\n").getBytes(StandardCharsets.UTF_8);
        if(Files.exists(file)&&Files.size(file)+line.length>20L*1024*1024)throw new IOException("Audit capacity reached");
        try(FileChannel out=FileChannel.open(file,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND)){var buffer=java.nio.ByteBuffer.wrap(line);while(buffer.hasRemaining())out.write(buffer);out.force(true);}
        List<Path> files;try(var stream=Files.list(folder)){files=stream.filter(p->p.getFileName().toString().matches("audit-[0-9-]+\\.log")).sorted().toList();}
        long total=0;for(Path p:files)total+=Files.size(p);
        for(Path p:files){if(p.equals(file))continue;boolean old=Files.getLastModifiedTime(p).toInstant().isBefore(now.minus(Duration.ofDays(30)));if(old||total>20L*1024*1024){long size=Files.size(p);Files.delete(p);total-=size;}}
        if(Files.size(file)>20L*1024*1024)throw new IOException("Audit capacity reached");
    }
    private static String safe(String s){if(s==null)return "-";String text=s.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]"," ");int end=Math.min(256,text.length());if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;return text.substring(0,end);}
}
