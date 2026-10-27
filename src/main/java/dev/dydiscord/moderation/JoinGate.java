package dev.dydiscord.moderation;

import dev.dydiscord.DyDiscord;
import dev.dydiscord.linking.Linking;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Keeps players without a linked Discord account out when linking.require-to-join is on; the kick screen shows their code. */
public final class JoinGate implements Listener {
    private final DyDiscord plugin;
    public JoinGate(DyDiscord plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.HIGH) public void login(AsyncPlayerPreLoginEvent event){
        if(event.getLoginResult()!=AsyncPlayerPreLoginEvent.Result.ALLOWED||!plugin.ready()||!plugin.settings().moderation().requireLink())return;
        UUID uuid=event.getUniqueId();String name=event.getName();
        if(plugin.settings().moderation().bypassOps()&&Bukkit.getOfflinePlayer(uuid).isOp())return;
        String lang=plugin.settings().language().equals("auto")?plugin.settings().botLanguage():plugin.settings().language();
        String text;
        try{
            Optional<Map<String,String>> code=plugin.submit(()->{
                if(plugin.store().byUuid(uuid).isPresent())return Optional.<Map<String,String>>empty();
                var bot=plugin.bot();Linking.Issued issued=plugin.linking().issueOffline(uuid,name,plugin.settings(),bot!=null&&bot.usable());
                String invite=bot.invitation();
                return Optional.of(Map.of("code",issued.code(),"minutes",Integer.toString(Math.max(1,issued.seconds()/60)),"invite",invite==null?"":invite));
            }).get(8,TimeUnit.SECONDS);
            if(code.isEmpty())return;
            text=plugin.messages().render(false,lang,"require-link-kick",code.get());
            if(!code.get().get("invite").isEmpty())text+="\n"+plugin.messages().render(false,lang,"require-link-invite",code.get());
        }catch(Exception unavailable){
            Throwable cause=unavailable.getCause();
            if(!(cause instanceof Linking.Rejected rejected&&rejected.key.equals("already-linked"))){if(!(cause instanceof Linking.Rejected))plugin.error(unavailable);text=plugin.messages().render(false,lang,"require-link-unavailable",Map.of());}
            else return;
        }
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,text);
    }
}
