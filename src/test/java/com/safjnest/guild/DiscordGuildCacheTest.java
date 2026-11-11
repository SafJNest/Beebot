package com.safjnest.guild;

import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import com.safjnest.auth.ApiKey;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.auth.DiscordApi.Server;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.redis.RedisClient;
import org.junit.Test;

public class DiscordGuildCacheTest {
    private final List<Server> servers = List.of(new Server("1", "Guild", null, true));

    @Test
    public void fallbackExpiresAndIsolatesUsersSessionsAndChangedTokens() {
        AtomicLong clock = new AtomicLong(1000);
        var cache = new DiscordGuildCache((script, keys, args) -> new RedisClient.AuthValue<>(false, null), clock::get);
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<Server>> loader = () -> { calls.incrementAndGet(); return servers; };
        var context = context("42", "session", "token");
        assertEquals(servers, cache.get(context, loader));
        assertEquals(servers, cache.get(context, loader));
        assertEquals(1, calls.get());
        clock.addAndGet(60001);
        cache.get(context, loader);
        cache.get(context("43", "session", "token"), loader);
        cache.get(context("42", "second-session", "token"), loader);
        cache.get(context("42", "session", "new-token"), loader);
        assertEquals(5, calls.get());
        cache.invalidate(context);
        cache.get(context, loader);
        assertEquals(6, calls.get());
    }

