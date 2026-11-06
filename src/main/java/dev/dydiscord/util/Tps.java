package dev.dydiscord.util;

import java.lang.reflect.Method;
import java.util.Optional;

/** Paper and its forks report TPS through getTPS; Spigot does not, and Folia has no single server TPS. Looked up once per server type. */
public final class Tps {
    private static final ClassValue<Optional<Method>> METHOD=new ClassValue<>(){@Override protected Optional<Method> computeValue(Class<?> type){try{Method method=type.getMethod("getTPS");method.trySetAccessible();return Optional.of(method);}catch(NoSuchMethodException|RuntimeException absent){return Optional.empty();}}};
    private Tps(){}
    /** The one-minute average, or -1 where the server does not report TPS. */
    public static double current(Object server){
        if(server==null)return -1;Optional<Method> method=METHOD.get(server.getClass());if(method.isEmpty())return -1;
        try{double[] values=(double[])method.get().invoke(server);return values!=null&&values.length>0?values[0]:-1;}catch(ReflectiveOperationException|RuntimeException unavailable){return -1;}
    }
}
