package com.safjnest.bot.cache;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.junit.Test;

import com.safjnest.bot.cache.managers.GenericCache;

public class CacheAdapterTest {

    @Test
    public void isolatesInstancesAndPreservesTypedKeys() {
        GenericCache<Integer, String> first = new GenericCache<>(10, 1, TimeUnit.HOURS, Integer.class, String.class);
        GenericCache<Integer, String> second = new GenericCache<>(10, 1, TimeUnit.HOURS, Integer.class, String.class);
        first.put(7, "first");
        second.put(7, "second");
        assertEquals("first", first.get(7));
        assertEquals("second", second.get(7));
        assertEquals(Set.of(7), Set.copyOf(first.keySet()));
        first.asTypedMap().clear();
        assertEquals("first", first.get(7));
        first.clear();
        assertNull(first.get(7));
        assertEquals("second", second.get(7));
    }

    @Test
    public void bulkFetchesOnlyTenMissesAndPreservesOrderAndDuplicates() {
        List<String> fetched = new ArrayList<>();
        TestCache cache = new TestCache(keys -> {
            fetched.addAll(keys);
            return values(keys);
        });
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String id = "u" + index;
            ids.add(id);
            if (index < 10)
                cache.put(id, new Value(id));
        }
        Value hit = cache.get("u0");
        ids.add("u0");
        List<Value> loaded = cache.getOrLoadAll(ids);
        assertEquals(ids.subList(10, 20), fetched);
        assertEquals(21, loaded.size());
        for (int index = 0; index < ids.size(); index++)
            assertEquals(ids.get(index), loaded.get(index).id);
        assertSame(hit, loaded.get(0));
        assertSame(hit, loaded.get(20));
    }

    @Test(timeout = 5000)
    public void overlappingBulkAndSingleRequestsShareTheirLoads() throws Exception {
        CountDownLatch firstLoading = new CountDownLatch(1);
        CountDownLatch secondLoading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        TestCache cache = new TestCache(keys -> {
            loads.incrementAndGet();
            if (keys.contains("a")) {
                firstLoading.countDown();
                await(finish);
            } else {
                assertEquals(List.of("c"), new ArrayList<>(keys));
                secondLoading.countDown();
            }
            return values(keys);
        });
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            Future<List<Value>> first = workers.submit(() -> cache.getOrLoadAll(List.of("a", "b")));
            await(firstLoading);
            Future<List<Value>> second = workers.submit(() -> cache.getOrLoadAll(List.of("b", "c")));
            await(secondLoading);
            Future<Value> single = workers.submit(() -> cache.getOrLoad("b"));
            finish.countDown();
            List<Value> firstValues = first.get(2, TimeUnit.SECONDS);
            List<Value> secondValues = second.get(2, TimeUnit.SECONDS);
            assertSame(firstValues.get(1), secondValues.get(0));
            assertSame(firstValues.get(1), single.get(2, TimeUnit.SECONDS));
            assertEquals(2, loads.get());
        } finally {
            finish.countDown();
            workers.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void concurrentSingleRequestsLoadOnce() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        TestCache cache = new TestCache(keys -> {
            loads.incrementAndGet();
            return values(keys);
        });
        ExecutorService workers = Executors.newFixedThreadPool(12);
        CyclicBarrier start = new CyclicBarrier(12);
        try {
            List<Future<Value>> results = new ArrayList<>();
            for (int index = 0; index < 12; index++)
                results.add(workers.submit(() -> {
                    start.await();
                    return cache.getOrLoad("user");
                }));
            Value first = results.get(0).get(2, TimeUnit.SECONDS);
            for (Future<Value> result : results)
                assertSame(first, result.get(2, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void activeOperationSurvivesExpirationAndEviction() throws Exception {
        AtomicLong ticker = new AtomicLong();
        TestCache cache = new TestCache(CacheAdapterTest::values, ticker, 1);
        Value original = cache.getOrLoad("guild");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Value> operation = worker.submit(() -> cache.with("guild", value -> {
                entered.countDown();
                await(finish);
                value.count = 42;
                return value;
            }));
            await(entered);
            ticker.set(TimeUnit.MINUTES.toNanos(2));
            cache.put("other", new Value("other"));
            cache.getSize();
            assertSame(original, cache.getOrLoad("guild"));
            finish.countDown();
            assertSame(original, operation.get(2, TimeUnit.SECONDS));
            assertSame(original, cache.getOrLoad("guild"));
            assertEquals(42, original.count);
        } finally {
            finish.countDown();
            worker.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void invalidationFencesAnInFlightLoad() throws Exception {
        verifyDiscardDuringLoad(false);
    }

    @Test(timeout = 5000)
    public void clearFencesAnInFlightLoad() throws Exception {
        verifyDiscardDuringLoad(true);
    }

    @Test
    public void loaderFailureIsPropagatedAndCanBeRetried() {
        AtomicInteger loads = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("database unavailable");
        TestCache cache = new TestCache(keys -> {
            if (loads.incrementAndGet() == 1)
                throw failure;
            return values(keys);
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> cache.getOrLoadAll(List.of("a", "b"))));
        assertEquals(0, cache.getSize());
        assertEquals(2, cache.getOrLoadAll(List.of("a", "b")).size());
    }

    @Test
    public void missingValuesAreNotNegativelyCached() {
        AtomicInteger loads = new AtomicInteger();
        TestCache cache = new TestCache(keys -> {
            loads.incrementAndGet();
            return Map.of();
        });
        assertNull(cache.getOrLoad("missing"));
        assertNull(cache.getOrLoad("missing"));
        assertEquals(2, loads.get());
        assertThrows(NoSuchElementException.class, () -> cache.with("missing", value -> value.id));
    }

    @Test(timeout = 5000)
    public void callbacksSerializeChangesToTheSameValue() throws Exception {
        TestCache cache = new TestCache(CacheAdapterTest::values);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int index = 0; index < 100; index++)
                results.add(workers.submit(() -> cache.with("guild", value -> ++value.count)));
            for (Future<Integer> result : results)
                result.get(2, TimeUnit.SECONDS);
            assertEquals(100, cache.getOrLoad("guild").count);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void callbackFailureReleasesTheOperationAndNestedCallsAreReentrant() {
        TestCache cache = new TestCache(CacheAdapterTest::values);
        assertThrows(IllegalArgumentException.class, () -> cache.with("guild", value -> {
            throw new IllegalArgumentException("bad input");
        }));
        Value original = cache.getOrLoad("guild");
        assertSame(original, cache.with("guild", value -> cache.with("guild", nested -> nested)));
        cache.invalidate("guild");
        assertNotSame(original, cache.getOrLoad("guild"));
    }

    @Test(timeout = 5000)
    public void slowLoaderDoesNotBlockAnotherKey() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        TestCache cache = new TestCache(keys -> {
            if (keys.contains("slow")) {
                loading.countDown();
                await(finish);
            }
            return values(keys);
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Value> slow = workers.submit(() -> cache.getOrLoad("slow"));
            await(loading);
            Future<Value> fast = workers.submit(() -> cache.getOrLoad("fast"));
            assertEquals("fast", fast.get(1, TimeUnit.SECONDS).id);
            finish.countDown();
            slow.get(2, TimeUnit.SECONDS);
        } finally {
            finish.countDown();
            workers.shutdownNow();
        }
    }

    @Test(timeout = 5000)
    public void concurrentCreationRunsTheFactoryOnce() throws Exception {
        TestCache cache = new TestCache(keys -> Map.of());
        AtomicInteger creations = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(8);
        CyclicBarrier start = new CyclicBarrier(8);
        try {
            List<Future<Value>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++)
                results.add(workers.submit(() -> {
                    start.await();
                    return cache.getOrCreate("guild", key -> {
                        creations.incrementAndGet();
                        return new Value(key);
                    });
                }));
            Value first = results.get(0).get(2, TimeUnit.SECONDS);
            for (Future<Value> result : results)
                assertSame(first, result.get(2, TimeUnit.SECONDS));
            assertEquals(1, creations.get());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    public void writeExpirationIsNotExtendedByACacheHit() {
        AtomicLong ticker = new AtomicLong();
        TestCache cache = new TestCache(CacheAdapterTest::values, ticker, 100);
        Value first = cache.getOrLoad("sound");
        ticker.set(TimeUnit.SECONDS.toNanos(50));
        assertSame(first, cache.getOrLoad("sound"));
        assertEquals(10_000, cache.expiresAfter("sound"));
        ticker.set(TimeUnit.SECONDS.toNanos(61));
        assertNotSame(first, cache.getOrLoad("sound"));
    }

    @Test
    public void replacementsWaitForTheActiveOperationAndRespectInvalidationOrder() {
        TestCache cache = new TestCache(CacheAdapterTest::values);
        Value replacement = new Value("replacement");
        cache.with("guild", value -> {
            cache.put("guild", replacement);
            assertSame(value, cache.getOrLoad("guild"));
            return null;
        });
        assertSame(replacement, cache.getOrLoad("guild"));
        cache.with("guild", value -> {
            cache.put("guild", new Value("discarded"));
            cache.invalidate("guild");
            return null;
        });
        assertNull(cache.get("guild"));
    }

    @Test(timeout = 5000)
    public void failedLoadReleasesOverlappingBulkWaiters() throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch otherLoading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        IllegalStateException failure = new IllegalStateException("storage failed");
        AtomicInteger attempts = new AtomicInteger();
        TestCache cache = new TestCache(keys -> {
            if (keys.contains("a") && attempts.incrementAndGet() == 1) {
                loading.countDown();
                await(finish);
                throw failure;
            }
            otherLoading.countDown();
            return values(keys);
        });
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Value> first = workers.submit(() -> cache.getOrLoad("a"));
            await(loading);
            Future<List<Value>> second = workers.submit(() -> cache.getOrLoadAll(List.of("a", "b")));
            await(otherLoading);
            finish.countDown();
            assertSame(failure, assertThrows(ExecutionException.class, () -> first.get(2, TimeUnit.SECONDS)).getCause());
            assertSame(failure, assertThrows(ExecutionException.class, () -> second.get(2, TimeUnit.SECONDS)).getCause());
            assertEquals("a", cache.getOrLoad("a").id);
        } finally {
            finish.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    public void failedCreationCanBeRetried() {
        TestCache cache = new TestCache(keys -> Map.of());
        assertThrows(IllegalStateException.class, () -> cache.getOrCreate("guild", key -> {
            throw new IllegalStateException("insert failed");
        }));
        assertNull(cache.get("guild"));
        Value created = cache.getOrCreate("guild", Value::new);
        assertSame(created, cache.getOrLoad("guild"));
    }

    @Test
    public void bulkScopeRetainsEveryInstanceBeyondCapacityAndExpiration() {
        AtomicLong ticker = new AtomicLong();
        TestCache cache = new TestCache(CacheAdapterTest::values, ticker, 1);
        List<Value> result = cache.withAll(List.of("a", "b", "c"), loaded -> {
            ticker.set(TimeUnit.MINUTES.toNanos(2));
            cache.getSize();
            for (Value value : loaded) {
                assertSame(value, cache.getOrLoad(value.id));
                cache.with(value.id, current -> ++current.count);
            }
            return loaded;
        });
        assertEquals(3, result.size());
        for (Value value : result)
            assertEquals(1, value.count);
    }

    @Test
    public void bulkCallbackFailureReleasesAllEntries() {
        TestCache cache = new TestCache(CacheAdapterTest::values);
        assertThrows(IllegalStateException.class, () -> cache.withAll(List.of("a", "b"), loaded -> {
            cache.clear();
            throw new IllegalStateException("operation failed");
        }));
        assertEquals(0, cache.getSize());
        assertEquals(2, cache.getOrLoadAll(List.of("a", "b")).size());
    }

    /* -------------------------------------------------------------------------- */

    private void verifyDiscardDuringLoad(boolean clear) throws Exception {
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        TestCache cache = new TestCache(keys -> {
            if (loads.incrementAndGet() == 1) {
                loading.countDown();
                await(finish);
            }
            return values(keys);
        });
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Value> first = worker.submit(() -> cache.getOrLoad("guild"));
            await(loading);
            if (clear)
                cache.clear();
            else cache.invalidate("guild");
            finish.countDown();
            Value old = first.get(2, TimeUnit.SECONDS);
            assertNull(cache.get("guild"));
            assertNotSame(old, cache.getOrLoad("guild"));
            assertEquals(2, loads.get());
        } finally {
            finish.countDown();
            worker.shutdownNow();
        }
    }

    private static Map<String, Value> values(Collection<String> keys) {
        Map<String, Value> result = new HashMap<>();
        for (String key : keys)
            result.put(key, new Value(key));
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS))
                throw new AssertionError("Timed out waiting for test coordination");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class Value {
        private final String id;
        private int count;

        private Value(String id) {
            this.id = id;
        }
    }

    private static final class TestCache extends CacheAdapter<String, Value> {
        private final Function<Collection<String>, Map<String, Value>> loader;

        private TestCache(Function<Collection<String>, Map<String, Value>> loader) {
            this(loader, new AtomicLong(), 100);
        }

        private TestCache(Function<Collection<String>, Map<String, Value>> loader, AtomicLong ticker, int size) {
            super(size, 1, TimeUnit.MINUTES, ticker::get, false);
            this.loader = loader;
        }

        @Override
        protected Map<String, Value> loadValues(Collection<String> keys) {
            return loader.apply(keys);
        }
    }
}
