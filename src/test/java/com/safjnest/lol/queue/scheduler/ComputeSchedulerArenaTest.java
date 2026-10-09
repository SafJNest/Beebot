package com.safjnest.lol.queue.scheduler;

import static org.junit.Assert.*;
import static com.safjnest.lol.arena.ArenaGameParserTest.CATALOG;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import com.safjnest.lol.arena.ArenaItemCatalog;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.status.JobStatus;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.service.ChampionService;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public class ComputeSchedulerArenaTest {

    @After
    public void stopTestWorkers() {
        ComputeScheduler.shutdown();
    }

    @Test
    public void actualQueueUsesChampionBackgroundAndDeduplicatesTheArenaKey() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Job<?>> running = new AtomicReference<>();
        ChampionService service = new ChampionService() {
            @Override
            public boolean refreshArena(Filter filter, ArenaItemCatalog catalog, Job<?> job) {
                calls.incrementAndGet();
                running.set(job);
                started.countDown();
                await(release);
                return true;
            }
        };
        CompletableFuture<Boolean> first = null;
        CompletableFuture<Boolean> second = null;
        try {
            first = ComputeScheduler.startChampionArena(filter(), CATALOG, service);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            second = ComputeScheduler.startChampionArena(filter(), CATALOG, service);
            Job<?> job = running.get();
            assertEquals(DatabaseWorkerType.CHAMPION, job.route());
            assertEquals(JobPriority.BACKGROUND, job.priority());
            assertEquals("champion-arena:" + filter().toKey(), job.key());
            assertTrue(job.name().contains("champion=27 patch=16.19.1 shard=ALL"));
            assertTrue(ComputeScheduler.status().queues().stream()
                .anyMatch(queue -> queue.route().equals("champion") && queue.worker().currentJob() != null
                    && queue.worker().currentJob().pid() == job.pid()));
            boolean follower = false;
            for (JobStatus status : QueueHandler.snapshot())
                if (status.key().equals(job.key()) && status.followingPid() != null) follower = true;
            assertTrue(follower);
        } finally {
            release.countDown();
            if (first != null) assertTrue(first.get(5, TimeUnit.SECONDS));
            if (second != null) assertTrue(second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get());
    }

    @Test
    public void callerMutationDoesNotChangeQueuedFullPatchOrIdentity() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Filter> executed = new AtomicReference<>();
        ChampionService service = new ChampionService() {
            @Override
            public boolean refreshArena(Filter filter, ArenaItemCatalog catalog, Job<?> job) {
                started.countDown();
                await(release);
                executed.set(filter);
                return true;
            }
        };
        Filter request = filter();
        CompletableFuture<Boolean> future = ComputeScheduler.startChampionArena(request, CATALOG, service);
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            request.setChampion(99).setPatch("16.19.2");
        } finally {
            release.countDown();
            assertTrue(future.get(5, TimeUnit.SECONDS));
        }
        assertNotSame(request, executed.get());
        assertEquals(27, executed.get().champion());
        assertEquals("16.19.1", executed.get().patch());
    }

    @Test
    public void arenaReservesChampionWithoutChangingStandardHeavyKeys() {
        assertTrue(ComputeScheduler.isHeavyChampionTaskKey("champion-arena:any"));
        assertTrue(ComputeScheduler.isHeavyChampionTaskKey("champion-build:any"));
        assertTrue(ComputeScheduler.isHeavyChampionTaskKey("champion-stats-matrix:any"));
        assertTrue(ComputeScheduler.isHeavyChampionTaskKey("champion-data-refresh:any"));
        assertFalse(ComputeScheduler.isHeavyChampionTaskKey("profile-statistics:any"));
        assertEquals(DatabaseWorkerType.PROFILE_2, ComputeScheduler.profileQueue(5, 2, 0, 0, true));
    }

    @Test
    public void invalidScopeAndCatalogFailBeforeSubmittingAnyWork() {
        assertThrows(IllegalArgumentException.class,
            () -> ComputeScheduler.startChampionArena(filter().setPatch("16.19"), CATALOG));
        assertThrows(IllegalArgumentException.class,
            () -> ComputeScheduler.startChampionArena(filter().setRankBehavior(Filter.RankBehavior.EXACT), CATALOG));
        assertThrows(IllegalArgumentException.class,
            () -> ComputeScheduler.startChampionArena(filter().setQueue(GameQueueType.ARAM), CATALOG));
        assertThrows(NullPointerException.class, () -> ComputeScheduler.startChampionArena(filter(), null));
    }

    @Test
    public void actualQueuePropagatesFailureAndAllowsLaterRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ChampionService service = new ChampionService() {
            @Override
            public boolean refreshArena(Filter filter, ArenaItemCatalog catalog, Job<?> job) {
                if (attempts.incrementAndGet() == 1) throw new IllegalStateException("synthetic writer failure");
                return true;
            }
        };
        CompletableFuture<Boolean> failed = ComputeScheduler.startChampionArena(filter(), CATALOG, service);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
        assertTrue(ComputeScheduler.startChampionArena(filter(), CATALOG, service).get(5, TimeUnit.SECONDS));
        assertEquals(2, attempts.get());
    }

    private static Filter filter() {
        return Filter.championBuild(27, "16.19.1", GameQueueType.CHERRY);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
