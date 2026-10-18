package dev.dydiscord.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Adds descriptions with source edits, leaving scalar spelling and owner comments intact. */
final class YamlComments {
    private static final String MARKER="# Setting descriptions follow their settings.";
    private record Setting(String path, int line, int after, int indent) {}
    static String below(String source,String defaults)throws IOException {
        source=blockMappings(source);
        List<String> template=lines(defaults), input=lines(source);
        List<Setting> bundled=settings(defaults), current=settings(source);
        boolean below=template.contains(MARKER);
        Map<String,List<String>> descriptions=new LinkedHashMap<>();
        Set<String> packaged=new HashSet<>(List.of(
            "Trusted Discord role IDs as quoted strings; role names are not authorization.",
            "Quoted Discord role IDs given to verified linked accounts.",
            "Up to 10 commands, each at most 256 characters. Optional leading / is removed.",
            "Provider commands run when a recorded boost ends or its link is removed; {player}/{uuid} target the linked account.",
            "Up to ten trusted console commands; {player}/{uuid}, max 256 characters each.",
            "Optional OR rule: a member must hold at least one listed role ID. Max twenty.",
            "Random code length (8-16); existing explicit values are preserved on upgrade.",
            "auto uses each player's preference/client language; en or tr forces one language.",
            "HTTPS discord.gg or discord.com/invite link; blank disables the invitation.",
            "Discord language and message commands are independent from Minecraft language.",
            "Bot/panel language: en or tr. Restart the bot after changing this."));
        try(InputStream in=YamlComments.class.getResourceAsStream("/yaml-comment-history.txt")){
            if(in!=null)packaged.addAll(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF", "").lines().toList());
        }
        for(Setting setting:bundled){
            List<String> comments=new ArrayList<>();
            if(below){for(int i=setting.after+1;i<template.size()&&comment(template.get(i));i++)comments.add(body(template.get(i)));}
            else {for(int i=setting.line-1;i>=0&&comment(template.get(i));i--)comments.add(0,body(template.get(i)));}
            comments.removeIf(s->s.equals(MARKER.substring(2)));
            if(comments.isEmpty())comments.add(description(setting.path));
            // One concise line directly below each setting; owner comments are retained separately.
            String description=String.join(" ",comments);
            if(description.length()>220)description=comments.get(0).length()>220?description(setting.path):comments.get(0);
            descriptions.put(setting.path,List.of(description));
            packaged.addAll(comments);packaged.add(String.join(" ",comments));
        }
        Map<Integer,List<String>> insertions=new HashMap<>();
        for(Setting setting:current){
            if(!descriptions.containsKey(setting.path))continue;
            List<String> docs=descriptions.get(setting.path);
            for(String doc:docs)insertions.computeIfAbsent(setting.after,k->new ArrayList<>()).add(" ".repeat(setting.indent)+"# "+doc);
            packaged.addAll(docs);
        }
        Set<Integer> literalLines=new HashSet<>();
        protectScalars(compose(source),literalLines,Collections.newSetFromMap(new IdentityHashMap<>()));
        List<String> result=new ArrayList<>();
        if(!input.contains(MARKER))result.add(MARKER);
        for(int i=0;i<input.size();i++){
            String line=input.get(i);
            if(literalLines.contains(i)||!comment(line)||line.equals(MARKER)||!packaged.contains(body(line)))result.add(line);
            if(insertions.containsKey(i))result.addAll(insertions.get(i));
        }
        String rewritten=String.join(source.contains("\r\n")?"\r\n":"\n",result)+ (source.endsWith("\n")?source.contains("\r\n")?"\r\n":"\n":"");
        if(!Configuration.parse(source).equals(Configuration.parse(rewritten)))throw new IOException("Comment migration changed YAML values");
        return rewritten;
    }
    /** Flow maps cannot hold one description after each key without splitting scalar lines. */
    private static String blockMappings(String source)throws IOException{
        var loader=Configuration.options();loader.setProcessComments(true);
        var dumper=new org.yaml.snakeyaml.DumperOptions();dumper.setProcessComments(true);dumper.setWidth(4096);dumper.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        var yaml=new Yaml(new SafeConstructor(loader),new org.yaml.snakeyaml.representer.Representer(dumper),dumper,loader);
        Node node=yaml.compose(new StringReader(source));
        if(!block(node,Collections.newSetFromMap(new IdentityHashMap<>())))return source;
        StringWriter writer=new StringWriter();yaml.serialize(node,writer);String result=writer.toString();
        if(source.contains("\r\n"))result=result.replace("\n","\r\n");
        if(!Configuration.parse(source).equals(Configuration.parse(result)))throw new IOException("Comment migration changed YAML values");return result;
    }
    private static boolean block(Node node,Set<Node> seen){
        if(node==null||!seen.add(node))return false;boolean changed=false;
        if(node instanceof MappingNode map){if(!map.getValue().isEmpty()&&map.getFlowStyle()==org.yaml.snakeyaml.DumperOptions.FlowStyle.FLOW){map.setFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);changed=true;}for(var tuple:map.getValue())changed|=block(tuple.getValueNode(),seen);}
        else if(node instanceof SequenceNode list){for(Node item:list.getValue())changed|=block(item,seen);if(changed)list.setFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);}
        return changed;
    }
    private static String description(String path){
        String key=path.substring(path.lastIndexOf('.')+1).replace('-',' ');
        return "Text shown for "+key+".";
    }
    private static List<String> lines(String text){List<String> result=new ArrayList<>(Arrays.asList(text.replace("\r\n","\n").split("\n",-1)));if(text.endsWith("\n"))result.remove(result.size()-1);return result;}
    private static boolean comment(String line){return line.stripLeading().startsWith("#");}
    private static String body(String line){return line.stripLeading().substring(1).strip();}
    private static Node compose(String text)throws IOException {try{return new Yaml(new SafeConstructor(Configuration.options())).compose(new StringReader(text));}catch(org.yaml.snakeyaml.error.YAMLException e){throw new IOException("Malformed YAML (values withheld)",e);}}
    private static List<Setting> settings(String text)throws IOException {List<Setting> result=new ArrayList<>();collect(compose(text),"",result,Collections.newSetFromMap(new IdentityHashMap<>()));return result;}
    private static void collect(Node node,String parent,List<Setting> result,Set<Node> visited){
        if(!(node instanceof MappingNode map)||!visited.add(node))return;
        for(NodeTuple tuple:map.getValue())if(tuple.getKeyNode() instanceof ScalarNode key){
            String path=parent.isEmpty()?key.getValue():parent+"."+key.getValue();Node value=tuple.getValueNode();
            int after=key.getStartMark().getLine();
            if(value instanceof ScalarNode&&value.getEndMark().getLine()>after)after=value.getEndMark().getLine()-(value.getEndMark().getColumn()==0?1:0);
            result.add(new Setting(path,key.getStartMark().getLine(),after,key.getStartMark().getColumn()));collect(value,path,result,visited);
        }
    }
    private static void protectScalars(Node node,Set<Integer> lines,Set<Node> visited){
        if(node==null||!visited.add(node))return;
        if(node instanceof ScalarNode scalar){for(int i=scalar.getStartMark().getLine()+1;i<=scalar.getEndMark().getLine()-(scalar.getEndMark().getColumn()==0?1:0);i++)lines.add(i);}
        else if(node instanceof MappingNode map)for(NodeTuple tuple:map.getValue()){protectScalars(tuple.getKeyNode(),lines,visited);protectScalars(tuple.getValueNode(),lines,visited);}
        else if(node instanceof SequenceNode sequence)for(Node child:sequence.getValue())protectScalars(child,lines,visited);
    }
    /** Developer utility: converts the six public templates without touching their values. */
    public static void main(String[] args)throws IOException {for(String file:args){Path path=Path.of(file);String before=Files.readString(path);String after=below(before,before);if(!Configuration.parse(before).equals(Configuration.parse(after)))throw new IOException("Comment conversion changed YAML values");Files.writeString(path,after);}}
}
