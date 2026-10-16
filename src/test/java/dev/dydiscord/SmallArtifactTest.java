package dev.dydiscord;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;

class SmallArtifactTest {
    private Path jar(){return Path.of("build/libs/DyDiscord-1.0.0.jar");}

    @Test void smallDistributionDeclaresBuildLibraryCoordinatesWithoutBundlingTheirClasses()throws Exception{
        assertTrue(Files.size(jar())<4_000_000L,"Small distribution must fit a 4 MB upload budget");
        try(JarFile archive=new JarFile(jar().toFile())){
            Map<?,?> descriptor;
            try(var in=archive.getInputStream(archive.getJarEntry("plugin.yml"))){descriptor=new Yaml().load(in);}
            assertEquals("1.0.0",descriptor.get("version"));
            assertEquals(new TreeSet<>(List.of(System.getProperty("dydiscord.runtime.coordinates").split(","))),new TreeSet<>((List<String>)descriptor.get("libraries")));
            assertNull(archive.getJarEntry("net/dv8tion/jda/api/JDA.class"));
            assertNull(archive.getJarEntry("dev/dydiscord/lib/jda/api/JDA.class"));
            assertNull(archive.getJarEntry("org/sqlite/JDBC.class"));
            assertNotNull(archive.getJarEntry("dev/dydiscord/DyDiscord.class"));
            assertNotNull(archive.getJarEntry("META-INF/THIRD-PARTY-NOTICES.md"));
            assertNotNull(archive.getJarEntry("META-INF/licenses/trove4j/core-3.1.0-sources.jar"));
        }
    }

    @Test void smallDistributionWorksWithSeparateRuntimeLibrariesInAnIsolatedLoader()throws Exception{
        List<URL> urls=new ArrayList<>();urls.add(jar().toUri().toURL());
        for(String path:System.getProperty("dydiscord.runtime.classpath").split(java.io.File.pathSeparator))urls.add(Path.of(path).toUri().toURL());
        try(URLClassLoader loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())){
            Class<?> data=Class.forName("net.dv8tion.jda.api.utils.data.DataObject",true,loader);
            Object parsed=data.getMethod("fromJson",String.class).invoke(null,"{\"probe\":\"ok\"}");
            assertEquals("ok",data.getMethod("getString",String.class).invoke(parsed,"probe"));
            Class<?> text=Class.forName("net.dv8tion.jda.api.components.textdisplay.TextDisplay",true,loader);
            Object body=text.getMethod("of",String.class).invoke(null,"Small distribution");
            Class<?> container=Class.forName("net.dv8tion.jda.api.components.container.Container",true,loader);
            Object card=container.getMethod("of",Collection.class).invoke(null,List.of(body));
            Object json=Class.forName("net.dv8tion.jda.api.utils.data.SerializableData",true,loader).getMethod("toData").invoke(card);
            assertTrue(json.toString().contains("Small distribution"));
            Driver driver=(Driver)Class.forName("org.sqlite.JDBC",true,loader).getConstructor().newInstance();
            try(Connection connection=driver.connect("jdbc:sqlite::memory:",new Properties());Statement sql=connection.createStatement()){
                sql.execute("CREATE TABLE packaging(value TEXT)");sql.execute("INSERT INTO packaging VALUES('ok')");
                try(ResultSet result=sql.executeQuery("SELECT value FROM packaging")){assertTrue(result.next());assertEquals("ok",result.getString(1));}
            }
        }
    }

    @Test void bothDistributionsKeepIdenticalConfigurationTranslationsAndApplicationClasses()throws Exception{
        try(JarFile small=new JarFile(jar().toFile());JarFile full=new JarFile("build/libs/DyDiscord-1.0.0-standalone.jar")){
            for(String resource:List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml")){
                try(var a=small.getInputStream(small.getJarEntry(resource));var b=full.getInputStream(full.getJarEntry(resource))){assertArrayEquals(a.readAllBytes(),b.readAllBytes(),resource);}
            }
            Set<String> smallClasses=new TreeSet<>(),fullClasses=new TreeSet<>();
            small.stream().map(JarEntry::getName).filter(n->n.startsWith("dev/dydiscord/")&&n.endsWith(".class")).forEach(smallClasses::add);
            full.stream().map(JarEntry::getName).filter(n->n.startsWith("dev/dydiscord/")&&!n.startsWith("dev/dydiscord/lib/")&&n.endsWith(".class")).forEach(fullClasses::add);
            assertEquals(fullClasses,smallClasses);
            try(var descriptor=full.getInputStream(full.getJarEntry("plugin.yml"))){assertNull(((Map<?,?>)new Yaml().load(descriptor)).get("libraries"));}
        }
    }
}
