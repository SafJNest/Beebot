package com.safjnest.lol.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

public class AbstractQueueSchedulerTest {

    private TestQueueScheduler scheduler;

    @After
    public void shutdown() {
        if (scheduler != null) scheduler.shutdownScheduler();
    }

    @Test
    public void shouldDrainImmediateBeforeNormalBeforeBackground() throws Exception {
        scheduler = new TestQueueScheduler();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> order = new CopyOnWriteArrayList<>();

        CompletableFuture<Void> blocker = scheduler.publicSchedule(new QueueRequest<>(
            "blocker", "blocker", "main", QueuePriority.NORMAL, () -> {
                started.countDown();
                await(release);
                order.add("blocker");
                return null;
            }
        ));
        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<Void> background = scheduler.publicSchedule(new QueueRequest<>(
            "bg", "bg", "main", QueuePriority.BACKGROUND, () -> {
                order.add("background");
                return null;
            }
        ));
        CompletableFuture<Void> normal = scheduler.publicSchedule(new QueueRequest<>(
            "normal", "normal", "main", QueuePriority.NORMAL, () -> {
                order.add("normal");
                return null;
            }
        ));
        CompletableFuture<Void> immediate = scheduler.publicSchedule(new QueueRequest<>(
            "immediate", "immediate", "main", QueuePriority.IMMEDIATE, () -> {
                order.add("immediate");
                return null;
            }
        ));

        release.countDown();
        blocker.join();
        immediate.join();
        normal.join();
        background.join();
        assertEquals(List.of("blocker", "immediate", "normal", "background"), order);
    }

    @Test
    public void shouldKeepOneWorkerPerRegisteredRoute() {
        scheduler = new TestQueueScheduler();
        List<QueueWorkerStatus> statuses = scheduler.publicWorkerStatuses();
        assertEquals(2, statuses.size());
        assertEquals("main", statuses.get(0).type());
        assertEquals("helper", statuses.get(1).type());
        assertTrue(statuses.get(0).running());
        assertTrue(statuses.get(1).running());
    }

    @Test
    public void shouldCancelQueuedTasksOnShutdown() throws Exception {
        scheduler = new TestQueueScheduler();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> running = scheduler.publicSchedule(new QueueRequest<>(
            "running", "running", "main", QueuePriority.NORMAL, () -> {
                started.countDown();
                await(release);
                return null;
            }
        ));
        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<Void> queued = scheduler.publicSchedule(new QueueRequest<>(
            "queued", "queued", "main", QueuePriority.NORMAL, () -> null
        ));

        scheduler.shutdownScheduler();
        release.countDown();

        assertTrue(queued.isCompletedExceptionally());
        try {
            queued.join();
            throw new AssertionError("expected CancellationException");
        } catch (CompletionException exception) {
            assertTrue(exception.getCause() instanceof CancellationException);
            assertEquals("test shutdown", exception.getCause().getMessage());
        }
        running.join();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
