package com.safjnest.lol.queue;

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
import java.util.function.Supplier;

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
    public void profileRefreshKeyUsesOnlyTheSummonerIdentity() {
        assertEquals("profile-refresh:puuid-1", DatabaseTracker.profileRefreshKey("puuid-1"));
    }

    @Test
    public void duplicateKeySharesQueuedFutureAndRunsOnce() throws Exception {
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch profileStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        CompletableFuture<Void> buildBlocker = submitBuild("test:queued-build-blocker", () -> {
            buildStarted.countDown();
            await(releaseBlockers);
            return null;
        });
        assertTrue(buildStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CompletableFuture<Void> blocker = submit("test:queued-blocker", () -> {
            profileStarted.countDown();
            await(releaseBlockers);
            return null;
        });

        assertTrue(profileStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        AtomicInteger executions = new AtomicInteger();
        CompletableFuture<Integer> first = submit("test:duplicate", () -> {
            executions.incrementAndGet();
            return 7;
        });
        CompletableFuture<Integer> second = submit("test:duplicate", () -> 99);

        assertSame(first, second);
        assertEquals(0, executions.get());
        releaseBlockers.countDown();
        assertEquals(Integer.valueOf(7), first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        blocker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        buildBlocker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(1, executions.get());
    }

    @Test
    public void duplicateKeySharesRunningFutureAndRunsOnce() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Integer> first = submit("test:running-duplicate", () -> {
            executions.incrementAndGet();
            started.countDown();
            await(release);
            return 7;
        });
        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<Integer> second = submit("test:running-duplicate", () -> 99);

        assertSame(first, second);
        release.countDown();
        assertEquals(Integer.valueOf(7), first.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, executions.get());
    }

    @Test
    public void manualSubmissionPromotesTheQueuedStaleTaskWithTheSameKey() throws Exception {
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch profileStarted = new CountDownLatch(1);
        CountDownLatch releaseProfile = new CountDownLatch(1);
        CountDownLatch releaseBuild = new CountDownLatch(1);
        CompletableFuture<Void> build = submitBuild("test:promotion-build", () -> {
            buildStarted.countDown();
            await(releaseBuild);
            return null;
        });
        assertTrue(buildStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CompletableFuture<Void> blocker = submit("test:promotion-blocker", () -> {
            profileStarted.countDown();
            await(releaseProfile);
            return null;
        });
        assertTrue(profileStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<Integer> stale = submitStale("test:promotion", () -> {
            return 7;
        });
        CompletableFuture<Void> onDemand = submit("test:promotion-on-demand", () -> {
            return null;
        });
        CompletableFuture<Integer> manual = submitManual("test:promotion", () -> 99);

        assertSame(stale, manual);
        releaseProfile.countDown();
        releaseBuild.countDown();
        assertEquals(Integer.valueOf(7), manual.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        onDemand.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        blocker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        build.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void profileWorkUsesAtMostBothDatabaseWorkers() throws Exception {
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch profilesStarted = new CountDownLatch(2);
        CountDownLatch releaseProfiles = new CountDownLatch(1);
        CountDownLatch releaseBuild = new CountDownLatch(1);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        CompletableFuture<Void> buildBlocker = submitBuild("test:parallel-build-blocker", () -> {
            buildStarted.countDown();
            await(releaseBuild);
            return null;
        });
        assertTrue(buildStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        for (int i = 0; i < 3; i++) {
            int task = i;
            futures.add(submit("test:parallel:" + task, () -> {
                int current = active.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                profilesStarted.countDown();
                await(releaseProfiles);
                active.decrementAndGet();
                return null;
            }));
        }

        releaseBuild.countDown();
        assertTrue(profilesStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(2, maximum.get());
        releaseProfiles.countDown();
        for (CompletableFuture<Void> future : futures) future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        buildBlocker.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(2, maximum.get());
    }

    @Test
    public void championWorkerCanRunAlongsideProfileWorker() throws Exception {
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch profileStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> build = submitBuild("test:dedicated-build", () -> {
            buildStarted.countDown();
            await(release);
            return null;
        });
        assertTrue(buildStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CompletableFuture<Void> general = submit("test:dedicated-general", () -> {
            profileStarted.countDown();
            await(release);
            return null;
        });

        assertTrue(profileStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
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
            futures.add(submitBuild("test:serial-build:" + task, () -> {
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

        CompletableFuture<Void> firstProfile = submit("test:profile-worker", () -> {
            profileWorkerStarted.countDown();
            await(release);
            return null;
        });
        assertTrue(profileWorkerStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<Void> secondProfile = submit("test:champion-worker-profile", () -> {
            championProfileStarted.countDown();
            await(release);
            return null;
        });
        assertTrue(championProfileStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<Void> build = submitBuild("test:second-worker-build", () -> {
            buildStarted.countDown();
            await(release);
            return null;
        });
        release.countDown();
        firstProfile.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        secondProfile.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(buildStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        build.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void failedWorkerDoesNotStopAnotherTask() throws Exception {
        CountDownLatch survivorStarted = new CountDownLatch(1);
        CompletableFuture<Integer> failed = submit("test:worker-failure", () -> {
            throw new IllegalStateException("expected");
        });
        CompletableFuture<Integer> survivor = submit("test:worker-survivor", () -> {
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
        CompletableFuture<Integer> failed = submit("test:retry", () -> {
            executions.incrementAndGet();
            throw new IllegalStateException("expected");
        });

        try {
            failed.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException expected) {
            assertTrue(expected.getCause() instanceof IllegalStateException);
        }

        CompletableFuture<Integer> retry = submit("test:retry", () -> {
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

    private static <T> CompletableFuture<T> submit(String key, Supplier<T> supplier) {
        return schedule(key, DatabaseWorkerType.PROFILE, QueuePriority.NORMAL, supplier);
    }

    private static <T> CompletableFuture<T> submitBuild(String key, Supplier<T> supplier) {
        return schedule(key, DatabaseWorkerType.CHAMPION, QueuePriority.NORMAL, supplier);
    }

    private static <T> CompletableFuture<T> submitManual(String key, Supplier<T> supplier) {
        return schedule(key, DatabaseWorkerType.PROFILE, QueuePriority.IMMEDIATE, supplier);
    }

    private static <T> CompletableFuture<T> submitStale(String key, Supplier<T> supplier) {
        return schedule(key, DatabaseWorkerType.PROFILE, QueuePriority.BACKGROUND, supplier);
    }

    private static <T> CompletableFuture<T> schedule(
        String key,
        DatabaseWorkerType route,
        QueuePriority priority,
        Supplier<T> supplier
    ) {
        return DatabaseTracker.schedule(new QueueRequest<>(key, key, route, priority, supplier));
    }
}
