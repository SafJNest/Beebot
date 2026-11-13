package com.safjnest.guild;

import com.safjnest.auth.DiscordApi.Server;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import com.safjnest.App;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.redis.RedisClient;
import com.safjnest.utils.CryptoUtils;
import com.safjnest.utils.JsonCodec;
import org.springframework.http.HttpStatus;

public final class DiscordGuildCache {
    public static final int TTL_SECONDS = 60;
    private static final int LEASE_SECONDS = 60;
    private static final int GENERATION_SECONDS = 86400;
    private static final int POLL_MILLIS = 25;
    private static final int MAX_IDLE_SLOTS = 10000;
    private static final String STATE = "local g=redis.call('GET',KEYS[2]); if not g then g=ARGV[1]; redis.call('SET',KEYS[2],g,'EX',ARGV[2]); end; return {g,redis.call('GET',KEYS[1]) or ''}";
    private static final String CLAIM = "if redis.call('GET',KEYS[2])~=ARGV[1] then return -1 end; if redis.call('SET',KEYS[3],ARGV[2],'NX','EX',ARGV[3]) then return 1 end; return 0";
    private static final String PUBLISH = "if redis.call('GET',KEYS[2])~=ARGV[1] or redis.call('GET',KEYS[3])~=ARGV[2] then return 0 end; redis.call('SET',KEYS[1],ARGV[3],'EX',ARGV[4]); redis.call('DEL',KEYS[3]); return 1";
    private static final String RELEASE = "if redis.call('GET',KEYS[3])==ARGV[1] then redis.call('DEL',KEYS[3]); end; return 1";
    private static final String INVALIDATE = "redis.call('SET',KEYS[2],ARGV[1],'EX',ARGV[2]); redis.call('DEL',KEYS[1],KEYS[3]); return 1";
    private static final DiscordGuildCache INSTANCE = new DiscordGuildCache(RedisClient::authEval);

    @FunctionalInterface
    public interface Store { RedisClient.AuthValue<Object> eval(String script, List<String> keys, List<String> arguments); }
    public record Entry(List<Server> servers, long expiresAt) {
        public Entry { servers = List.copyOf(servers); }
    }
    private record State(boolean available, String generation, Entry entry) {}
    private static final class Slot {
        long generation;
        long touched;
        Entry entry;
        FutureTask<List<Server>> flight;
    }

    private final Store store;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Slot> slots = new ConcurrentHashMap<>();

    public DiscordGuildCache(Store store) { this(store, System::currentTimeMillis); }
    public DiscordGuildCache(Store store, LongSupplier clock) { this.store = store; this.clock = clock; }
    public static DiscordGuildCache getInstance() { return INSTANCE; }
    public static DiscordGuildCache memory() { return new DiscordGuildCache((script, keys, args) -> new RedisClient.AuthValue<>(false, null)); }