    @Test
    public void singleFlightAndInvalidationPreventPublishingOldResult() throws Exception {
        var cache = DiscordGuildCache.memory();
        var context = context("42", "session", "token");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<Server>> loader = () -> { calls.incrementAndGet(); entered.countDown(); await(release); return servers; };
        try (var executor = Executors.newFixedThreadPool(8)) {
            var first = executor.submit(() -> cache.get(context, loader));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            List<Future<List<Server>>> rest = new ArrayList<>();
            for (int i = 0; i < 6; i++) rest.add(executor.submit(() -> cache.get(context, loader)));
            release.countDown();
            assertEquals(servers, first.get(3, TimeUnit.SECONDS));
            for (var request : rest) assertEquals(servers, request.get(3, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
        cache.invalidate(context);
        CountDownLatch blocked = new CountDownLatch(1), unblock = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var flight = executor.submit(() -> cache.get(context, () -> { blocked.countDown(); await(unblock); return servers; }));
            assertTrue(blocked.await(2, TimeUnit.SECONDS));
            cache.invalidate(context);
            unblock.countDown();
            try { flight.get(3, TimeUnit.SECONDS); fail(); }
            catch (ExecutionException error) { assertEquals("invalid_session", ((AuthException) error.getCause()).code()); }
        }
        cache.get(context, () -> { calls.incrementAndGet(); return servers; });
        assertEquals(2, calls.get());
    }

    @Test
    public void redisSharesAcrossInstancesAndRecoveryDoesNotUseAnInvalidatedLocalEntry() throws Exception {
        var redis = new RedisStore();
        var first = new DiscordGuildCache(redis);
        var second = new DiscordGuildCache(redis);
        var context = context("42", "session", "token");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        Supplier<List<Server>> loader = () -> { calls.incrementAndGet(); entered.countDown(); await(release); return servers; };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> first.get(context, loader));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            var b = executor.submit(() -> second.get(context, loader));
            release.countDown();
            assertEquals(servers, a.get(3, TimeUnit.SECONDS));
            assertEquals(servers, b.get(3, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
        second.invalidate(context);
        first.get(context, () -> { calls.incrementAndGet(); return servers; });
        assertEquals(2, calls.get());
        redis.available = false;
        first.get(context, () -> { throw new AssertionError("Fallback should retain a valid successful result"); });
        redis.available = true;
        second.invalidate(context);
        first.get(context, () -> { calls.incrementAndGet(); return servers; });
        assertEquals(3, calls.get());
    }

    @Test
    public void aMissReadBeforeAnotherInstancePublishesDoesNotCauseADuplicateFetch() {
        var redis = new RedisStore();
        var first = new DiscordGuildCache(redis);
        var context = context("42", "session", "token");
        AtomicBoolean intercepted = new AtomicBoolean();
        AtomicInteger calls = new AtomicInteger();
        Supplier<List<Server>> loader = () -> { calls.incrementAndGet(); return servers; };
        var second = new DiscordGuildCache((script, keys, args) -> {
            var result = redis.eval(script, keys, args);
            if (script.startsWith("local g=") && intercepted.compareAndSet(false, true)) first.get(context, loader);
            return result;
        });
        assertEquals(servers, second.get(context, loader));
        assertEquals(1, calls.get());
    }

    @Test
    public void distributedInvalidationFencesAnInflightLoader() throws Exception {
        var redis = new RedisStore();
        var first = new DiscordGuildCache(redis);
        var second = new DiscordGuildCache(redis);
        var context = context("42", "session", "token");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var flight = executor.submit(() -> first.get(context, () -> { entered.countDown(); await(release); return servers; }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            second.invalidate(context);
            release.countDown();
            try { flight.get(3, TimeUnit.SECONDS); fail(); }
            catch (ExecutionException error) { assertEquals("invalid_session", ((AuthException) error.getCause()).code()); }
        }
        assertEquals(servers, second.get(context, () -> servers));
    }

    @Test
    public void realRedisScriptsWhenTestPortIsProvided() throws Exception {
        String configured = System.getProperty("beebot.test.redis.port");
        org.junit.Assume.assumeNotNull(configured);
        int port = Integer.parseInt(configured);
        Set<String> touched = ConcurrentHashMap.newKeySet();
        DiscordGuildCache.Store store = (script, keys, args) -> {
            touched.addAll(keys);
            try (var jedis = new redis.clients.jedis.Jedis("127.0.0.1", port)) {
                return new RedisClient.AuthValue<>(true, jedis.eval(script, keys, args));
            }
        };
        var first = new DiscordGuildCache(store);
        var second = new DiscordGuildCache(store);
        var context = context("42", UUID.randomUUID().toString(), "token");
        AtomicInteger calls = new AtomicInteger();
        try {
            assertEquals(servers, first.get(context, () -> { calls.incrementAndGet(); return servers; }));
            assertEquals(servers, second.get(context, () -> { throw new AssertionError("Redis hit"); }));
            first.invalidate(context);
            second.get(context, () -> { calls.incrementAndGet(); return servers; });
            assertEquals(2, calls.get());
        } finally {
            try (var jedis = new redis.clients.jedis.Jedis("127.0.0.1", port)) { for (String key : touched) jedis.del(key); }
        }
    }

    public static AuthContext context(String user, String session, String token) {
        return new AuthContext(new ApiKey("app", "dashboard", ""), session, token, Long.MAX_VALUE, new AuthUser(user, "name", null, null));
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(3, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    static final class RedisStore implements DiscordGuildCache.Store {
        final Map<String, String> values = new HashMap<>();
        volatile boolean available = true;
        public synchronized RedisClient.AuthValue<Object> eval(String script, List<String> keys, List<String> args) {
            if (!available) return new RedisClient.AuthValue<>(false, null);
            String data = keys.get(0), generation = keys.get(1), lock = keys.get(2);
            Object result;
            if (script.startsWith("local g=")) {
                values.putIfAbsent(generation, args.get(0));
                result = List.of(values.get(generation), values.getOrDefault(data, ""));
            } else if (script.contains("'NX'")) {
                if (!Objects.equals(values.get(generation), args.get(0))) result = -1L;
                else if (values.containsKey(lock)) result = 0L;
                else { values.put(lock, args.get(1)); result = 1L; }
            } else if (script.startsWith("if redis.call('GET',KEYS[2])")) {
                if (Objects.equals(values.get(generation), args.get(0)) && Objects.equals(values.get(lock), args.get(1))) {
                    values.put(data, args.get(2)); values.remove(lock); result = 1L;
                } else result = 0L;
            } else if (script.startsWith("if redis.call('GET',KEYS[3])")) {
                if (Objects.equals(values.get(lock), args.get(0))) values.remove(lock);
                result = 1L;
            } else {
                values.put(generation, args.get(0)); values.remove(data); values.remove(lock); result = 1L;
            }
            return new RedisClient.AuthValue<>(true, result);
        }
    }
}
