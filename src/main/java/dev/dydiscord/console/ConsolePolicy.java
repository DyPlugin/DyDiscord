package dev.dydiscord.console;
import dev.dydiscord.config.Settings;
import java.util.*;
public final class ConsolePolicy {
    public record Source(String guild,String channel,boolean human,boolean webhook,boolean thread,boolean edited,boolean attachments) {}
    public static boolean source(Source source,Settings settings){return source.human&&!source.webhook&&!source.thread&&!source.edited&&!source.attachments&&settings.consoleEnabled()&&settings.commandsEnabled()&&source.guild.equals(settings.guild())&&source.channel.equals(settings.consoleChannel());}
    public static boolean authorized(boolean administrator,Set<String> roles,Settings settings){return settings.administrator()&&administrator||roles.stream().anyMatch(settings.roles()::contains);}
    public static String command(String text){
        if(text==null||text.length()>512||text.isBlank()||text.startsWith("dy!")||text.contains("`")||text.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw new IllegalArgumentException("Invalid console command");
        String command=text.startsWith("/")?text.substring(1):text;if(command.isBlank()||command.startsWith("/")||!command.split(" ",2)[0].matches("[A-Za-z0-9_:.?-]{1,100}"))throw new IllegalArgumentException("Invalid console command");return command;
    }
}
