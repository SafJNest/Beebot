package com.hive;

import static org.junit.Assert.*;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.hive.job.Job;
import com.hive.job.JobPriority;
import com.hive.job.ResultType;
import com.hive.scheduler.AbstractScheduler;
import com.hive.status.JobProgress;
import com.hive.job.JobState;

public class HiveLifecycleTest {

    @Test
    public void isolatesRuntimeIdentityDedupContextAndForeignJobs() throws Exception {
        try (Hive first = hive(); Hive second = hive()) {
            first.register(new Queued(first.registry()));
            second.register(new Queued(second.registry()));
            AtomicReference<Job<String>> foreign = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            CompletableFuture<String> left = first.router().submit(Queued.class, "alpha", "same", "first", JobPriority.NORMAL, String.class, job -> {
                foreign.set(job); started.countDown(); await(release); return "first";
            });
            CompletableFuture<String> right = second.router().submit(Queued.class, "alpha", "same", "second", JobPriority.NORMAL, String.class, job -> {
                started.countDown(); await(release); return "second";
            });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> second.registry().retain(foreign.get()));
            assertEquals(1, first.registry().snapshot().size());
            assertEquals(1, second.registry().snapshot().size());
            release.countDown();
            assertEquals("first", left.get(3, TimeUnit.SECONDS));
            assertEquals("second", right.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void rejectsForeignRegistrationsInvalidRoutesAndIncompatibleResults() throws Exception {
        try (Hive hive = hive(); Hive foreign = hive()) {
            Queued queued = new Queued(hive.registry()); hive.register(queued);
            assertThrows(IllegalArgumentException.class, () -> foreign.register(queued));
            assertThrows(IllegalArgumentException.class, () -> hive.router().submit(Queued.class, null, "x", "x", JobPriority.NORMAL, String.class, job -> "x"));
            CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var source = hive.router().submit(Queued.class, "alpha", "same", "same", JobPriority.NORMAL, String.class, job -> {
                entered.countDown(); await(release); return "text";
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> hive.router().submit(Queued.class, "alpha", "same", "same", JobPriority.NORMAL, Integer.class, job -> 1));
            assertEquals(1, hive.registry().snapshot().size());
            release.countDown(); assertEquals("text", source.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void validatesListElementContractsBeforeDedupAndAtExecution() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Queued(hive.registry()));
            CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var source = hive.router().submit(Queued.class, "alpha", "list", "list", JobPriority.NORMAL, ResultType.list(String.class), job -> {
                entered.countDown(); await(release); return List.of("value");
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> hive.router().submit(Queued.class, "alpha", "list", "list", JobPriority.NORMAL, ResultType.list(Integer.class), job -> List.of(1)));
            release.countDown(); assertEquals(List.of("value"), source.get(3, TimeUnit.SECONDS));
            assertThrows(ClassCastException.class, () -> ResultType.list(String.class).cast(List.of(1)));
        }
    }

    @Test
    public void futureCancellationDoesNotCancelPhysicalSourceOrItsFollowers() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Queued(hive.registry()));
            CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            AtomicInteger executions = new AtomicInteger();
            var source = hive.router().submit(Queued.class, "alpha", "x", "x", JobPriority.NORMAL, Integer.class, job -> {
                entered.countDown(); await(release); return executions.incrementAndGet();
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var follower = hive.router().submit(Queued.class, "alpha", "x", "x", JobPriority.IMMEDIATE, Integer.class, job -> 9);
            assertTrue(source.cancel(true));
            release.countDown(); assertEquals(Integer.valueOf(1), follower.get(3, TimeUnit.SECONDS));
            assertEquals(1, executions.get());
        }
    }

    @Test
    public void cancellingRunningWorkKeepsItsSerialWorkerUsable() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Queued(hive.registry()));
            AtomicReference<Job<Void>> running = new AtomicReference<>(); CountDownLatch entered = new CountDownLatch(1);
            var source = hive.router().submit(Queued.class, "alpha", "cancel", "cancel", JobPriority.NORMAL, Void.class, job -> {
                running.set(job); entered.countDown(); await(new CountDownLatch(1)); return null;
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            hive.registry().cancel(running.get());
            assertCancelled(source);
            assertEquals("next", hive.router().submit(Queued.class, "alpha", "next", "next", JobPriority.NORMAL, String.class, job -> "next").get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void cancellationOfWaitingParentCancelsItsChildrenAndParentFuture() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            AtomicReference<Job<String>> parent = new AtomicReference<>(); CountDownLatch childEntered = new CountDownLatch(1);
            var root = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, String.class, job -> {
                parent.set(job);
                hive.router().submit(Direct.class, "alpha", "child", "child", JobPriority.NORMAL, Void.class, child -> {
                    childEntered.countDown(); await(new CountDownLatch(1)); return null;
                });
                return "root";
            });
            assertTrue(childEntered.await(3, TimeUnit.SECONDS));
            awaitState(hive.registry(), "root", JobState.WAITING_CHILDREN);
            hive.registry().cancel(parent.get()); assertCancelled(root);
        }
    }

    @Test
    public void shutdownCancelsQueuedAndDirectWorkAndRejectsNewAdmission() throws Exception {
        Hive hive = hive();
        try {
            hive.register(new Queued(hive.registry())); hive.register(new Direct(hive.registry()));
            CountDownLatch entered = new CountDownLatch(2);
            var serial = hive.router().submit(Queued.class, "alpha", "serial", "serial", JobPriority.NORMAL, Void.class, job -> {
                entered.countDown(); await(new CountDownLatch(1)); return null;
            });
            var direct = hive.router().submit(Direct.class, "alpha", "direct", "direct", JobPriority.NORMAL, Void.class, job -> {
                entered.countDown(); await(new CountDownLatch(1)); return null;
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var queued = hive.router().submit(Queued.class, "alpha", "queued", "queued", JobPriority.NORMAL, Void.class, job -> failBody());
            hive.close(); assertCancelled(serial); assertCancelled(direct); assertCancelled(queued);
            assertTrue(awaitEmpty(hive.registry()));
            assertThrows(IllegalStateException.class, () -> hive.router().submit(Direct.class, "alpha", "new", "new", JobPriority.NORMAL, Void.class, job -> null));
        } finally { hive.close(); }
    }

    @Test
    public void propagatesBodyAndEnqueueFailuresToFollowersAndCleansUp() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Queued(hive.registry())); hive.register(new Rejecting(hive.registry()));
            CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var source = hive.router().submit(Queued.class, "alpha", "bad", "bad", JobPriority.NORMAL, Void.class, job -> {
                entered.countDown(); await(release); throw new IllegalArgumentException("body");
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var follower = hive.router().submit(Queued.class, "alpha", "bad", "bad", JobPriority.NORMAL, Void.class, job -> null);
            release.countDown(); assertFailure(source, IllegalArgumentException.class); assertFailure(follower, IllegalArgumentException.class);
            assertFailure(hive.router().submit(Rejecting.class, "alpha", "rejected", "rejected", JobPriority.NORMAL, Void.class, job -> null), IllegalStateException.class);
            assertTrue(awaitEmpty(hive.registry()));
        }
    }

    @Test
    public void failedChildrenCompleteParentNormallyWithItsOwnResult() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            var root = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, String.class, job -> {
                hive.router().submit(Direct.class, "beta", "failure", "failure", JobPriority.NORMAL, Void.class, child -> { throw new IllegalStateException("child"); });
                return "parent";
            });
            assertEquals("parent", root.get(3, TimeUnit.SECONDS));
            assertTrue(awaitEmpty(hive.registry()));
        }
    }

