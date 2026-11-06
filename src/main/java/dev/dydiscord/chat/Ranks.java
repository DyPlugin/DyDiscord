package dev.dydiscord.chat;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import dev.dydiscord.DyDiscord;
import java.lang.reflect.*;
import java.util.*;

/** Optional public LuckPerms 5 API first, then registered Vault Chat. Never loads users from disk/network. */
public final class Ranks {
    private final DyDiscord plugin;private boolean warned;
    /** Method lookups by receiver type, name and argument types; getMethods() copies every public method on each call. */
    private record Lookup(Class<?> owner,String name,List<Class<?>> args) {}
    private static final Map<Lookup,Method> METHODS=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ClassLoader,Map<String,Class<?>>> types=new java.util.concurrent.ConcurrentHashMap<>();
    private Class<?> type(String name,ClassLoader loader)throws ClassNotFoundException{Map<String,Class<?>> known=types.computeIfAbsent(loader,ignored->new java.util.concurrent.ConcurrentHashMap<>());Class<?> type=known.get(name);if(type==null){type=Class.forName(name,true,loader);if(types.size()>8)types.clear();known.put(name,type);}return type;}
    public Ranks(DyDiscord plugin){this.plugin=plugin;}
    public String rank(Player player){
        try{
            Plugin lp=plugin.getServer().getPluginManager().getPlugin("LuckPerms");if(lp!=null&&lp.isEnabled()){
                Class<?> api=type("net.luckperms.api.LuckPerms",lp.getClass().getClassLoader());Object provider=service(api);if(provider!=null){Object manager=call(provider,"getUserManager"),user=call(manager,"getUser",player.getUniqueId());if(user!=null){Object contexts=call(provider,"getContextManager"),query=call(contexts,"getQueryOptions",player),cached=call(user,"getCachedData"),meta=call(cached,"getMetaData",query);String prefix=clean(call(meta,"getPrefix"));if(!prefix.isEmpty())return prefix;String group=clean(call(user,"getPrimaryGroup"));if(!group.equalsIgnoreCase("default"))return group;}}
            }
            Plugin vault=plugin.getServer().getPluginManager().getPlugin("Vault");if(vault!=null&&vault.isEnabled()){
                Class<?> api=type("net.milkbowl.vault.chat.Chat",vault.getClass().getClassLoader());Object provider=service(api);if(provider!=null){String prefix=clean(api.getMethod("getPlayerPrefix",Player.class).invoke(provider,player));if(!prefix.isEmpty())return prefix;String group=clean(api.getMethod("getPrimaryGroup",Player.class).invoke(provider,player));if(!group.equalsIgnoreCase("default"))return group;}
            }
        }catch(ReflectiveOperationException|LinkageError|RuntimeException failure){if(!warned){warned=true;plugin.getLogger().warning(dev.dydiscord.language.ConsoleDiagnostics.text(plugin,"error-chat-ranks",java.util.Map.of()));}}
        return "";
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private Object service(Class<?> type){return plugin.getServer().getServicesManager().load((Class)type);}
    public static Object call(Object owner,String name,Object... args)throws ReflectiveOperationException{
        List<Class<?>> argTypes=new ArrayList<>(args.length);for(Object arg:args)argTypes.add(arg==null?null:arg.getClass());Lookup key=new Lookup(owner.getClass(),name,argTypes);
        Method cached=METHODS.get(key);if(cached!=null)return cached.invoke(owner,args);
        for(Method method:owner.getClass().getMethods()){if(!method.getName().equals(name)||method.getParameterCount()!=args.length)continue;boolean accepts=true;Class<?>[] types=method.getParameterTypes();for(int i=0;i<args.length;i++)if(args[i]!=null&&!types[i].isInstance(args[i]))accepts=false;if(accepts){if(!method.canAccess(owner))method.trySetAccessible();if(METHODS.size()<512)METHODS.put(key,method);return method.invoke(owner,args);}}throw new NoSuchMethodException(name);}
    private static String clean(Object value){return ChatText.plain(value instanceof String s?s:"",64);}
}
