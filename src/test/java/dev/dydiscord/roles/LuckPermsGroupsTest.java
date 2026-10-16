package dev.dydiscord.roles;

import org.junit.jupiter.api.*;
import java.lang.reflect.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reflects against the real published API jar, isolated from the existing chat test stub. */
class LuckPermsGroupsTest {
    static URLClassLoader api;
    static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    @BeforeAll static void loadApi() throws Exception {
        URL resource = LuckPermsGroupsTest.class.getClassLoader().getResource("net/luckperms/api/query/QueryOptions.class");
        assertNotNull(resource, "Published LuckPerms API must be on test classpath");
        api = new URLClassLoader(new URL[]{((JarURLConnection)resource.openConnection()).getJarFileURL()}, ClassLoader.getPlatformClassLoader());
    }
    @AfterAll static void closeApi() throws Exception { if (api != null) api.close(); }

    @Test void includesMultipleAndInheritedMembershipsWithoutPrimaryGroupShortcut() throws Exception {
        Fixture f = new Fixture();
        Object parent = f.group("staff", List.of(), List.of());
        Object vip = f.group("vip", List.of(f.node("staff", true, null, Map.of())), List.of(parent));
        Object donor = f.group("donor", List.of(), List.of());
        f.user = f.user(List.of(f.node("vip", true, null, Map.of()), f.node("donor", true, null, Map.of())), List.of(vip, donor));
        try (AutoCloseable provider = provider(f, 50)) {
            Object result = groups(provider, Map.of());
            assertEquals(Set.of("vip", "donor", "staff"), value(result, "groups"));
            assertEquals(true, value(result, "known"));
        }
    }
    @Test void emptyFixedContextDoesNotGrantContextualGroupAndConfiguredContextDoes() throws Exception {
        Fixture f = new Fixture(); Object vip = f.group("vip", List.of(), List.of());
        f.user = f.user(List.of(f.node("vip", true, null, Map.of("server", "survival"))), List.of(vip));
        try (AutoCloseable provider = provider(f, 50)) {
            assertEquals(Set.of(), value(groups(provider, Map.of()), "groups"));
            assertEquals(Set.of("vip"), value(groups(provider, Map.of("server", "survival")), "groups"));
        }
    }
    @Test void expiredAncestorCannotGrantDescendantsAndFutureAncestorSchedulesNextExpiry() throws Exception {
        Fixture f = new Fixture(); Object staff = f.group("staff", List.of(), List.of());
        Object vip = f.group("vip", List.of(f.node("staff", true, null, Map.of())), List.of(staff));
        f.user = f.user(List.of(f.node("vip", true, NOW, Map.of())), List.of(vip));
        try (AutoCloseable provider = provider(f, 50)) {
            assertEquals(Set.of(), value(groups(provider, Map.of()), "groups"));
            f.user = f.user(List.of(f.node("vip", true, NOW.plusSeconds(7), Map.of())), List.of(vip));
            Object result = groups(provider, Map.of());
            assertEquals(Set.of("vip", "staff"), value(result, "groups"));
            assertEquals(NOW.plusSeconds(7), value(result, "validUntil"));
        }
    }
    @Test void negativeMembershipDoesNotGrantRoles() throws Exception {
        Fixture f = new Fixture(); Object vip = f.group("vip", List.of(), List.of());
        f.user = f.user(List.of(f.node("vip", false, null, Map.of())), List.of(vip));
        try (AutoCloseable provider = provider(f, 50)) { assertEquals(Set.of(), value(groups(provider, Map.of()), "groups")); }
    }
    @Test void offlineLoadIsBoundedAndFailureIsUnknownRatherThanKnownEmpty() throws Exception {
        Fixture f = new Fixture(); f.loaded = false;
        f.user = f.user(List.of(), List.of());
        try (AutoCloseable provider = provider(f, 10)) {
            assertEquals(true, value(groups(provider, Map.of()), "known"));
            assertEquals(1, f.loads.get());
            f.future = new CompletableFuture<>();
            assertEquals(false, value(groups(provider, Map.of()), "known"));
            f.future = CompletableFuture.failedFuture(new IllegalStateException("private storage details"));
            Object failed = groups(provider, Map.of());
            assertEquals(false, value(failed, "known"));
            assertFalse(value(failed, "reason").toString().contains("private storage details"));
            assertEquals(0, f.cleanups.get(), "Shared LuckPerms users must not be unloaded");
        }
    }
    @Test void unavailableProviderIsUnknownAndClosedSubscriptionsCannotEnqueue() throws Exception {
        Fixture f = new Fixture(); f.user = f.user(List.of(), List.of());
        AutoCloseable provider = provider(f, 50);
        List<UUID> dirty = new ArrayList<>(); AtomicInteger sweeps = new AtomicInteger();
        provider.getClass().getMethod("start", Consumer.class, Runnable.class).invoke(provider, (Consumer<UUID>)dirty::add, (Runnable)sweeps::incrementAndGet);
        f.fire("user.UserDataRecalculateEvent", Map.of("getUser", f.user));
        Object group = f.group("staff", List.of(), List.of());
        f.fire("node.NodeAddEvent", Map.of("getTarget", group));
        assertEquals(List.of(ID), dirty); assertEquals(1, sweeps.get());
        provider.close();
        f.fire("user.UserDataRecalculateEvent", Map.of("getUser", f.user));
        assertEquals(List.of(ID), dirty); assertTrue(f.closed.get() >= 4);
        assertEquals(false, value(groups(provider, Map.of()), "known"));
        try (AutoCloseable absent = provider(null, 50)) { assertEquals(false, value(groups(absent, Map.of()), "known")); }
    }
    @Test void everyRapidGroupChangeInvalidatesDecisionsCalculatedBetweenEvents() throws Exception {
        Fixture f = new Fixture(); f.user = f.user(List.of(), List.of());
        AtomicInteger epoch = new AtomicInteger();
        try (AutoCloseable provider = provider(f, 50)) {
            provider.getClass().getMethod("start", Consumer.class, Runnable.class).invoke(provider,
                    (Consumer<UUID>)uuid -> {}, (Runnable)epoch::incrementAndGet);
            Object group = f.group("staff", List.of(), List.of());
            f.fire("group.GroupDataRecalculateEvent", Map.of("getGroup", group));
            int capturedDecision = epoch.get();
            f.fire("group.GroupDataRecalculateEvent", Map.of("getGroup", group));
            assertTrue(epoch.get() > capturedDecision, "Second rapid mutation must invalidate an intervening membership decision");
        }
    }
    static AutoCloseable provider(Fixture f, long timeout) throws Exception {
        Class<?> type = assertDoesNotThrow(() -> Class.forName("dev.dydiscord.roles.LuckPermsGroups"), "Membership adapter missing");
        Constructor<?> ctor = type.getDeclaredConstructor(Object.class, ClassLoader.class, Clock.class, long.class);
        ctor.setAccessible(true);
        Object service = f == null ? null : f.service();
        if(service != null) {
            Method register = Class.forName("net.luckperms.api.LuckPermsProvider",true,api).getDeclaredMethod("register",Class.forName("net.luckperms.api.LuckPerms",true,api));
            register.setAccessible(true); register.invoke(null,service);
        }
        return (AutoCloseable)ctor.newInstance(service, api, Clock.fixed(NOW, ZoneOffset.UTC), timeout);
    }
    static Object groups(Object p, Map<String,String> context) throws Exception { return p.getClass().getMethod("groups", UUID.class, Map.class).invoke(p, ID, context); }
    static Object value(Object o, String name) throws Exception { return o.getClass().getMethod(name).invoke(o); }
    static Object proxy(String type, BiFunction<Method,Object[],Object> handler) throws Exception {
        return java.lang.reflect.Proxy.newProxyInstance(api, new Class<?>[]{Class.forName("net.luckperms.api."+type, true, api)}, (p,m,a) -> {
            if (m.getName().equals("toString")) return type;
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            return handler.apply(m, a == null ? new Object[0] : a);
        });
    }
    static class Fixture {
        Object user; boolean loaded = true; CompletableFuture<Object> future;
        AtomicInteger loads = new AtomicInteger(), cleanups = new AtomicInteger(), closed = new AtomicInteger();
        Map<Class<?>,Consumer<Object>> events = new HashMap<>();
        Object service() throws Exception {
            Object manager = proxy("model.user.UserManager", (m,a) -> switch(m.getName()) {
                case "getUser" -> loaded ? user : null;
                case "loadUser" -> { loads.incrementAndGet(); yield future == null ? CompletableFuture.completedFuture(user) : future; }
                case "cleanupUser" -> { cleanups.incrementAndGet(); yield null; }
                default -> throw new AssertionError(m);
            });
            Object factory = proxy("context.ContextSetFactory", (m,a) -> {
                try { return contextBuilder(); } catch(Exception e) { throw new RuntimeException(e); }
            });
            Object contexts = proxy("context.ContextManager", (m,a) -> {
                try { return switch(m.getName()) { case "getContextSetFactory" -> factory; case "queryOptionsBuilder" -> {assertEquals("CONTEXTUAL", a[0].toString());yield queryBuilder();} default -> throw new AssertionError("No live context lookup allowed: "+m); }; }
                catch(Exception e) { throw new RuntimeException(e); }
            });
            Object bus = proxy("event.EventBus", (m,a) -> {
                try {
                    Class<?> event = (Class<?>)a[a.length-2]; events.put(event, (Consumer<Object>)a[a.length-1]);
                    return proxy("event.EventSubscription", (sm,sa) -> { if(sm.getName().equals("close")) {closed.incrementAndGet(); return null;} throw new AssertionError(sm); });
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            return proxy("LuckPerms", (m,a) -> switch(m.getName()) {case "getUserManager" -> manager; case "getContextManager" -> contexts; case "getEventBus" -> bus; default -> throw new AssertionError(m);});
        }
        Object contextBuilder() throws Exception {
            Map<String,String> data = new HashMap<>(); Object[] self = new Object[1];
            self[0] = proxy("context.ImmutableContextSet$Builder", (m,a) -> { if(m.getName().equals("add")) {data.put(a[0].toString(),a[1].toString()); return self[0];} try {return context(data);}catch(Exception e){throw new RuntimeException(e);} }); return self[0];
        }
        Object context(Map<String,String> data) throws Exception {
            Map<String,String> copy = Map.copyOf(data);
            return proxy("context.ImmutableContextSet", (m,a) -> switch(m.getName()) { case "toMap" -> {Map<String,Set<String>> map=new HashMap<>();copy.forEach((k,v)->map.put(k,Set.of(v)));yield map;} case "isEmpty" -> copy.isEmpty(); default -> throw new AssertionError(m); });
        }
        Object queryBuilder() throws Exception {
            Object[] context = new Object[1], self = new Object[1]; boolean[] inheritance = {true};
            self[0] = proxy("query.QueryOptions$Builder", (m,a) -> {
                if(m.getName().equals("context")) {context[0]=a[0];return self[0];}
                if(m.getName().equals("flag")) {if(a[0].toString().equals("RESOLVE_INHERITANCE")) inheritance[0]=(Boolean)a[1];return self[0];}
                try {return proxy("query.QueryOptions", (qm,qa) -> {
                    try { return switch(qm.getName()) {case "context" -> context[0];case "flag" -> inheritance[0]; case "satisfies" -> {Map<?,?> fixed=(Map<?,?>)value(context[0],"toMap"), needed=(Map<?,?>)value(qa[0],"toMap"); yield needed.entrySet().stream().allMatch(e -> ((Set<?>)e.getValue()).stream().anyMatch(v -> fixed.get(e.getKey()) instanceof Set<?> present && present.contains(v)));} default -> throw new AssertionError(qm);}; }
                    catch(Exception e) {throw new RuntimeException(e);}
                });} catch(Exception e){throw new RuntimeException(e);}
            }); return self[0];
        }
        Object node(String group, boolean value, Instant expiry, Map<String,String> contexts) throws Exception {
            Object context = context(contexts);
            return proxy("node.types.InheritanceNode", (m,a) -> switch(m.getName()) {case "getGroupName" -> group;case "getValue" -> value;case "getExpiry" -> expiry;case "getContexts" -> context;default -> throw new AssertionError(m);});
        }
        Object user(List<Object> nodes, List<Object> groups) throws Exception { return holder("model.user.User", null, nodes, groups); }
        Object group(String name,List<Object> nodes,List<Object> groups) throws Exception {return holder("model.group.Group",name,nodes,groups);}
        Object holder(String type,String name,List<Object> nodes,List<Object> groups) throws Exception {
            return proxy(type, (m,a) -> switch(m.getName()) {case "getUniqueId" -> ID;case "getName" -> name;case "getNodes" -> nodes;case "getInheritedGroups" -> {try {Object flag=Class.forName("net.luckperms.api.query.Flag",true,api).getEnumConstants()[0];assertEquals(false,a[0].getClass().getMethod("flag",flag.getClass()).invoke(a[0],flag));}catch(Exception e){throw new RuntimeException(e);}yield groups;}default -> throw new AssertionError("No metadata or mutation allowed: "+m);});
        }
        void fire(String name,Map<String,Object> values) throws Exception {
            Class<?> type=Class.forName("net.luckperms.api.event."+name,true,api); Consumer<Object> handler=events.get(type); assertNotNull(handler,"Missing subscription "+name);
            handler.accept(proxy("event."+name,(m,a)->values.get(m.getName())));
        }
    }
}
