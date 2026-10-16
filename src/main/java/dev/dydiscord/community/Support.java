package dev.dydiscord.community;
import dev.dydiscord.DyDiscord;
import dev.dydiscord.config.*;
import dev.dydiscord.language.Messages;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
/** Allowlist-only diagnostics: never reads raw files, identities, commands or URLs. */
public final class Support {
 private Support(){}
 public static String report(DyDiscord p){var s=p.settings();return "DyDiscord "+p.getDescription().getVersion()+"\nJava "+Runtime.version().feature()+"\nBot "+p.bot().state()+"\nData writable: "+p.store().writable()+"\nPanel enabled: "+s.panelEnabled()+"\nChat Minecraft to Discord: "+s.chat().toDiscord()+"\nChat Discord to Minecraft: "+s.chat().toMinecraft()+"\nRoles enabled: "+s.roleSync().enabled()+"\nBoost rewards enabled: "+s.boostRewards().enabled()+"\nFirst link rewards enabled: "+s.linkRewards().enabled()+"\n";}
 public static Path save(DyDiscord p)throws Exception{Path folder=p.getDataFolder().toPath().resolve("reports");Files.createDirectories(folder);Path file=folder.resolve("diagnostic.txt");Configuration.atomicWrite(file,report(p));return file;}
 /** Read-only: unlike initial load, never creates files or performs migration. */
 public static Set<String> validate(Configuration c)throws Exception {Path folder=c.folder();Settings settings=Settings.read(Configuration.read(folder.resolve("config.yml")),Configuration.read(folder.resolve("bot.yml")),System.getenv("DYDISCORD_BOT_TOKEN"));Messages messages=Messages.load(folder);messages.validatePanel(settings.serverName());return messages.missing();}
}
