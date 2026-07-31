package com.safjnest.lol.tracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
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

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class DatabaseTrackerTest {

    private static final long TIMEOUT_SECONDS = 10;

    @AfterClass
    public static void shutdownWorkers() {
        DatabaseTracker.shutdown();
    }

    @Test
    public void championStatsMatrixKeyUsesPatchAndQueue() {
        String solo = DatabaseTracker.championStatsMatrixKey("15.14", GameQueueType.TEAM_BUILDER_RANKED_SOLO);
        String aram = DatabaseTracker.championStatsMatrixKey("15.14", GameQueueType.ARAM);

        assertEquals("champion-stats-matrix:15.14:TEAM_BUILDER_RANKED_SOLO", solo);
        assertEquals("champion-stats-matrix:15.14:ARAM", aram);
        assertNotEquals(solo, aram);
    }

    @Test
    public void duplicateKeySharesQueuedFutureAndRunsOnce() throws Exception {
        CountDownLatch blockersStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        CompletableFuture<Void> blocker = DatabaseTracker.submit("test:queued-blocker", () -> {
            blockersStarted.countDown();
            await(releaseBlockers);
            return null;
        });

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
        blocker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
    public void profileWorkerRunsOneTaskAtOnce() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
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
        assertEquals(1, maximum.get());
        release.countDown();
        for (CompletableFuture<Void> future : futures) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, maximum.get());
    }

    @Test
    public void championWorkerCanRunAlongsideProfileWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> build = DatabaseTracker.submitBuild("test:dedicated-build", () -> {
            started.countDown();
            await(release);
            return null;
        });
        CompletableFuture<Void> general = DatabaseTracker.submit("test:dedicated-general", () -> {
            started.countDown();
            await(release);
            return null;
        });

        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        release.countDown();
        build.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        general.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void championWorkerRunsOneBuildAtOnce() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            int task = i;
            futures.add(DatabaseTracker.submitBuild("test:serial-build:" + task, () -> {
                int current = active.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                started.countDown();
                await(release);
                active.decrementAndGet();
                return null;
            }));
        }

        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, maximum.get());
        release.countDown();
        for (CompletableFuture<Void> future : futures) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, maximum.get());
    }

    @Test
    public void championWorkStaysOnSecondWorkerWhileItCanHelpProfiles() throws Exception {
        CountDownLatch profileWorkerStarted = new CountDownLatch(1);
        CountDownLatch championProfileStarted = new CountDownLatch(1);
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> firstProfile = DatabaseTracker.submit("test:profile-worker", () -> {
            profileWorkerStarted.countDown();
            await(release);
            return null;
        });
        assertTrue(profileWorkerStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<Void> secondProfile = DatabaseTracker.submit("test:champion-worker-profile", () -> {
            championProfileStarted.countDown();
            await(release);
            return null;
        });
        assertTrue(championProfileStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<Void> build = DatabaseTracker.submitBuild("test:second-worker-build", () -> {
            buildStarted.countDown();
            await(release);
            return null;
        });
        release.countDown();
        firstProfile.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        secondProfile.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(buildStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        build.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        boolean secondWorkerRanBuild = false;
        for (DatabaseTracker.WorkerStatus status : DatabaseTracker.workerStatuses()) {
            if (status.id() == 2 && status.started() > 0) secondWorkerRanBuild = true;
        }
        assertTrue(secondWorkerRanBuild);
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
