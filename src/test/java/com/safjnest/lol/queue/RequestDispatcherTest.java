package com.safjnest.lol.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

public class RequestDispatcherTest {

    private final TestDispatcher dispatcher = new TestDispatcher();

    @After
    public void shutdown() {
        dispatcher.shutdownDispatcher();
    }

    @Test
    public void deduplicatesActiveTasks() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Integer> first = dispatcher.submit("same", "EUW", RequestPriority.NORMAL, () -> {
            await(release);
            return calls.incrementAndGet();
        });
        CompletableFuture<Integer> second = dispatcher.submit("same", "EUW", RequestPriority.IMMEDIATE, calls::incrementAndGet);

        release.countDown();
        assertEquals(Integer.valueOf(1), first.get(2, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(1), second.get(2, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    public void prioritizesEachRouteIndependently() throws Exception {
        CountDownLatch euwRelease = new CountDownLatch(1);
        CountDownLatch naCompleted = new CountDownLatch(1);
        CompletableFuture<Void> blocker = dispatcher.submit("euw-running", "EUW", RequestPriority.NORMAL, () -> {
            await(euwRelease);
            return null;
        });
        CompletableFuture<Void> background = dispatcher.submit("euw-background", "EUW", RequestPriority.BACKGROUND, () -> null);
        CompletableFuture<Void> immediate = dispatcher.submit("euw-immediate", "EUW", RequestPriority.IMMEDIATE, () -> null);
        CompletableFuture<Void> na = dispatcher.submit("na", "NA", RequestPriority.BACKGROUND, () -> {
            naCompleted.countDown();
            return null;
        });

        assertTrue(naCompleted.await(2, TimeUnit.SECONDS));
        euwRelease.countDown();
        blocker.get(2, TimeUnit.SECONDS);
        immediate.get(2, TimeUnit.SECONDS);
        background.get(2, TimeUnit.SECONDS);
        na.get(2, TimeUnit.SECONDS);
        assertFalse(dispatcher.snapshot().queues().isEmpty());
    }

    @Test
    public void removesRunAfterItsLastTaskCompletes() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        RequestRun run = dispatcher.run("tracking", "TRACKING");
        CompletableFuture<Void> task = dispatcher.submit("run-task", "EUW", RequestPriority.IMMEDIATE, run, () -> {
            await(release);
            return null;
        });
        dispatcher.finish(run);

        assertEquals(1, dispatcher.snapshot().runs().size());
        release.countDown();
        task.get(2, TimeUnit.SECONDS);
        assertTrue(dispatcher.snapshot().runs().isEmpty());
    }

    @Test
    public void exposesTaskItemsWhileItsRunIsActive() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RequestRun run = dispatcher.run("sample", "SAMPLE_GAMES");
        CompletableFuture<Void> future = dispatcher.submit("sample-euw", "EUW", RequestPriority.BACKGROUND, run, task -> {
            task.phase("PERSISTING");
            task.trackItems(java.util.List.of("EUW1_1", "EUW1_2"));
            task.done("EUW1_1");
            started.countDown();
            await(release);
            task.missing("EUW1_2");
            return null;
        });
        dispatcher.finish(run);

        assertTrue(started.await(2, TimeUnit.SECONDS));
        var task = dispatcher.snapshot().runs().get(0).tasks().get(0);
        assertEquals("PERSISTING", task.phase());
        assertEquals(1, task.progress().current());
        assertEquals(2, task.progress().total());
        assertEquals("DONE", task.items().get("EUW1_1"));
        assertEquals("PENDING", task.items().get("EUW1_2"));

        release.countDown();
        future.get(2, TimeUnit.SECONDS);
    }

    private static final class TestDispatcher extends AbstractRequestDispatcher<String> {

        private TestDispatcher() {
            super("test", "test shutdown");
        }

        private <T> CompletableFuture<T> submit(String key, String route, RequestPriority priority, java.util.function.Supplier<T> supplier) {
            return enqueue(new Request<>(key, key, route, priority, supplier));
        }

        private <T> CompletableFuture<T> submit(String key, String route, RequestPriority priority, RequestRun run, java.util.function.Supplier<T> supplier) {
            return enqueue(new Request<>(key, key, route, priority, supplier, run));
        }

        private <T> CompletableFuture<T> submit(String key, String route, RequestPriority priority, RequestRun run, java.util.function.Function<RequestTask<String, T>, T> work) {
            return enqueue(new Request<>(key, key, route, priority, work, run));
        }

        private RequestRun run(String key, String type) {
            return createRun(key, type);
        }

        private void finish(RequestRun run) {
            finishRun(run);
        }

        @Override
        protected String routeName(String route) {
            return route;
        }

        @Override
        protected String workerThreadName(String route) {
            return "test-request-" + route + "-";
        }

        @Override
        protected boolean promoteOnReuse(String route) {
            return true;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
