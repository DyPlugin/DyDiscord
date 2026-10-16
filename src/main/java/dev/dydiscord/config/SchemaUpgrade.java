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
        if(!merge(current,target,settings))return before;
        StringWriter out=new StringWriter();yaml.serialize(current,out);return out.toString();
    }
    private static boolean merge(MappingNode current,MappingNode defaults,boolean fill){
        boolean changed=false;
        for(NodeTuple entry:defaults.getValue()){
            ScalarNode key=(ScalarNode)entry.getKeyNode();NodeTuple existing=current.getValue().stream().filter(t->t.getKeyNode() instanceof ScalarNode s&&s.getValue().equals(key.getValue())).findFirst().orElse(null);
            if(existing==null){if(fill||dev.dydiscord.language.Messages.releaseKey(key.getValue())||key.getValue().startsWith("state-")||key.getValue().startsWith("language-name-")||key.getValue().equals("update-available")||key.getValue().startsWith("chat-")||key.getValue().equals("server-stopping")||key.getValue().startsWith("server-info-")||key.getValue().equals("slash-server")||key.getValue().equals("help-server")||Set.of("panel-disabled","help-panel-enable","help-panel-disable","commands-prompt","commands-help").contains(key.getValue())){current.getValue().add(entry);changed=true;}continue;}
            changed|=comments(existing.getKeyNode(),key);changed|=comments(existing.getValueNode(),entry.getValueNode());
            boolean upgradeVersion=fill&&key.getValue().equals("config-version")&&existing.getValueNode() instanceof ScalarNode version&&entry.getValueNode() instanceof ScalarNode targetVersion&&!version.getValue().equals(targetVersion.getValue());
            boolean upgradePrefix=fill&&key.getValue().equals("prefix")&&existing.getValueNode() instanceof ScalarNode s&&s.getValue().equals("&7[&dDyDiscord&7]");
            if(upgradeVersion||upgradePrefix){
                Node value=entry.getValueNode();value.setInLineComments(existing.getValueNode().getInLineComments());value.setBlockComments(existing.getValueNode().getBlockComments());current.getValue().set(current.getValue().indexOf(existing),new NodeTuple(existing.getKeyNode(),value));changed=true;continue;}
            if(existing.getValueNode() instanceof MappingNode nested&&entry.getValueNode() instanceof MappingNode template)changed|=merge(nested,template,fill);
        }
        return changed;
    }
    private static boolean comments(Node node,Node defaults){
        List<CommentLine> added=defaults.getBlockComments();if(added==null||added.isEmpty())return false;
        List<CommentLine> existing=new ArrayList<>(node.getBlockComments()==null?List.of():node.getBlockComments());
        int originalSize=existing.size();
        for(CommentLine line:added)if(existing.stream().noneMatch(c->c.getValue().equals(line.getValue())))existing.add(line);
        if(existing.size()==originalSize)return false;
        node.setBlockComments(existing);return true;
    }
}
