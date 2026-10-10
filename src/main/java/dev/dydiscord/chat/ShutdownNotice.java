package dev.dydiscord.chat;

import java.util.concurrent.*;
import java.util.function.Supplier;

/** A confirmed shutdown is the only caller. Failure never keeps the server alive. */
public final class ShutdownNotice {
    public static boolean stopping(Object server){try{return Boolean.TRUE.equals(server.getClass().getMethod("isStopping").invoke(server));}catch(ReflectiveOperationException|RuntimeException failure){return false;}}
    public static boolean send(boolean stopping,boolean enabled,Supplier<CompletableFuture<?>> send,long timeoutMillis){
        if(!stopping||!enabled)return false;CompletableFuture<?> pending=null;try{pending=send.get();pending.get(timeoutMillis,TimeUnit.MILLISECONDS);return true;}catch(InterruptedException failure){Thread.currentThread().interrupt();return false;}catch(Exception failure){return false;}finally{if(pending!=null&&!pending.isDone())pending.cancel(true);}
    }
}
