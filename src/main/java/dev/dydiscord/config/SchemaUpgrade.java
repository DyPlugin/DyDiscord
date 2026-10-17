package dev.dydiscord.config;

import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.comments.CommentLine;
import java.io.*;
import java.util.*;

/** Merge supported schemas without resetting values, unknown keys or owner comments. */
final class SchemaUpgrade {
    static String merge(String before,String defaults,boolean settings)throws IOException {
        LoaderOptions loader=Configuration.options();loader.setProcessComments(true);
        DumperOptions dumper=new DumperOptions();dumper.setProcessComments(true);dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml=new Yaml(new SafeConstructor(loader),new org.yaml.snakeyaml.representer.Representer(dumper),dumper,loader);
        Node old,template;try{old=yaml.compose(new StringReader(before));template=yaml.compose(new StringReader(defaults));}catch(org.yaml.snakeyaml.error.YAMLException failure){throw new IOException("Malformed YAML (values withheld)",failure);}
        if(!(old instanceof MappingNode current)||!(template instanceof MappingNode target))throw new IOException("Expected YAML mapping");
        if(settings){Object version=Configuration.parse(before).getOrDefault("config-version",1);if(!(version instanceof Number n)||n.doubleValue()!=n.intValue()||n.intValue()<1||n.intValue()>2)throw Settings.invalid("config-version");}
        if(settings)markShippedEight(current,before);
        boolean changed=removeCommunityTools(current,settings);
        changed|=merge(current,target,settings);
        if(!changed)return YamlComments.below(before,defaults);
        StringWriter out=new StringWriter();yaml.serialize(current,out);return YamlComments.below(out.toString(),defaults);
    }
    private static boolean merge(MappingNode current,MappingNode defaults,boolean fill){
        boolean changed=false;
        for(NodeTuple entry:defaults.getValue()){
            ScalarNode key=(ScalarNode)entry.getKeyNode();NodeTuple existing=current.getValue().stream().filter(t->t.getKeyNode() instanceof ScalarNode s&&s.getValue().equals(key.getValue())).findFirst().orElse(null);
            if(existing==null){if(fill||dev.dydiscord.language.Messages.releaseKey(key.getValue())||key.getValue().startsWith("state-")||key.getValue().startsWith("language-name-")||key.getValue().equals("update-available")||key.getValue().startsWith("chat-")||key.getValue().equals("server-stopping")||key.getValue().startsWith("server-info-")||key.getValue().equals("slash-server")||key.getValue().equals("help-server")||Set.of("panel-disabled","help-panel-enable","help-panel-disable","commands-prompt","commands-help").contains(key.getValue())){current.getValue().add(entry);changed=true;}continue;}
            boolean shippedEight=key.getValue().equals("code-length")&&(shippedCodeComment(existing.getKeyNode())||shippedCodeComment(existing.getValueNode()));
            boolean upgradeVersion=fill&&key.getValue().equals("config-version")&&existing.getValueNode() instanceof ScalarNode version&&entry.getValueNode() instanceof ScalarNode targetVersion&&!version.getValue().equals(targetVersion.getValue());
            boolean upgradePrefix=fill&&key.getValue().equals("prefix")&&existing.getValueNode() instanceof ScalarNode s&&s.getValue().equals("&7[&dDyDiscord&7]");
            boolean shortenShippedCode=fill&&shippedEight&&existing.getValueNode() instanceof ScalarNode length&&length.getValue().equals("8")&&entry.getValueNode() instanceof ScalarNode nextLength&&nextLength.getValue().equals("6");
            if(upgradeVersion||upgradePrefix||shortenShippedCode){
                Node value=entry.getValueNode();value.setInLineComments(existing.getValueNode().getInLineComments());value.setBlockComments(existing.getValueNode().getBlockComments());current.getValue().set(current.getValue().indexOf(existing),new NodeTuple(existing.getKeyNode(),value));changed=true;continue;}
            if(existing.getValueNode() instanceof MappingNode nested&&entry.getValueNode() instanceof MappingNode template)changed|=merge(nested,template,fill);
        }
        return changed;
    }
    private static void markShippedEight(MappingNode root,String source){
        String[] lines=source.split("\\R");
        String marker="# Random code length (8-16); existing explicit values are preserved on upgrade.";
        for(NodeTuple entry:root.getValue())if(entry.getKeyNode() instanceof ScalarNode section&&section.getValue().equals("linking")&&entry.getValueNode() instanceof MappingNode linking)
            for(NodeTuple setting:linking.getValue())if(setting.getKeyNode() instanceof ScalarNode key&&key.getValue().equals("code-length")){
                int line=key.getStartMark().getLine();
                if(line+1<lines.length&&lines[line+1].strip().equals(marker)&&!shippedCodeComment(key)){
                    List<CommentLine> comments=new ArrayList<>(key.getBlockComments()==null?List.of():key.getBlockComments());
                    comments.add(new CommentLine(null,null,marker.substring(1),org.yaml.snakeyaml.comments.CommentType.BLOCK));key.setBlockComments(comments);
                }
            }
    }
    private static boolean removeCommunityTools(MappingNode root,boolean settings){
        if(!settings)return false;
        for(NodeTuple tuple:new ArrayList<>(root.getValue()))if(tuple.getKeyNode() instanceof ScalarNode key&&key.getValue().equals("community-tools")){
            List<CommentLine> retained=new ArrayList<>(root.getEndComments()==null?List.of():root.getEndComments());
            retainComments(tuple.getKeyNode(),retained,Collections.newSetFromMap(new IdentityHashMap<>()));
            retainComments(tuple.getValueNode(),retained,Collections.newSetFromMap(new IdentityHashMap<>()));
            root.setEndComments(retained);root.getValue().remove(tuple);return true;
        }
        return false;
    }
    private static void retainComments(Node node,List<CommentLine> output,Set<Node> visited){
        if(!visited.add(node))return;
        for(List<CommentLine> comments:Arrays.asList(node.getBlockComments(),node.getInLineComments(),node.getEndComments()))if(comments!=null)
            for(CommentLine line:comments)output.add(new CommentLine(line.getStartMark(),line.getEndMark(),line.getValue(),org.yaml.snakeyaml.comments.CommentType.BLOCK));
        if(node instanceof MappingNode map)for(NodeTuple entry:map.getValue()){retainComments(entry.getKeyNode(),output,visited);retainComments(entry.getValueNode(),output,visited);}
        else if(node instanceof SequenceNode sequence)for(Node child:sequence.getValue())retainComments(child,output,visited);
    }
    private static boolean shippedCodeComment(Node node){return java.util.stream.Stream.of(node.getBlockComments(),node.getInLineComments(),node.getEndComments()).filter(Objects::nonNull).flatMap(Collection::stream).anyMatch(line->line.getValue().strip().equals("Random code length (8-16); existing explicit values are preserved on upgrade."));}
}