    @Test
    public void retainedCallbackRestoresParentAndPropagatesItsFailure() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            AtomicReference<Job<String>> retained = new AtomicReference<>(); CountDownLatch entered = new CountDownLatch(1);
            var root = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, String.class, job -> {
                hive.registry().retain(job); retained.set(job); entered.countDown(); return "parent";
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS)); awaitState(hive.registry(), "root", JobState.WAITING_CHILDREN);
            assertFalse(root.isDone());
            assertThrows(IllegalArgumentException.class, () -> hive.registry().resume(retained.get(), () -> {
                assertSame(retained.get(), hive.registry().currentJob()); throw new IllegalArgumentException("callback");
            }));
            assertFailure(root, IllegalArgumentException.class); assertNull(hive.registry().currentJob());
        }
    }

    @Test
    public void rejectsUnbalancedResumeWithoutExecutingCallback() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            AtomicReference<Job<Void>> running = new AtomicReference<>(); CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var source = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, Void.class, job -> {
                running.set(job); entered.countDown(); await(release); return null;
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> hive.registry().resume(running.get(), () -> fail("executed")));
            release.countDown(); source.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void ownedContinuationsRetainContextAndReleaseForExceptionalSource() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            CompletableFuture<String> delayed = new CompletableFuture<>(); CountDownLatch registered = new CountDownLatch(1);
            AtomicReference<CompletableFuture<String>> callback = new AtomicReference<>();
            var root = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, String.class, parent -> {
                callback.set(hive.thenApply(delayed, value -> {
                    assertSame(parent, hive.registry().currentJob()); return value;
                }));
                registered.countDown(); return "parent";
            });
            assertTrue(registered.await(3, TimeUnit.SECONDS)); awaitState(hive.registry(), "root", JobState.WAITING_CHILDREN);
            assertFalse(root.isDone()); delayed.completeExceptionally(new IllegalArgumentException("source"));
            assertFailure(callback.get(), IllegalArgumentException.class);
            assertEquals("parent", root.get(3, TimeUnit.SECONDS)); assertTrue(awaitEmpty(hive.registry()));
        }
    }

    @Test
    public void futureCallbackCanJoinAnotherWorkerWithoutHoldingRegistryMonitor() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Queued(hive.registry()));
            CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var second = hive.router().submit(Queued.class, "beta", "second", "second", JobPriority.NORMAL, String.class, job -> {
                entered.countDown(); await(release); return "second";
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var first = hive.router().submit(Queued.class, "alpha", "first", "first", JobPriority.NORMAL, String.class, job -> "first");
            var callback = first.thenApply(value -> { release.countDown(); return second.join(); });
            assertEquals("second", callback.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void explicitProgressWinsOverDirectChildrenAndSnapshotsHonorTotalLimit() throws Exception {
        HiveConfig config = new HiveConfig("limited", Clock.systemUTC(), 1, 2, Duration.ofSeconds(2), (job, event) -> {});
        try (Hive hive = new Hive(config)) {
            hive.register(new Direct(hive.registry())); CountDownLatch created = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            var root = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, Void.class, job -> {
                job.setProgressTotal(8); job.done("done"); job.missing("missing"); job.failed("failed"); job.phase("READING"); job.currentItem("item");
                for (int index = 0; index < 5; index++) {
                    hive.router().submit(Direct.class, "beta", "child" + index, "child", JobPriority.NORMAL, Void.class, child -> { await(release); return null; });
                }
                created.countDown(); return null;
            });
            assertTrue(created.await(3, TimeUnit.SECONDS)); awaitState(hive.registry(), "root", JobState.WAITING_CHILDREN);
            var snapshot = hive.registry().statusSnapshot(1, 100);
            assertEquals(2, snapshot.size()); assertEquals(new JobProgress(3, 8), snapshot.get(0).progress());
            assertEquals("READING", snapshot.get(0).phase()); assertEquals("item", snapshot.get(0).currentItem()); assertTrue(snapshot.get(0).items().isEmpty());
            release.countDown(); root.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    public void thenComposeRetainsParentUntilTheInnerFutureCompletes() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            CompletableFuture<String> inner = new CompletableFuture<>(); CountDownLatch callbackStarted = new CountDownLatch(1);
            AtomicReference<CompletableFuture<String>> composed = new AtomicReference<>();
            var root = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, String.class, parent -> {
                composed.set(hive.thenCompose(CompletableFuture.completedFuture("source"), value -> {
                    assertSame(parent, hive.registry().currentJob()); callbackStarted.countDown(); return inner;
                }));
                return "parent";
            });
            assertTrue(callbackStarted.await(3, TimeUnit.SECONDS)); awaitState(hive.registry(), "root", JobState.WAITING_CHILDREN);
            assertFalse(root.isDone()); inner.complete("inner");
            assertEquals("inner", composed.get().get(3, TimeUnit.SECONDS)); assertEquals("parent", root.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    public void completedChildBeforeParentReturnStillCountsTowardProgressAndErrors() throws Exception {
        try (Hive hive = hive()) {
            Registry registry = hive.registry();
            Job<String> root = registry.create(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, ResultType.of(String.class), parent -> {
                Job<Void> child = registry.create(Direct.class, "beta", "child", "child", JobPriority.BACKGROUND, ResultType.of(Void.class), job -> {
                    throw new IllegalArgumentException("child");
                });
                assertEquals(JobPriority.NORMAL, child.priority());
                registry.started(child);
                assertThrows(IllegalArgumentException.class, () -> registry.execute(child));
                registry.released(child);
                assertEquals(new JobProgress(1, 1), registry.status(parent).progress());
                return "parent";
            });
            registry.started(root); registry.execute(root);
            assertEquals(JobState.COMPLETED_WITH_ERRORS, registry.status(root).state());
            assertEquals("parent", root.future().get(3, TimeUnit.SECONDS));
            registry.released(root); assertTrue(registry.snapshot().isEmpty());
        }
    }

    @Test
    public void normalJobsUseFifoAndRunningSourceIsNeverPreempted() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Queued(hive.registry()));
            CountDownLatch entered = new CountDownLatch(1); CountDownLatch release = new CountDownLatch(1);
            List<String> order = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            var running = hive.router().submit(Queued.class, "alpha", "running", "running", JobPriority.BACKGROUND, String.class, job -> {
                entered.countDown(); await(release); order.add("running"); return "running";
            });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var first = hive.router().submit(Queued.class, "alpha", "first", "first", JobPriority.NORMAL, String.class, job -> { order.add("first"); return "first"; });
            var second = hive.router().submit(Queued.class, "alpha", "second", "second", JobPriority.NORMAL, String.class, job -> { order.add("second"); return "second"; });
            var urgent = hive.router().submit(Queued.class, "alpha", "urgent", "urgent", JobPriority.IMMEDIATE, String.class, job -> { order.add("urgent"); return "urgent"; });
            var follower = hive.router().submit(Queued.class, "alpha", "running", "running", JobPriority.IMMEDIATE, String.class, job -> { fail("duplicate"); return null; });
            assertEquals(JobPriority.BACKGROUND, hive.registry().snapshot().get(0).priority());
            assertTrue(order.isEmpty()); release.countDown();
            running.get(3, TimeUnit.SECONDS); follower.get(3, TimeUnit.SECONDS); first.get(3, TimeUnit.SECONDS); second.get(3, TimeUnit.SECONDS); urgent.get(3, TimeUnit.SECONDS);
            assertEquals(List.of("running", "urgent", "first", "second"), order);
        }
    }

    @Test
    public void rejectsDedupFollowerCyclesWithoutLeakingChildren() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            var root = hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, String.class, job -> {
                assertThrows(IllegalArgumentException.class, () -> hive.router().submit(Direct.class, "alpha", "root", "root", JobPriority.NORMAL, String.class, child -> "child"));
                return "parent";
            });
            assertEquals("parent", root.get(3, TimeUnit.SECONDS)); assertTrue(awaitEmpty(hive.registry()));
        }
    }

    @Test
    public void rejectsMutualFollowerDependenciesAcrossConcurrentRootTrees() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Direct(hive.registry()));
            CountDownLatch secondEntered = new CountDownLatch(1); CountDownLatch followerCreated = new CountDownLatch(1);
            var first = hive.router().submit(Direct.class, "alpha", "first", "first", JobPriority.NORMAL, String.class, job -> {
                await(secondEntered);
                hive.router().submit(Direct.class, "alpha", "second", "second follower", JobPriority.NORMAL, String.class, child -> "duplicate");
                followerCreated.countDown(); return "first";
            });
            var second = hive.router().submit(Direct.class, "alpha", "second", "second", JobPriority.NORMAL, String.class, job -> {
                secondEntered.countDown(); await(followerCreated);
                assertThrows(IllegalArgumentException.class, () -> hive.router().submit(Direct.class, "alpha", "first", "first follower", JobPriority.NORMAL, String.class, child -> "duplicate"));
                return "second";
            });
            assertEquals("first", first.get(3, TimeUnit.SECONDS)); assertEquals("second", second.get(3, TimeUnit.SECONDS));
            assertTrue(awaitEmpty(hive.registry()));
        }
    }

    @Test
    public void followerPromotesSourceBeforeItsPhysicalDispatch() throws Exception {
        try (Hive hive = hive()) {
            DelayedDispatch scheduler = new DelayedDispatch(hive.registry()); hive.register(scheduler);
            var submission = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                var sourceSubmission = submission.submit(() -> hive.router().submit(DelayedDispatch.class, "alpha", "same", "same", JobPriority.BACKGROUND, String.class, job -> {
                    assertEquals(JobPriority.IMMEDIATE, job.priority()); return "one";
                }));
                assertTrue(scheduler.dispatchEntered.await(3, TimeUnit.SECONDS));
                var follower = hive.router().submit(DelayedDispatch.class, "alpha", "same", "same", JobPriority.IMMEDIATE, String.class, job -> "duplicate");
                scheduler.dispatchRelease.countDown();
                assertEquals("one", sourceSubmission.get(3, TimeUnit.SECONDS).get(3, TimeUnit.SECONDS));
                assertEquals("one", follower.get(3, TimeUnit.SECONDS));
            } finally { scheduler.dispatchRelease.countDown(); submission.shutdownNow(); }
        }
    }

    @Test
    public void promotionToleratesACompletedSourceAfterPhysicalRelease() throws Exception {
        try (Hive hive = hive()) {
            hive.register(new Queued(hive.registry()));
            AtomicReference<Job<String>> source = new AtomicReference<>();
            var completed = hive.router().submit(Queued.class, "alpha", "source", "source", JobPriority.BACKGROUND, String.class, job -> {
                source.set(job); return "done";
            });
            assertEquals("done", completed.get(3, TimeUnit.SECONDS));
            long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!hive.registry().snapshot().isEmpty() && System.nanoTime() < limit) Thread.yield();
            assertTrue(awaitEmpty(hive.registry()));
            assertFalse(hive.registry().promotePending(source.get(), JobPriority.IMMEDIATE));
            assertTrue(hive.registry().bodyFinished(source.get()));
            assertNull(hive.registry().status(source.get()));
            assertFalse(hive.router().scheduler(Queued.class).promote(source.get(), JobPriority.IMMEDIATE));
        }
    }

    private static Hive hive() { return new Hive(new HiveConfig("tests", Clock.systemUTC(), 20, 1000, Duration.ofSeconds(2), (job, event) -> {})); }
    private static Void failBody() { fail("queued body executed after shutdown"); return null; }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("latch timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CancellationException("interrupted"); }
    }
    private static void assertCancelled(CompletableFuture<?> future) throws Exception {
        try { future.get(3, TimeUnit.SECONDS); fail("future completed successfully"); }
        catch (CancellationException expected) {}
        catch (ExecutionException exception) { assertTrue(exception.getCause() instanceof CancellationException); }
    }
    private static void assertFailure(CompletableFuture<?> future, Class<? extends Throwable> type) throws Exception {
        try { future.get(3, TimeUnit.SECONDS); fail("future completed successfully"); }
        catch (ExecutionException exception) { assertTrue(exception.getCause().toString(), type.isInstance(exception.getCause())); }
    }
    private static boolean awaitEmpty(Registry registry) {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limit) {
            if (registry.snapshot().isEmpty()) return true;
            Thread.yield();
        }
        return registry.snapshot().isEmpty();
    }
    private static void awaitState(Registry registry, String key, JobState expected) {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < limit) {
            if (registry.snapshot().stream().anyMatch(job -> key.equals(job.key()) && job.state() == expected)) return;
            Thread.yield();
        }
        fail("state " + expected + " missing for " + key);
    }

    private static class Queued extends AbstractScheduler<String> {
        Queued(Registry registry) { this(registry, ExecutionMode.QUEUED); }
        Queued(Registry registry, ExecutionMode mode) { super("generic", "test shutdown", registry, mode); }
        @Override protected String routeName(String route) { return route; }
        @Override protected String workerThreadName(String route) { return "generic-" + route + "-"; }
        @Override protected String routeForJob(Object route) {
            if (route instanceof String text && !text.isBlank()) return text;
            throw new IllegalArgumentException("generic route");
        }
    }
    private static final class Direct extends Queued { Direct(Registry registry) { super(registry, ExecutionMode.DIRECT); } }
    private static final class DelayedDispatch extends Queued {
        private final CountDownLatch dispatchEntered = new CountDownLatch(1);
        private final CountDownLatch dispatchRelease = new CountDownLatch(1);
        DelayedDispatch(Registry registry) { super(registry); }
        @Override public <T> void enqueue(Job<T> job) {
            dispatchEntered.countDown(); await(dispatchRelease); super.enqueue(job);
        }
    }
    private static final class Rejecting extends Queued {
        Rejecting(Registry registry) { super(registry); }
        @Override public <T> void enqueue(Job<T> job) { throw new IllegalStateException("enqueue failed"); }
    }
}
