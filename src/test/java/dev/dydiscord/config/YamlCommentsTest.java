package dev.dydiscord.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YamlCommentsTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder;
    @Test void multiSettingEditValidatesTogetherAndWritesOnceOrLeavesBothUnchanged() throws Exception {
        Configuration config=dev.dydiscord.TestSupport.config(folder);
        String before=java.nio.file.Files.readString(folder.resolve("bot.yml"));
        java.util.Map<String,Object> both=java.util.Map.of("chat.minecraft-to-discord",true,"chat.discord-to-minecraft",true);
        assertThrows(java.io.IOException.class,()->config.change("bot.yml",both,settings->{throw new IllegalArgumentException("blocked");}));
        assertEquals(before,java.nio.file.Files.readString(folder.resolve("bot.yml")));
        java.util.concurrent.atomic.AtomicInteger validations=new java.util.concurrent.atomic.AtomicInteger();
        config.change("bot.yml",both,settings->{validations.incrementAndGet();});
        assertEquals(1,validations.get());
        var chat=(java.util.Map<?,?>)Configuration.read(folder.resolve("bot.yml")).get("chat");
        assertEquals(true,chat.get("minecraft-to-discord"));assertEquals(true,chat.get("discord-to-minecraft"));
        String after=java.nio.file.Files.readString(folder.resolve("bot.yml"));config.load();
        assertEquals(after,java.nio.file.Files.readString(folder.resolve("bot.yml")));
    }
    @Test void publicTemplatesDescribeEverySettingAndRemainStableAcrossReloads() throws Exception {
        for(String file:java.util.List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml")){
            String text;
            try(var in=getClass().getResourceAsStream("/"+file)){assertNotNull(in);text=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
            assertEquals(text,YamlComments.below(text,text),file);
            var root=new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(Configuration.options())).compose(new java.io.StringReader(text));
            assertDescriptions(root,text.split("\\R"),file);
        }
        Configuration config=dev.dydiscord.TestSupport.config(folder);
        java.util.Map<String,String> first=new java.util.LinkedHashMap<>();
        for(String file:java.util.List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml"))first.put(file,java.nio.file.Files.readString(folder.resolve(file)));
        config.load();
        for(var entry:first.entrySet())assertEquals(entry.getValue(),java.nio.file.Files.readString(folder.resolve(entry.getKey())),entry.getKey());
    }
    private static void assertDescriptions(org.yaml.snakeyaml.nodes.Node node,String[] lines,String file){
        if(!(node instanceof org.yaml.snakeyaml.nodes.MappingNode map))return;
        for(var tuple:map.getValue()){
            int line=tuple.getKeyNode().getStartMark().getLine();
            assertTrue(line+1<lines.length&&lines[line+1].stripLeading().startsWith("# "),file+":"+(line+1));
            assertDescriptions(tuple.getValueNode(),lines,file);
        }
    }
    @Test void flowMappingsWithWrappedQuotedTextRemainReadableAndIdempotent()throws Exception{
        String source="{one: 'first line\n    second line', two: value}\n# owner note\n";
        String defaults="# First text.\none: default\n# Second text.\ntwo: default\n";
        String after=YamlComments.below(source,defaults);assertEquals(Configuration.parse(source),Configuration.parse(after));assertTrue(after.contains("owner note"));assertEquals(after,YamlComments.below(after,defaults));
    }
    @Test void movesPackagedCommentsBelowAndPreservesOwnerTextAndValues() throws Exception {
        String defaults="# Feature switch.\nfeature: false\n# Message text.\nmessage: default\n";
        String before="# owner heading\n# Feature switch.\nfeature: true # owner inline\n# Message text.\nmessage: \"# literal: value\"\n# owner's note\n";
        String after=YamlComments.below(before,defaults);
        assertTrue(after.contains("feature: true # owner inline\n# Feature switch.\n"));
        assertTrue(after.contains("message: \"# literal: value\"\n# Message text.\n"));
        assertTrue(after.contains("# owner heading"));assertTrue(after.contains("# owner's note"));
        assertEquals(Configuration.parse(before),Configuration.parse(after));
        assertEquals(after,YamlComments.below(after,defaults));
    }
    @Test void handlesNestedCollectionsAndLiteralScalarsWithoutMovingTheirContents() throws Exception {
        String defaults="# Group settings.\ngroup:\n  # Text setting.\n  text: default\n  # Item list.\n  items: []\n";
        String before="group:\n  text: |-\n    # literal: text\n    next: value\n  items:\n    - \"# item: value\"\n";
        String after=YamlComments.below(before,defaults);
        assertTrue(after.contains("group:\n# Group settings.\n"));
        assertTrue(after.contains("    # literal: text\n    next: value\n  # Text setting.\n"));
        assertTrue(after.contains("  items:\n  # Item list.\n"));
        assertEquals(Configuration.parse(before),Configuration.parse(after));
        assertEquals(after,YamlComments.below(after,defaults));
    }
    @Test void removesObsoleteCommunityToolsAndPreservesUpdatesAndOwnerComments() throws Exception {
        String before="config-version: 2\nupdates:\n  enabled: false\ncommunity-tools:\n  # owner's private note\n  channel-id: '123' # owner inline note\n  announcements:\n    enabled: true\n    schedules: []\n  templates:\n    custom: hello\n";
        String defaults="config-version: 2\nupdates:\n  enabled: true\n";
        String after=SchemaUpgrade.merge(before,defaults,true);
        assertEquals(false,((java.util.Map<?,?>)Configuration.parse(after).get("updates")).get("enabled"));
        assertFalse(Configuration.parse(after).containsKey("community-tools"));
        assertTrue(after.contains("owner's private note"));assertTrue(after.contains("owner inline note"));
    }
    @Test void leavesUnknownOwnerKeysAndCommentsAlone() throws Exception {
        String source="feature: true\nowner-extra: '# custom: value'\n# owner's note\n";
        String after=YamlComments.below(source,"# Feature switch.\nfeature: false\n");
        assertTrue(after.contains("owner-extra: '# custom: value'\n# owner's note\n"));
        assertEquals(after,YamlComments.below(after,"# Feature switch.\nfeature: false\n"));
    }
}
