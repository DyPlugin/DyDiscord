package dev.dydiscord.util;

import java.util.concurrent.ThreadFactory;

/** Bukkit's server context loader cannot discover relocated dependency services. */
public final class PluginThreads {
    private PluginThreads() {}
    public static ThreadFactory factory(String name){return action->{Thread thread=new Thread(action,name);thread.setDaemon(true);thread.setContextClassLoader(PluginThreads.class.getClassLoader());return thread;};}
}
