package dev.dydiscord;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.jar.*;
import static org.junit.jupiter.api.Assertions.*;
class ArtifactTest {
    private Path jar(){return Path.of("build/libs/DyDiscord-v1.0.1-BETA.jar");}
    @Test void workerLoadsRelocatedLoggingProviderEvenWithForeignServerContext()throws Exception{
        try(URLClassLoader isolated=new URLClassLoader(new URL[]{jar().toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            Class<?> workers=Class.forName("dev.dydiscord.util.PluginThreads",true,isolated);
            var factory=(java.util.concurrent.ThreadFactory)workers.getMethod("factory",String.class).invoke(null,"logging-provider-test");
            var result=new java.util.concurrent.CompletableFuture<String>();Thread thread;
            ClassLoader original=Thread.currentThread().getContextClassLoader();try{Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());thread=factory.newThread(()->{try{Class<?> jda=Class.forName("dev.dydiscord.lib.jda.internal.utils.JDALogger",true,isolated);assertEquals(true,jda.getField("SLF4J_ENABLED").get(null));Object logging=Class.forName("dev.dydiscord.lib.slf4j.LoggerFactory",true,isolated).getMethod("getILoggerFactory").invoke(null);result.complete(logging.getClass().getName());}catch(Throwable e){result.completeExceptionally(e);}});}finally{Thread.currentThread().setContextClassLoader(original);}
            thread.start();assertEquals("dev.dydiscord.lib.slf4j.jul.JDK14LoggerFactory",result.get(5,java.util.concurrent.TimeUnit.SECONDS));thread.join(1000);assertFalse(thread.isAlive());
        }
    }
    @Test void shadedArtifactCanLoadJdaYamlAndNativeSqliteWithoutDevelopmentDependencies()throws Exception{
        try(URLClassLoader isolated=new URLClassLoader(new URL[]{jar().toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            Class.forName("dev.dydiscord.lib.jda.api.JDABuilder",true,isolated);Class.forName("dev.dydiscord.lib.yaml.Yaml",true,isolated);
            Driver driver=(Driver)Class.forName("org.sqlite.JDBC",true,isolated).getConstructor().newInstance();try(Connection c=driver.connect("jdbc:sqlite::memory:",new Properties());Statement s=c.createStatement()){s.execute("CREATE TABLE smoke(id INTEGER PRIMARY KEY)");s.execute("INSERT INTO smoke VALUES(1)");try(ResultSet r=s.executeQuery("SELECT id FROM smoke")){assertTrue(r.next());assertEquals(1,r.getInt(1));}}
        }
    }
    @Test void jarContainsVersionTranslationsServiceMetadataAndNoServerOrSecrets()throws Exception{
        try(JarFile archive=new JarFile(jar().toFile())){
            String plugin=new String(archive.getInputStream(archive.getJarEntry("plugin.yml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(plugin.contains("1.0.1-BETA"));assertTrue(plugin.contains("author: Claww"));assertNotNull(archive.getJarEntry("META-INF/services/java.sql.Driver"));
            for(String file:List.of("config.yml","bot.yml","messages_en.yml","messages_tr.yml","discord/messages_en.yml","discord/messages_tr.yml","META-INF/THIRD-PARTY-NOTICES.md","META-INF/licenses/trove4j/core-3.1.0-sources.jar"))assertNotNull(archive.getJarEntry(file),file);
            assertNull(archive.getJarEntry("org/bukkit/Bukkit.class"));assertNull(archive.getJarEntry("org/apache/logging/log4j/core/Logger.class"));assertNull(archive.getJarEntry("net/dv8tion/jda/api/JDA.class"));
            var entries=archive.entries();while(entries.hasMoreElements()){String path=entries.nextElement().getName();assertFalse(path.endsWith(".db")||path.endsWith(".log"));}
            String bot=new String(archive.getInputStream(archive.getJarEntry("bot.yml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(bot.contains("token: \"\""));
        }
    }
}
