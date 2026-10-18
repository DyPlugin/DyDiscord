package dev.dydiscord.config;

import dev.dydiscord.language.Messages;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

/** All parsing and file operations run on the plugin's bounded I/O executor. */
public final class Configuration {
    /** Only bundled filenames and fixed operation labels reach the public diagnostic. */
    public static final class FileFailure extends IOException {
        public FileFailure(String file,IOException cause){super("Cannot load or migrate "+safeFile(file)+position(cause)+"; check YAML syntax, file access and available disk space.",cause);}
        private static String safeFile(String file){return file!=null&&file.matches("(?:(?:discord/)?messages_(?:en|tr)|config|bot)\\.yml")?file:"configuration file";}
        private static String position(Throwable cause){for(int depth=0;cause!=null&&depth<16;depth++,cause=cause.getCause()){if(cause instanceof SyntaxFailure syntax&&syntax.line()>0)return " at line "+syntax.line()+", column "+syntax.column();if(cause instanceof org.yaml.snakeyaml.error.MarkedYAMLException marked&&marked.getProblemMark()!=null){var mark=marked.getProblemMark();return " at line "+(mark.getLine()+1)+", column "+(mark.getColumn()+1);}}return "";}
    }
    /** Retain a safe location, never the YAML line/token contained in parser exceptions. */
    public static final class SyntaxFailure extends IOException {
        private final int line,column;
        private SyntaxFailure(int line,int column){super("Malformed YAML (values withheld)");this.line=line;this.column=column;}
        public int line(){return line;}public int column(){return column;}
    }
    public record Snapshot(Settings settings, Messages messages,Map<String,String> migrations,Map<String,String> sources) {
        public Snapshot(Settings settings,Messages messages){this(settings,messages,Map.of());}
        public Snapshot(Settings settings,Messages messages,Map<String,String> migrations){this(settings,messages,migrations,Map.of());}
        public Snapshot{messages.validatePanel(settings.serverName());migrations=Map.copyOf(migrations);sources=Map.copyOf(sources);}
        @Override public String toString(){return "Snapshot["+settings+", pendingFiles="+migrations.keySet()+"]";}
    }
    private final Path folder;
    private volatile Snapshot active;
    public Configuration(Path folder){this.folder=folder;}
    public Snapshot active(){return active;}
    public synchronized void apply(Snapshot snapshot)throws IOException{
        requireSources(snapshot.sources());
        Map<Path,String> originals=new LinkedHashMap<>(),changes=new LinkedHashMap<>();
        for(var migration:snapshot.migrations().entrySet()){
            Path target=folder.resolve(migration.getKey());String before=Files.readString(target);
            if(!digest(before).equals(migration.getValue()))throw new IOException("Configuration changed during validation; retry reload");
            String after=upgrade(migration.getKey(),before);
            if(!before.equals(after)){originals.put(target,before);changes.put(target,after);}
        }
        // Backup every affected file before the first write. Roll back partial writes on failure.
        if(!changes.isEmpty()){
            for(Path target:changes.keySet())try{backup(target,originals.get(target));}catch(IOException failure){throw new FileFailure(folder.relativize(target).toString().replace('\\','/'),failure);}
            List<Path> written=new ArrayList<>();
            try{for(var change:changes.entrySet()){try{atomicWrite(change.getKey(),change.getValue());}catch(IOException failure){throw new FileFailure(folder.relativize(change.getKey()).toString().replace('\\','/'),failure);}written.add(change.getKey());}}
            catch(IOException failure){for(Path target:written)try{atomicWrite(target,originals.get(target));}catch(IOException rollback){failure.addSuppressed(rollback);}throw failure;}
        }
        active=snapshot;
    }
    private static void backup(Path target,String original)throws IOException{
        Path backup=target.resolveSibling(target.getFileName()+".backup-"+digest(original));
        try{Files.copy(target,backup);}
        catch(FileAlreadyExistsException exists){
            if(!Files.readString(backup).equals(original))throw new IOException("Configuration backup differs from the original; migration stopped");
        }
    }
    public Path folder(){return folder;}
    public synchronized Snapshot load() throws IOException {
        Snapshot next=preview(); apply(next); return next;
    }
    public Snapshot preview() throws IOException {
        for(String file:List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml")) {
            Path target=folder.resolve(file);try{if(!Files.exists(target)){Files.createDirectories(target.getParent());try(InputStream in=getClass().getClassLoader().getResourceAsStream(file)){if(in==null)throw new IOException("Missing bundled resource: "+file);Files.copy(in,target);}}}catch(IOException failure){throw new FileFailure(file,failure);}
        }
        Map<String,String> changes=new LinkedHashMap<>(),sources=new LinkedHashMap<>();Map<String,Object> config=null,bot=null;
        for(String file:List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml")){
            String before,after;try{before=Files.readString(folder.resolve(file));after=upgrade(file,before);}catch(IOException failure){throw new FileFailure(file,failure);}sources.put(file,digest(before));if(!before.equals(after))changes.put(file,digest(before));
            if(file.equals("config.yml"))config=parse(after);if(file.equals("bot.yml"))bot=parse(after);
        }
        return new Snapshot(Settings.read(config,bot,System.getenv("DYDISCORD_BOT_TOKEN")),Messages.load(folder),changes,sources);
    }
    private Map<String,String> sources()throws IOException{Map<String,String> result=new LinkedHashMap<>();for(String file:List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml"))result.put(file,digest(Files.readString(folder.resolve(file))));return result;}
    private void requireSources(Map<String,String> sources)throws IOException{for(var entry:sources.entrySet())if(!digest(Files.readString(folder.resolve(entry.getKey()))).equals(entry.getValue()))throw new IOException("Configuration changed during validation; retry reload");}
    private static String upgrade(String file,String text)throws IOException{try(InputStream in=Configuration.class.getClassLoader().getResourceAsStream(file)){if(in==null)throw new IOException("Missing bundled resource");return SchemaUpgrade.merge(text,new String(in.readAllBytes(),StandardCharsets.UTF_8),file.equals("config.yml")||file.equals("bot.yml"));}}
    private static String digest(String text){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    public interface Validator {void validate(Settings settings)throws Exception;}
    /** Compare under the same lock as edits, including un-reloaded owner changes on disk. */
    public synchronized boolean inviteIfMissing(String guild,String url,Validator validator)throws IOException{
        Map<String,String> capturedSources=sources();
        Map<String,Object> config=read(folder.resolve("config.yml")),bot=read(folder.resolve("bot.yml"));
        if(!"".equals(Settings.get(config,"discord.invite-url",""))||!guild.equals(Settings.get(bot,"guild-id","")))return false;
        Settings.validateInvite(url);changeFrom("config.yml","discord.invite-url",url,validator,capturedSources);return true;
    }
    public synchronized Snapshot change(String file,String path,Object value) throws IOException {return change(file,path,value,null);}
    public synchronized Snapshot change(String file,String path,Object value,Validator validator) throws IOException {
        return changeFrom(file,path,value,validator,sources());
    }
    public synchronized Snapshot change(String file,Map<String,Object> changes,Validator validator)throws IOException {
        return changeFrom(file,changes,validator,sources());
    }
    private Snapshot changeFrom(String file,String path,Object value,Validator validator,Map<String,String> sources)throws IOException{
        return changeFrom(file,Collections.singletonMap(path,value),validator,sources);
    }
    private Snapshot changeFrom(String file,Map<String,Object> changes,Validator validator,Map<String,String> sources)throws IOException{
        Path target=folder.resolve(file); String before=Files.readString(target);
        String after=before;
        for(var change:changes.entrySet())after=edit(after,change.getKey(),change.getValue());
        after=upgrade(file,after);
        Map<String,Object> c=read(folder.resolve("config.yml")), b=read(folder.resolve("bot.yml"));
        Map<String,Object> edited=parse(after); if(file.equals("config.yml"))c=edited;else b=edited;
        Snapshot next=new Snapshot(Settings.read(c,b,System.getenv("DYDISCORD_BOT_TOKEN")),Messages.load(folder));
        if(validator!=null)try{validator.validate(next.settings());}catch(Exception e){throw new IOException("Discord configuration validation failed; previous settings remain active",e);}
        requireSources(sources);try{atomicWrite(target,after);}catch(IOException failure){throw new FileFailure(file,failure);} active=next; return next;
    }
    private static String edit(String before,String path,Object value)throws IOException{
        LoaderOptions loader=options(); loader.setProcessComments(true);
        DumperOptions dumper=new DumperOptions(); dumper.setProcessComments(true); dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml=new Yaml(new SafeConstructor(loader),new org.yaml.snakeyaml.representer.Representer(dumper),dumper,loader);
        Node root=yaml.compose(new StringReader(before)); if(!(root instanceof MappingNode mapping))throw new IOException("Expected YAML mapping");
        putNode(mapping,path.split("\\."),0,yaml.represent(value));
        StringWriter writer=new StringWriter(); yaml.serialize(root,writer);return writer.toString();
    }
    private static void putNode(MappingNode node,String[] keys,int i,Node value){
        for(int j=0;j<node.getValue().size();j++){NodeTuple tuple=node.getValue().get(j);if(tuple.getKeyNode() instanceof ScalarNode s&&s.getValue().equals(keys[i])){
            if(i==keys.length-1){value.setBlockComments(tuple.getValueNode().getBlockComments());value.setInLineComments(tuple.getValueNode().getInLineComments());node.getValue().set(j,new NodeTuple(tuple.getKeyNode(),value));return;}
            if(!(tuple.getValueNode() instanceof MappingNode child))throw Settings.invalid(String.join(".",keys));putNode(child,keys,i+1,value);return;
        }}
        Node key=new ScalarNode(Tag.STR,keys[i],null,null,DumperOptions.ScalarStyle.PLAIN);
        if(i==keys.length-1)node.getValue().add(new NodeTuple(key,value));
        else {MappingNode child=new MappingNode(Tag.MAP,new ArrayList<>(),DumperOptions.FlowStyle.BLOCK);node.getValue().add(new NodeTuple(key,child));putNode(child,keys,i+1,value);}
    }
    public static void atomicWrite(Path target,String text)throws IOException{
        Path temp=Files.createTempFile(target.getParent(),".dydiscord-",".tmp");
        try {try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){var bytes=StandardCharsets.UTF_8.encode(text);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
    public static LoaderOptions options(){LoaderOptions o=new LoaderOptions();o.setAllowDuplicateKeys(false);o.setMaxAliasesForCollections(20);o.setCodePointLimit(262144);o.setNestingDepthLimit(20);return o;}
    public static Map<String,Object> read(Path file)throws IOException {try{return parse(Files.readString(file));}catch(IOException e){throw new IOException("Invalid file: "+file.getFileName(),e);}}
    @SuppressWarnings("unchecked") public static Map<String,Object> parse(String text)throws IOException{
        try{Object data=new Yaml(new SafeConstructor(options())).load(text);if(!(data instanceof Map<?,?>))throw new IOException("Expected YAML mapping");return (Map<String,Object>)data;}
        catch(org.yaml.snakeyaml.error.YAMLException e){if(e instanceof org.yaml.snakeyaml.error.MarkedYAMLException marked&&marked.getProblemMark()!=null){var mark=marked.getProblemMark();throw new SyntaxFailure(mark.getLine()+1,mark.getColumn()+1);}throw new SyntaxFailure(0,0);}
    }
}