    public List<Server> get(AuthContext context, Supplier<List<Server>> loader) {
        String key = key(context);
        Slot slot = slots.compute(key, (ignored, current) -> {
            Slot result = current == null ? new Slot() : current;
            synchronized (result) { result.touched = clock.getAsLong(); }
            return result;
        });
        FutureTask<List<Server>> flight;
        boolean start = false;
        synchronized (slot) {
            slot.touched = clock.getAsLong();
            if (slot.flight == null) {
                long generation = slot.generation;
                slot.flight = new FutureTask<>(() -> load(key, slot, generation, loader));
                start = true;
            }
            flight = slot.flight;
        }
        if (start) flight.run();
        try { return flight.get(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (ExecutionException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) throw runtime;
            throw unavailable();
        } finally {
            synchronized (slot) { if (flight.isDone() && slot.flight == flight) slot.flight = null; }
            prune();
        }
    }

    public void invalidate(AuthContext context) {
        String key = key(context);
        Slot slot = slots.compute(key, (ignored, current) -> {
            Slot result = current == null ? new Slot() : current;
            synchronized (result) { result.touched = clock.getAsLong(); }
            return result;
        });
        synchronized (slot) { slot.generation++; slot.entry = null; slot.touched = clock.getAsLong(); }
        store.eval(INVALIDATE, keys(key), List.of(CryptoUtils.randomToken(), Integer.toString(GENERATION_SECONDS)));
    }

    // ================================================================================================================

    private List<Server> load(String key, Slot slot, long generation, Supplier<List<Server>> loader) {
        State state = state(key);
        if (state.available() && valid(state.entry())) return remember(slot, generation, state.entry());
        synchronized (slot) {
            if (!state.available() && valid(slot.entry)) return slot.entry.servers();
        }
        String owner = CryptoUtils.randomToken();
        boolean claimed = false;
        if (state.available()) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(LEASE_SECONDS);
            while (true) {
                var claim = store.eval(CLAIM, keys(key), List.of(state.generation(), owner, Integer.toString(LEASE_SECONDS)));
                if (!claim.available()) break;
                int result = ((Number) claim.value()).intValue();
                if (result < 0) throw invalidated();
                if (result == 1) { claimed = true; break; }
                if (System.nanoTime() >= deadline) throw unavailable();
                try { Thread.sleep(POLL_MILLIS); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw unavailable(); }
                State current = state(key);
                if (!current.available()) break;
                if (!state.generation().equals(current.generation())) throw invalidated();
                if (valid(current.entry())) return remember(slot, generation, current.entry());
            }
        }
        try {
            if (claimed) {
                State current = state(key);
                if (current.available()) {
                    if (!state.generation().equals(current.generation())) throw invalidated();
                    if (valid(current.entry())) return remember(slot, generation, current.entry());
                }
            }
            Entry entry = new Entry(loader.get(), clock.getAsLong() + TTL_SECONDS * 1000L);
            synchronized (slot) {
                if (slot.generation != generation) throw invalidated();
                if (claimed) {
                    var publish = store.eval(PUBLISH, keys(key), List.of(state.generation(), owner, JsonCodec.toJson(entry), Integer.toString(TTL_SECONDS)));
                    if (publish.available() && ((Number) publish.value()).intValue() != 1) throw invalidated();
                }
                slot.entry = entry;
            }
            return entry.servers();
        } finally {
            if (claimed) store.eval(RELEASE, keys(key), List.of(owner));
        }
    }

    private State state(String key) {
        var result = store.eval(STATE, keys(key), List.of(CryptoUtils.randomToken(), Integer.toString(GENERATION_SECONDS)));
        if (!result.available()) return new State(false, null, null);
        List<?> values = (List<?>) result.value();
        Entry entry = null;
        try { if (!values.get(1).toString().isEmpty()) entry = JsonCodec.fromJson(values.get(1).toString(), Entry.class); }
        catch (RuntimeException ignored) { }
        return new State(true, values.get(0).toString(), entry);
    }

    private List<Server> remember(Slot slot, long generation, Entry entry) {
        synchronized (slot) {
            if (slot.generation != generation) throw invalidated();
            slot.entry = entry;
            return entry.servers();
        }
    }
    private boolean valid(Entry entry) { return entry != null && entry.servers() != null && entry.expiresAt() > clock.getAsLong(); }
    private String key(AuthContext context) { return (App.isTesting() ? "beebot_test:" : "beebot:") + "auth:guilds:v2:{" + context.cacheIdentity() + "}"; }
    private List<String> keys(String key) { return List.of(key, key + ":generation", key + ":lock"); }
    private void prune() {
        if (slots.size() < MAX_IDLE_SLOTS) return;
        long cutoff = clock.getAsLong() - TTL_SECONDS * 1000L;
        slots.forEach((key, ignored) -> slots.computeIfPresent(key, (currentKey, slot) -> {
            synchronized (slot) { return slot.flight == null && slot.touched < cutoff ? null : slot; }
        }));
    }
    private static AuthException unavailable() { return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_unavailable", "Cannot load guild permissions"); }
    private static AuthException invalidated() { return new AuthException(HttpStatus.UNAUTHORIZED, "invalid_session", "Session changed while loading guild permissions"); }
}
