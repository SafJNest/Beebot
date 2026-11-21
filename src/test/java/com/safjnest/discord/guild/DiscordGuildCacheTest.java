package com.safjnest.discord.guild;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.safjnest.database.redis.RedisClient;
import com.safjnest.discord.auth.ApiKey;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordApi.Server;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;

import org.junit.Test;

public class DiscordGuildCacheTest {

    private static final AuthContext CONTEXT = new AuthContext(new ApiKey("app", "App", ""), "session", null,
        Long.MAX_VALUE, new AuthUser("2", "user", "User", null));
    private static final List<Server> SERVERS = List.of(new Server("1", "Guild", null, true));

    @Test
    public void localFallbackKeepsOneMinuteExpiry() {
        AtomicLong time = new AtomicLong(1_000);
        AtomicInteger loads = new AtomicInteger();
        DiscordGuildCache cache = new DiscordGuildCache((script, keys, arguments) -> new RedisClient.AuthValue<>(false, null), time::get);

        assertEquals(SERVERS, cache.get(CONTEXT, () -> { loads.incrementAndGet(); return SERVERS; }));
        assertEquals(SERVERS, cache.get(CONTEXT, () -> { throw new AssertionError("Warm cache"); }));
        time.addAndGet(DiscordGuildCache.TTL_SECONDS * 1000L + 1);
        assertEquals(SERVERS, cache.get(CONTEXT, () -> { loads.incrementAndGet(); return SERVERS; }));

        assertEquals(2, loads.get());
    }

    @Test
    public void overlappingReadsShareOneLoader() throws java.lang.Exception {
        DiscordGuildCache cache = DiscordGuildCache.memory();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<Server>> first = executor.submit(() -> cache.get(CONTEXT, () -> {
                loads.incrementAndGet();
                entered.countDown();
                await(release);
                return SERVERS;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<List<Server>> second = executor.submit(() -> cache.get(CONTEXT, () -> {
                loads.incrementAndGet();
                return SERVERS;
            }));
            release.countDown();

            assertEquals(SERVERS, first.get(5, TimeUnit.SECONDS));
            assertEquals(SERVERS, second.get(5, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void invalidationRejectsAnOlderInflightLoad() throws java.lang.Exception {
        DiscordGuildCache cache = DiscordGuildCache.memory();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<List<Server>> load = executor.submit(() -> cache.get(CONTEXT, () -> {
                entered.countDown();
                await(release);
                return SERVERS;
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            cache.invalidate(CONTEXT);
            release.countDown();

            ExecutionException failure = assertThrows(ExecutionException.class, () -> load.get(5, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof Exception);
            assertEquals(Type.UNAUTHENTICATED, ((Exception) failure.getCause()).getType());
            assertEquals(List.of(), cache.get(CONTEXT, List::of));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    public void failedLoaderDoesNotPublishAnEmptyDirectory() {
        DiscordGuildCache cache = DiscordGuildCache.memory();
        Exception unavailable = new Exception(Type.UNAVAILABLE, "discord_unavailable", "Unavailable");

        assertSame(unavailable, assertThrows(Exception.class, () -> cache.get(CONTEXT, () -> { throw unavailable; })));
        assertEquals(SERVERS, cache.get(CONTEXT, () -> SERVERS));
    }

    /* -------------------------------------------------------------------------- */

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Timed out waiting for the test");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
