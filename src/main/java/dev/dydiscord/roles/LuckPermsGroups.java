package dev.dydiscord.roles;

import dev.dydiscord.DyDiscord;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Optional, read-only LuckPerms 5 public API adapter. Never derives membership from chat metadata. */
public final class LuckPermsGroups implements GroupProvider {
    private static final int MAX_HOLDERS = 1024, MAX_NODES = 16384;
    private final Object api;
    private final ClassLoader loader;
    private final Clock clock;
    private final long timeoutMillis;
    private final Thread serverThread;
    private final Object subscriptionLock = new Object();
    private final List<Object> subscriptions = new ArrayList<>();
    private volatile boolean closed;
    private boolean started;

    /** Construct on the server thread: Bukkit is used only to acquire the optional service here. */
    public LuckPermsGroups(DyDiscord plugin) {
        Object service = null; ClassLoader apiLoader = null;
        Plugin lp = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        if (lp != null && lp.isEnabled()) {
            try {
                apiLoader = lp.getClass().getClassLoader();
                Class<?> type = Class.forName("net.luckperms.api.LuckPerms", true, apiLoader);
                service = service(plugin, type);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                plugin.getLogger().warning("Role group provider unavailable; LuckPerms memberships remain unknown (values withheld).");
            }
        }
        this.api = service; this.loader = apiLoader; this.clock = Clock.systemUTC();
        this.timeoutMillis = 5000; this.serverThread = Thread.currentThread();
    }

    /** Public-API boundary injection for deterministic tests, without Bukkit or a live server. */
    LuckPermsGroups(Object api, ClassLoader loader, Clock clock, long timeoutMillis) {
        this.api = api; this.loader = loader; this.clock = Objects.requireNonNull(clock);
        if (timeoutMillis < 1) throw new IllegalArgumentException("Positive timeout required");
        this.timeoutMillis = timeoutMillis; this.serverThread = null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object service(DyDiscord plugin, Class<?> type) {
        return plugin.getServer().getServicesManager().load((Class) type);
    }
    @Override public boolean available() { return api != null && !closed; }

    @Override public Result groups(UUID player, Map<String, String> fixedContext) {
        Objects.requireNonNull(player); Objects.requireNonNull(fixedContext);
        if (!available()) return Result.unknown("provider-unavailable");
        if (Thread.currentThread() == serverThread) return Result.unknown("main-thread");
        try {
            Object manager = call(api, "getUserManager");
            Object user = call(manager, "getUser", player);
            if (user == null) {
                // Do not cancel or unload a shared LuckPerms user. Its own housekeeper expires API usage.
                Object pending = call(manager, "loadUser", player);
                if (!(pending instanceof CompletableFuture<?> future)) return Result.unknown("load-unavailable");
                user = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            }
            if (user == null || !player.equals(call(user, "getUniqueId"))) return Result.unknown("user-unavailable");
            Object query = query(Map.copyOf(fixedContext));
            Class<?> inheritance = type("node.types.InheritanceNode");
            Instant now = clock.instant(), validUntil = Instant.MAX;
            Set<String> names = new LinkedHashSet<>();
            Deque<Object> pending = new ArrayDeque<>(); pending.add(user);
            int holders = 0, nodes = 0;
            while (!pending.isEmpty()) {
                if (!available()) return Result.unknown("provider-unavailable");
                if (++holders > MAX_HOLDERS) return Result.unknown("graph-limit");
                Object holder = pending.removeFirst();
                Set<String> validParents = new HashSet<>();
                for (Object node : collection(call(holder, "getNodes"))) {
                    if (++nodes > MAX_NODES) return Result.unknown("node-limit");
                    if (!inheritance.isInstance(node) || !Boolean.TRUE.equals(call(node, "getValue"))) continue;
                    if (!Boolean.TRUE.equals(call(query, "satisfies", call(node, "getContexts")))) continue;
                    Instant expiry = (Instant) call(node, "getExpiry");
                    // LuckPerms audits temporary nodes periodically. Filter at lookup time as well,
                    // before following any ancestor, so an expired parent cannot grant descendants.
                    if (expiry != null && !expiry.isAfter(now)) continue;
                    if (expiry != null && expiry.isBefore(validUntil)) validUntil = expiry;
                    validParents.add(((String) call(node, "getGroupName")).toLowerCase(Locale.ROOT));
                }
                // Ask LuckPerms for its permitted direct parents. Its context and inheritance rules
                // remain authoritative; we only remove invalid/expired positive edges from that view.
                for (Object group : collection(call(holder, "getInheritedGroups", query))) {
                    String name = ((String) call(group, "getName")).toLowerCase(Locale.ROOT);
                    if (validParents.contains(name) && names.add(name)) pending.addLast(group);
                }
            }
            if (!validUntil.isAfter(clock.instant())) return Result.unknown("expired-during-lookup");
            return available() ? Result.known(names, validUntil) : Result.unknown("provider-unavailable");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); return Result.unknown("interrupted");
        } catch (TimeoutException failure) {
            return Result.unknown("load-timeout");
        } catch (ReflectiveOperationException | ExecutionException | LinkageError | RuntimeException failure) {
            return Result.unknown("lookup-failed");
        }
    }

