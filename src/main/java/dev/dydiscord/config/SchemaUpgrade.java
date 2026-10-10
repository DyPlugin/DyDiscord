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
        merge(current,target,settings);
        StringWriter out=new StringWriter();yaml.serialize(current,out);return out.toString();
    }
    private static void merge(MappingNode current,MappingNode defaults,boolean fill){
        for(NodeTuple entry:defaults.getValue()){
            ScalarNode key=(ScalarNode)entry.getKeyNode();NodeTuple existing=current.getValue().stream().filter(t->t.getKeyNode() instanceof ScalarNode s&&s.getValue().equals(key.getValue())).findFirst().orElse(null);
            if(existing==null){if(fill||key.getValue().startsWith("state-")||key.getValue().startsWith("language-name-")||key.getValue().equals("update-available")||key.getValue().startsWith("chat-")||key.getValue().equals("server-stopping"))current.getValue().add(entry);continue;}
            comments(existing.getKeyNode(),key);comments(existing.getValueNode(),entry.getValueNode());
            if(key.getValue().equals("config-version")&&fill||key.getValue().equals("prefix")&&fill&&existing.getValueNode() instanceof ScalarNode s&&s.getValue().equals("&7[&dDyDiscord&7]")){
                Node value=entry.getValueNode();value.setInLineComments(existing.getValueNode().getInLineComments());value.setBlockComments(existing.getValueNode().getBlockComments());current.getValue().set(current.getValue().indexOf(existing),new NodeTuple(existing.getKeyNode(),value));continue;}
            if(existing.getValueNode() instanceof MappingNode nested&&entry.getValueNode() instanceof MappingNode template)merge(nested,template,fill);
        }
    }
    private static void comments(Node node,Node defaults){
        List<CommentLine> added=defaults.getBlockComments();if(added==null||added.isEmpty())return;
        List<CommentLine> existing=new ArrayList<>(node.getBlockComments()==null?List.of():node.getBlockComments());
        for(CommentLine line:added)if(existing.stream().noneMatch(c->c.getValue().equals(line.getValue())))existing.add(line);
        node.setBlockComments(existing);
    }
}
