package com.safjnest.lol.scheduler;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.hive.Hive;
import com.hive.HiveConfig;
import com.hive.job.JobPriority;

public class ComputeSchedulerTest {

    @Test
    public void preservesFourWorkersAndInsertTimePlacementWithLogicalDedup() throws Exception {
        try (Hive hive = new Hive(HiveConfig.defaults("compute-tests"))) {
            ComputeScheduler scheduler = new ComputeScheduler(hive.registry()); hive.register(scheduler);
            assertEquals(List.of("profile", "profile-2", "champion", "mongo"), scheduler.snapshot().queues().stream().map(queue -> queue.route()).toList());
            CountDownLatch started = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var source = hive.router().submit(ComputeScheduler.class, DatabaseWorkerType.PROFILE, "profile-data", "profile data", JobPriority.NORMAL, String.class, job -> {
                started.countDown(); await(release); return "one";
            });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            var mongo = scheduler.snapshot().queues().get(3).worker();
            assertNotNull(mongo.currentJob()); assertEquals("PROFILE", mongo.currentJob().route());
            var follower = hive.router().submit(ComputeScheduler.class, DatabaseWorkerType.PROFILE, "profile-data", "profile data", JobPriority.IMMEDIATE, String.class, job -> "duplicate");
            assertEquals(2, hive.registry().snapshot().size());
            release.countDown(); assertEquals("one", source.get(3, TimeUnit.SECONDS)); assertEquals("one", follower.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void preservesReservationAndLeastLoadedTiePolicy() {
        assertEquals(DatabaseWorkerType.MONGO, ComputeScheduler.profileQueue(0, 0, 0, 0, false));
        assertEquals(DatabaseWorkerType.CHAMPION, ComputeScheduler.profileQueue(1, 1, 0, 1, false));
        assertEquals(DatabaseWorkerType.PROFILE, ComputeScheduler.profileQueue(0, 0, 2, 2, false));
        assertEquals(DatabaseWorkerType.PROFILE_2, ComputeScheduler.profileQueue(3, 1, 0, 0, true));
        assertEquals(DatabaseWorkerType.PROFILE, ComputeScheduler.profileQueue(1, 1, 0, 0, true));
        assertTrue(ComputeScheduler.isHeavyChampionTaskKey("champion-stats-matrix:patch:queue"));
        assertTrue(ComputeScheduler.isHeavyChampionTaskKey("champion-build:filter"));
        assertTrue(ComputeScheduler.isHeavyChampionTaskKey("champion-data-refresh:patch"));
        assertFalse(ComputeScheduler.isHeavyChampionTaskKey("profile-statistics:id:filter"));
    }

    @Test
    public void activeChampionWorkReservesComputeProfileWorkers() throws Exception {
        try (Hive hive = new Hive(HiveConfig.defaults("compute-tests"))) {
            ComputeScheduler scheduler = new ComputeScheduler(hive.registry()); hive.register(scheduler);
            CountDownLatch championStarted = new CountDownLatch(1); CountDownLatch profileStarted = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var champion = hive.router().submit(ComputeScheduler.class, DatabaseWorkerType.CHAMPION, "champion-stats-matrix:patch:queue", "champion", JobPriority.NORMAL, Void.class, job -> {
                championStarted.countDown(); await(release); return null;
            });
            assertTrue(championStarted.await(3, TimeUnit.SECONDS));
            var profile = hive.router().submit(ComputeScheduler.class, DatabaseWorkerType.PROFILE, "profile-statistics:id:filter", "profile", JobPriority.NORMAL, Void.class, job -> {
                profileStarted.countDown(); await(release); return null;
            });
            assertTrue(profileStarted.await(3, TimeUnit.SECONDS));
            assertNotNull(scheduler.snapshot().queues().get(0).worker().currentJob());
            assertNull(scheduler.snapshot().queues().get(3).worker().currentJob());
            release.countDown(); champion.get(3, TimeUnit.SECONDS); profile.get(3, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(3, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
