package com.safjnest.lol.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.AfterClass;
import org.junit.Test;

public class DatabaseTrackerTest {

    private static final long TIMEOUT_SECONDS = 10;

    @AfterClass
    public static void shutdownWorkers() {
        DatabaseTracker.shutdown();
    }

    @Test
    public void duplicateKeySharesQueuedFutureAndRunsOnce() throws Exception {
        CountDownLatch blockersStarted = new CountDownLatch(2);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        List<CompletableFuture<Void>> blockers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            blockers.add(DatabaseTracker.submit("test:queued-blocker:" + i, () -> {
                blockersStarted.countDown();
                await(releaseBlockers);
                return null;
            }));
        }

        assertTrue(blockersStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        AtomicInteger executions = new AtomicInteger();
        CompletableFuture<Integer> first = DatabaseTracker.submit("test:duplicate", () -> {
            executions.incrementAndGet();
            return 7;
        });
        CompletableFuture<Integer> second = DatabaseTracker.submit("test:duplicate", () -> 99);

        assertSame(first, second);
        assertEquals(0, executions.get());
        releaseBlockers.countDown();
        assertEquals(Integer.valueOf(7), first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        for (CompletableFuture<Void> blocker : blockers) blocker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, executions.get());
    }

    @Test
    public void duplicateKeySharesRunningFutureAndRunsOnce() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Integer> first = DatabaseTracker.submit("test:running-duplicate", () -> {
            executions.incrementAndGet();
            started.countDown();
            await(release);
            return 7;
        });
        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<Integer> second = DatabaseTracker.submit("test:running-duplicate", () -> 99);

        assertSame(first, second);
        release.countDown();
        assertEquals(Integer.valueOf(7), first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, executions.get());
    }

    @Test
    public void neverRunsMoreThanTwoTasksAtOnce() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            int task = i;
            futures.add(DatabaseTracker.submit("test:parallel:" + task, () -> {
                int current = active.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                started.countDown();
                await(release);
                active.decrementAndGet();
                return null;
            }));
        }

        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(2, maximum.get());
        release.countDown();
        for (CompletableFuture<Void> future : futures) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(2, maximum.get());
    }

    @Test
    public void failedWorkerDoesNotStopAnotherTask() throws Exception {
        CountDownLatch survivorStarted = new CountDownLatch(1);
        CompletableFuture<Integer> failed = DatabaseTracker.submit("test:worker-failure", () -> {
            throw new IllegalStateException("expected");
        });
        CompletableFuture<Integer> survivor = DatabaseTracker.submit("test:worker-survivor", () -> {
            survivorStarted.countDown();
            return 11;
        });

        try {
            failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }
        assertTrue(survivorStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(11), survivor.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void failedTaskFreesKeyForRetry() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CompletableFuture<Integer> failed = DatabaseTracker.submit("test:retry", () -> {
            executions.incrementAndGet();
            throw new IllegalStateException("expected");
        });

        try {
            failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }

        CompletableFuture<Integer> retry = DatabaseTracker.submit("test:retry", () -> {
            executions.incrementAndGet();
            return 8;
        });

        assertEquals(Integer.valueOf(8), retry.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(2, executions.get());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
