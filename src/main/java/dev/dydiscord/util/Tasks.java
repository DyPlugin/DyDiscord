package dev.dydiscord.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Scheduling for every server type from one JAR. Folia runs global work on its global region and
 * player work on the thread that owns the player; other servers use the main thread for both.
 * Folia's scheduler API is reached reflectively, so the plugin still compiles against the Spigot API.
 */
public final class Tasks {
    /** A scheduled task that can be cancelled whichever scheduler started it. */
    public interface Task {void cancel();}
    public static final boolean FOLIA=present("io.papermc.paper.threadedregions.RegionizedServer");
    private static final Folia API=FOLIA?Folia.load():null;
    private final Plugin plugin;
    public Tasks(Plugin plugin){this.plugin=plugin;}

    public Task global(Runnable action){
        if(FOLIA)return API.global(plugin,action,0,-1);
        var handle=plugin.getServer().getScheduler().runTask(plugin,action);return handle::cancel;
    }
    public Task globalTimer(Runnable action,long delay,long period){
        if(FOLIA)return API.global(plugin,action,Math.max(1,delay),Math.max(1,period));
        var handle=plugin.getServer().getScheduler().runTaskTimer(plugin,action,delay,period);return handle::cancel;
    }
    /** Runs on the thread that owns the entity; on Folia nothing runs when the entity is gone. */
    public Task entity(Entity entity,Runnable action,long delay){
        if(FOLIA)return API.entity(plugin,entity,action,Math.max(1,delay),-1);
        var handle=delay<=0?plugin.getServer().getScheduler().runTask(plugin,action):plugin.getServer().getScheduler().runTaskLater(plugin,action,delay);return handle::cancel;
    }
    public Task entityTimer(Entity entity,Runnable action,long delay,long period){
        if(FOLIA)return API.entity(plugin,entity,action,Math.max(1,delay),Math.max(1,period));
        var handle=plugin.getServer().getScheduler().runTaskTimer(plugin,action,delay,period);return handle::cancel;
    }

    private static boolean present(String type){try{Class.forName(type);return true;}catch(ClassNotFoundException absent){return false;}}

    /** Folia's GlobalRegionScheduler and EntityScheduler, looked up once through their public interfaces. */
    private record Folia(Method globalScheduler,Method run,Method delayed,Method fixed,Method entityScheduler,Method entityRun,Method entityDelayed,Method entityFixed,Method cancel) {
        static Folia load(){
            try{
                Class<?> global=Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler"),entity=Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler"),task=Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
                return new Folia(Bukkit.class.getMethod("getGlobalRegionScheduler"),global.getMethod("run",Plugin.class,Consumer.class),global.getMethod("runDelayed",Plugin.class,Consumer.class,long.class),global.getMethod("runAtFixedRate",Plugin.class,Consumer.class,long.class,long.class),
                    Entity.class.getMethod("getScheduler"),entity.getMethod("run",Plugin.class,Consumer.class,Runnable.class),entity.getMethod("runDelayed",Plugin.class,Consumer.class,Runnable.class,long.class),entity.getMethod("runAtFixedRate",Plugin.class,Consumer.class,Runnable.class,long.class,long.class),task.getMethod("cancel"));
            }catch(ReflectiveOperationException failure){throw new IllegalStateException("Folia scheduler API unavailable",failure);}
        }
        Task global(Plugin plugin,Runnable action,long delay,long period){
            Consumer<Object> body=ignored->action.run();
            try{Object scheduler=globalScheduler.invoke(null);
                Object handle=period>0?fixed.invoke(scheduler,plugin,body,delay,period):delay>0?delayed.invoke(scheduler,plugin,body,delay):run.invoke(scheduler,plugin,body);
                return cancel(handle);
            }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        }
        Task entity(Plugin plugin,Entity target,Runnable action,long delay,long period){
            Consumer<Object> body=ignored->action.run();
            try{Object scheduler=entityScheduler.invoke(target);
                Object handle=period>0?entityFixed.invoke(scheduler,plugin,body,null,delay,period):entityDelayed.invoke(scheduler,plugin,body,null,delay);
                return cancel(handle);
            }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        }
        private Task cancel(Object handle){return ()->{if(handle!=null)try{cancel.invoke(handle);}catch(ReflectiveOperationException ignored){}};}
    }
}
