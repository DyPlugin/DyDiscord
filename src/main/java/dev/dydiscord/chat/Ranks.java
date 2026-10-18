package dev.dydiscord.chat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import dev.dydiscord.DyDiscord;
import java.lang.reflect.*;

/** Optional public LuckPerms 5 API first, then registered Vault Chat. Never loads users from disk/network. */
public final class Ranks {
    private final DyDiscord plugin;private boolean warned;
    public Ranks(DyDiscord plugin){this.plugin=plugin;}
    public String rank(Player player){
        try{
            Plugin lp=plugin.getServer().getPluginManager().getPlugin("LuckPerms");if(lp!=null&&lp.isEnabled()){
                Class<?> api=Class.forName("net.luckperms.api.LuckPerms",true,lp.getClass().getClassLoader());Object provider=service(api);if(provider!=null){Object manager=call(provider,"getUserManager"),user=call(manager,"getUser",player.getUniqueId());if(user!=null){Object contexts=call(provider,"getContextManager"),query=call(contexts,"getQueryOptions",player),cached=call(user,"getCachedData"),meta=call(cached,"getMetaData",query);String prefix=clean(call(meta,"getPrefix"));if(!prefix.isEmpty())return prefix;String group=clean(call(user,"getPrimaryGroup"));if(!group.equalsIgnoreCase("default"))return group;}}
            }
            Plugin vault=plugin.getServer().getPluginManager().getPlugin("Vault");if(vault!=null&&vault.isEnabled()){
                Class<?> api=Class.forName("net.milkbowl.vault.chat.Chat",true,vault.getClass().getClassLoader());Object provider=service(api);if(provider!=null){String prefix=clean(api.getMethod("getPlayerPrefix",Player.class).invoke(provider,player));if(!prefix.isEmpty())return prefix;String group=clean(api.getMethod("getPrimaryGroup",Player.class).invoke(provider,player));if(!group.equalsIgnoreCase("default"))return group;}
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException failure){if(!warned){warned=true;plugin.getLogger().warning(dev.dydiscord.language.ConsoleDiagnostics.text(plugin,"error-chat-ranks",java.util.Map.of()));}}
        return "";
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private Object service(Class<?> type){return plugin.getServer().getServicesManager().load((Class)type);}
    public static Object call(Object owner,String name,Object... args)throws ReflectiveOperationException{for(Method method:owner.getClass().getMethods()){if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;boolean accepts=true;Class<?>[] types=method.getParameterTypes();for(int i=0;i<args.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i]))accepts=false;if(accepts){if(!method.canAccess(owner))method.trySetAccessible();return method.invoke(owner,args);}}throw new NoSuchMethodException(name);}
    private static String clean(Object value){return ChatText.plain(value instanceof String s?s:"",64);}
}
