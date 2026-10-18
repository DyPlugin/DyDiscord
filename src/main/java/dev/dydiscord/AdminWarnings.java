package dev.dydiscord;

import dev.dydiscord.discord.SetupProblem;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.time.Instant;
import java.util.*;

/** Only known, localized repair messages can be broadcast; no exception text is accepted. */
public final class AdminWarnings implements AutoCloseable {
    private static final Set<String> ALLOWED;
    static{Set<String> keys=new HashSet<>(Set.of("setup-intents-denied","setup-connection-failed","setup-chat-conflict","setup-chat-adapter","startup-failed"));for(SetupProblem.Code code:SetupProblem.Code.values())keys.add(new SetupProblem(code).key());ALLOWED=Set.copyOf(keys);}
    private final DyDiscord plugin;
    private final Map<String,Instant> last=new HashMap<>();
    private volatile boolean closed;
    public AdminWarnings(DyDiscord plugin){this.plugin=plugin;}
    public void warn(String key){
        if(closed||key==null||!ALLOWED.contains(key))return;
        synchronized(last){Instant now=plugin.clock().instant();Instant previous=last.get(key);if(previous!=null&&now.isBefore(previous.plusSeconds(60)))return;last.put(key,now);}
        plugin.onMain(()->{
            if(closed)return;
            String console=dev.dydiscord.language.ConsoleDiagnostics.text(plugin,key,Map.of());
            plugin.getLogger().warning(console);
            for(Player player:plugin.getServer().getOnlinePlayers())if(player.isOnline()&&player.hasPermission("dydiscord.admin.alerts"))plugin.message(player,key,Map.of());
        });
    }
    public void resolved(String key){if(key!=null&&ALLOWED.contains(key))synchronized(last){last.remove(key);}}
    @Override public void close(){closed=true;synchronized(last){last.clear();}}
}