    private Object query(Map<String, String> context) throws ReflectiveOperationException {
        Object contextBuilder = type("context.ImmutableContextSet").getMethod("builder").invoke(null);
        for (var entry : context.entrySet()) call(contextBuilder, "add", entry.getKey(), entry.getValue());
        Object contexts = call(contextBuilder, "build");
        Class<?> mode = type("query.QueryMode");
        Object builder = type("query.QueryOptions").getMethod("builder", mode).invoke(null, enumValue(mode, "CONTEXTUAL"));
        call(builder, "context", contexts);
        // Traverse one edge at a time to validate every ancestor's current expiry independently.
        call(builder, "flag", enumValue(type("query.Flag"), "RESOLVE_INHERITANCE"), false);
        return call(builder, "build");
    }
    private Class<?> type(String name) throws ClassNotFoundException {
        return Class.forName("net.luckperms.api." + name, true, loader);
    }
    private static Object enumValue(Class<?> type, String name) {
        for (Object constant : type.getEnumConstants()) if (((Enum<?>)constant).name().equals(name)) return constant;
        throw new IllegalArgumentException("Unsupported LuckPerms API");
    }
    private static Collection<?> collection(Object value) {
        if (!(value instanceof Collection<?> items)) throw new IllegalStateException("Invalid LuckPerms collection");
        return items;
    }

    @Override public void start(Consumer<UUID> dirty, Runnable sweep) {
        Objects.requireNonNull(dirty); Objects.requireNonNull(sweep);
        synchronized (subscriptionLock) {
            if (!available() || started) return;
            try {
                Object bus = call(api, "getEventBus");
                for (String name : List.of("user.UserDataRecalculateEvent", "user.UserLoadEvent")) {
                    subscribe(bus, name, event -> userSignal(event, "getUser", dirty, sweep));
                }
                for (String name : List.of("node.NodeAddEvent", "node.NodeRemoveEvent", "node.NodeClearEvent")) {
                    subscribe(bus, name, event -> userSignal(event, "getTarget", dirty, sweep));
                }
                for (String name : List.of("group.GroupDataRecalculateEvent", "group.GroupLoadEvent", "group.GroupCreateEvent", "group.GroupDeleteEvent")) {
                    subscribe(bus, name, event -> sweepSignal(sweep));
                }
                started = true;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                close();
                throw new IllegalStateException("LuckPerms role subscriptions unavailable (values withheld)");
            }
        }
    }
    private void subscribe(Object bus, String name, Consumer<Object> callback) throws ReflectiveOperationException {
        Consumer<Object> guarded = event -> {
            synchronized (subscriptionLock) { if (!closed) callback.accept(event); }
        };
        subscriptions.add(call(bus, "subscribe", type("event." + name), guarded));
    }
    private void userSignal(Object event, String accessor, Consumer<UUID> dirty, Runnable sweep) {
        try {
            Object holder = call(event, accessor);
            if (type("model.user.User").isInstance(holder)) dirty.accept((UUID)call(holder, "getUniqueId"));
            else sweepSignal(sweep);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            sweepSignal(sweep);
        }
    }
    private void sweepSignal(Runnable sweep) {
        // Every mutation invalidates active decisions. The engine coalesces bounded scan work;
        // suppressing events here would leave decisions calculated between mutations usable.
        sweep.run();
    }
    @Override public void close() {
        synchronized (subscriptionLock) {
            closed = true;
            RuntimeException error = null;
            for (Object subscription : subscriptions) {
                try { call(subscription, "close"); }
                catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
                    if (error == null) error = new IllegalStateException("LuckPerms subscription cleanup failed (values withheld)");
                }
            }
            subscriptions.clear();
            if (error != null) throw error;
        }
    }

    /** Invoke public interfaces rather than plugin implementation classes, which may be nonpublic. */
    private static Object call(Object owner, String name, Object... args) throws ReflectiveOperationException {
        Objects.requireNonNull(owner);
        Deque<Class<?>> types = new ArrayDeque<>();
        for (Class<?> current = owner.getClass(); current != null; current = current.getSuperclass()) {
            types.addAll(Arrays.asList(current.getInterfaces()));
        }
        Set<Class<?>> seen = new HashSet<>();
        while (!types.isEmpty()) {
            Class<?> type = types.removeFirst();
            if (!seen.add(type)) continue;
            for (Method method : type.getMethods()) {
                if (accepts(method, name, args)) return method.invoke(owner, args);
            }
            types.addAll(Arrays.asList(type.getInterfaces()));
        }
        throw new NoSuchMethodException(name);
    }
    private static boolean accepts(Method method, String name, Object[] args) {
        if (!method.getName().equals(name) || method.getParameterCount() != args.length) return false;
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == boolean.class) { if (!(args[i] instanceof Boolean)) return false; }
            else if (args[i] != null && !types[i].isInstance(args[i])) return false;
        }
        return true;
    }
}
