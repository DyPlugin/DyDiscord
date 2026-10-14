package dev.dydiscord.roles;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Read-only membership lookup. Unknown is never an authoritative empty membership. */
public interface GroupProvider extends AutoCloseable {
    record Result(boolean known, Set<String> groups, Instant validUntil, String reason) {
        public Result {
            groups = Set.copyOf(Objects.requireNonNull(groups));
            Objects.requireNonNull(validUntil);
            Objects.requireNonNull(reason);
            if (!known && !groups.isEmpty()) throw new IllegalArgumentException("Unknown memberships cannot contain groups");
        }
        public static Result known(Set<String> groups, Instant validUntil) {
            return new Result(true, groups, validUntil, "");
        }
        public static Result unknown(String reason) {
            return new Result(false, Set.of(), Instant.MIN, reason);
        }
    }
    Result groups(UUID player, Map<String, String> fixedContext) throws Exception;
    boolean available();
    /** Callbacks must only enqueue bounded work; LuckPerms delivers events asynchronously. */
    default void start(Consumer<UUID> dirty, Runnable sweep) {}
    @Override default void close() {}
}
