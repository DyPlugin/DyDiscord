package dev.dydiscord.roles;

import dev.dydiscord.DyDiscord;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Adds and removes plain LuckPerms group memberships through the public LuckPerms 5 API, reached reflectively. */
public final class LuckPermsWriter implements DiscordGroups.Writer {
    private final Object api;
    /** Construct on the server thread; Bukkit is only used to look up the optional service. */
    public LuckPermsWriter(DyDiscord plugin){
        Object service=null;Plugin lp=plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        if(lp!=null&&lp.isEnabled())try{Class<?> type=Class.forName("net.luckperms.api.LuckPerms",true,lp.getClass().getClassLoader());service=load(plugin,type);}catch(ReflectiveOperationException|LinkageError|RuntimeException unavailable){service=null;}
        api=service;
    }
    /** Public-API boundary injection for tests. */
    LuckPermsWriter(Object api){this.api=api;}
    @SuppressWarnings({"rawtypes","unchecked"}) private static Object load(DyDiscord plugin,Class<?> type){return plugin.getServer().getServicesManager().load((Class)type);}
    @Override public boolean available(){return api!=null;}
    @Override public boolean add(UUID player,String group)throws Exception{return change(player,group,"add");}
    @Override public boolean remove(UUID player,String group)throws Exception{return change(player,group,"remove");}
    private boolean change(UUID player,String group,String action)throws Exception{
        if(api==null)throw new IllegalStateException("LuckPerms unavailable");
        Object node=LuckPermsGroups.call(LuckPermsGroups.call(LuckPermsGroups.call(api,"getNodeBuilderRegistry"),"forInheritance"),"group",group);node=LuckPermsGroups.call(node,"build");
        Object built=node;AtomicBoolean changed=new AtomicBoolean();
        Consumer<Object> edit=user->{try{changed.set(Boolean.TRUE.equals(LuckPermsGroups.call(LuckPermsGroups.call(LuckPermsGroups.call(user,"data"),action,built),"wasSuccessful")));}catch(ReflectiveOperationException failure){throw new IllegalStateException("LuckPerms edit failed (values withheld)");}};
        Object pending=LuckPermsGroups.call(LuckPermsGroups.call(api,"getUserManager"),"modifyUser",player,edit);
        if(!(pending instanceof CompletableFuture<?> future))throw new IllegalStateException("LuckPerms edit unavailable");
        future.get(10,TimeUnit.SECONDS);return changed.get();
    }
    @Override public Check check(String group)throws Exception{
        if(api==null)throw new IllegalStateException("LuckPerms unavailable");
        Object found=LuckPermsGroups.call(LuckPermsGroups.call(api,"getGroupManager"),"getGroup",group);if(found==null)return Check.MISSING;
        Object state=LuckPermsGroups.call(LuckPermsGroups.call(LuckPermsGroups.call(found,"getCachedData"),"getPermissionData"),"checkPermission","*");
        // Tristate is an API enum without interfaces; its constant name tells TRUE from FALSE and UNDEFINED.
        return state instanceof Enum<?> value&&value.name().equals("TRUE")?Check.WILDCARD:Check.OK;
    }
}
