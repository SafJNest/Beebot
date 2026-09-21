package com.safjnest.lol.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.scheduler.AbstractScheduler;

public class RouterTest {

    private final Registry registry = new Registry(Clock.systemUTC());
    private final Router router = new Router(registry);
    private final SyncTestScheduler sync = new SyncTestScheduler(registry);
    private final RiotTestScheduler riot = new RiotTestScheduler(registry);

    @After
    public void shutdown() {
        sync.shutdownDispatcher();
        riot.shutdownDispatcher();
    }

    @Test
    public void rejectsDuplicateSchedulerClasses() {
        router.register(sync);

        assertThrows(IllegalStateException.class, () -> router.register(new SyncTestScheduler(registry)));
    }

    @Test
    public void rejectsUnknownSchedulerClass() {
        assertThrows(IllegalArgumentException.class,
            () -> router.submit(MissingScheduler.class, "EUW", "key", "name", JobPriority.IMMEDIATE, ignored -> null));
    }

    @Test
    public void assignsPidAndPpidAcrossSchedulers() throws Exception {
        router.register(sync);
        router.register(riot);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch childRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "global", "root", "root", JobPriority.IMMEDIATE, parent -> {
            router.submit(RiotTestScheduler.class, "EUW", "child", "child", JobPriority.IMMEDIATE, ignored -> {
                childStarted.countDown();
                await(childRelease);
                return null;
            });
            return null;
        });

        assertTrue(childStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.snapshot();
        assertEquals(2, jobs.size());
        assertEquals(0, jobs.get(0).ppid());
        assertEquals(jobs.get(0).pid(), jobs.get(1).ppid());
        childRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void keepsPriorityLocalToEachRoute() throws Exception {
        router.register(sync);
        CountDownLatch euwRelease = new CountDownLatch(1);
        CountDownLatch euwStarted = new CountDownLatch(1);
        CountDownLatch naCompleted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        var blocker = router.submit(SyncTestScheduler.class, "EUW", "euw-running", "euw running", JobPriority.NORMAL, ignored -> {
            euwStarted.countDown();
            await(euwRelease);
            return calls.incrementAndGet();
        });
        assertTrue(euwStarted.await(2, TimeUnit.SECONDS));
        var background = router.submit(SyncTestScheduler.class, "EUW", "euw-background", "euw background", JobPriority.BACKGROUND,
            ignored -> calls.incrementAndGet());
        var immediate = router.submit(SyncTestScheduler.class, "EUW", "euw-immediate", "euw immediate", JobPriority.IMMEDIATE,
            ignored -> calls.incrementAndGet());
        var na = router.submit(SyncTestScheduler.class, "NA", "na", "na", JobPriority.BACKGROUND, ignored -> {
            naCompleted.countDown();
            return calls.incrementAndGet();
        });

        assertTrue(naCompleted.await(2, TimeUnit.SECONDS));
        euwRelease.countDown();
        blocker.get(2, TimeUnit.SECONDS);
        immediate.get(2, TimeUnit.SECONDS);
        background.get(2, TimeUnit.SECONDS);
        na.get(2, TimeUnit.SECONDS);
        assertEquals(4, calls.get());
    }

    @Test
    public void allowsAnImmediateChildAboveItsBackgroundParentPriority() throws Exception {
        router.register(sync);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch childRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "background-root", "background root", JobPriority.BACKGROUND,
            parent -> {
                router.submit(SyncTestScheduler.class, "EUW", "immediate-child", "immediate child", JobPriority.IMMEDIATE, child -> {
                    childStarted.countDown();
                    await(childRelease);
                    return null;
                });
                return null;
            });

        assertTrue(childStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.snapshot();
        assertEquals(JobPriority.IMMEDIATE, jobs.get(1).priority());
        childRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void releasesTheWorkerWhileAParentWaitsForItsOwnRoute() throws Exception {
        router.register(sync);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch childRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, parent -> {
            router.submit(SyncTestScheduler.class, "EUW", "child", "child", JobPriority.IMMEDIATE, ignored -> {
                childStarted.countDown();
                await(childRelease);
                return null;
            });
            return null;
        });

        assertTrue(childStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.snapshot();
        assertEquals(2, jobs.size());
        assertEquals(jobs.get(0).pid(), jobs.get(1).ppid());
        childRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void queuesChildrenThroughTheSamePriorityLanes() throws Exception {
        router.register(sync);
        CountDownLatch parentStarted = new CountDownLatch(1);
        CountDownLatch releaseParent = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());

        var parent = router.submit(SyncTestScheduler.class, "EUW", "parent", "parent", JobPriority.NORMAL, job -> {
            parentStarted.countDown();
            await(releaseParent);
            router.submit(SyncTestScheduler.class, "EUW", "child", "child", JobPriority.BACKGROUND, ignored -> {
                order.add("child");
                return null;
            });
            order.add("parent");
            return null;
        });
        assertTrue(parentStarted.await(2, TimeUnit.SECONDS));
        router.submit(SyncTestScheduler.class, "EUW", "immediate", "immediate", JobPriority.IMMEDIATE, ignored -> {
            order.add("immediate");
            return null;
        });

        releaseParent.countDown();
        parent.get(2, TimeUnit.SECONDS);
        assertEquals(List.of("parent", "immediate", "child"), order);
    }

    @Test
    public void restoresTheParentForAnExplicitAsyncCallback() throws Exception {
        router.register(sync);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch childRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, parent -> {
            registry.retain(parent);
            CompletableFuture.runAsync(() -> registry.resume(parent, () ->
                router.submit(SyncTestScheduler.class, "EUW", "child", "child", JobPriority.IMMEDIATE, ignored -> {
                    childStarted.countDown();
                    await(childRelease);
                    return null;
                })
            ));
            return null;
        });

        assertTrue(childStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.snapshot();
        assertEquals(2, jobs.size());
        assertEquals(jobs.get(0).pid(), jobs.get(1).ppid());
        childRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void createsAFollowerInsteadOfQueueingADuplicate() throws Exception {
        router.register(sync);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();

        var first = router.submit(SyncTestScheduler.class, "EUW", "same", "same", JobPriority.NORMAL, ignored -> {
            started.countDown();
            await(release);
            return calls.incrementAndGet();
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        var follower = router.submit(SyncTestScheduler.class, "EUW", "same", "same", JobPriority.IMMEDIATE,
            ignored -> calls.incrementAndGet());

        var jobs = registry.snapshot();
        assertEquals(jobs.get(0).pid(), jobs.get(1).followingPid().longValue());
        release.countDown();
        assertEquals(Integer.valueOf(1), first.get(2, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(1), follower.get(2, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void promotesAQueuedSourceWhenAnImmediateFollowerArrives() throws Exception {
        router.register(sync);
        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch blockerRelease = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger calls = new AtomicInteger();

        var blocker = router.submit(SyncTestScheduler.class, "EUW", "blocker", "blocker", JobPriority.IMMEDIATE, ignored -> {
            blockerStarted.countDown();
            await(blockerRelease);
            return null;
        });
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));
        var source = router.submit(SyncTestScheduler.class, "EUW", "rank", "rank", JobPriority.BACKGROUND, ignored -> {
            order.add("rank");
            return calls.incrementAndGet();
        });
        var normal = router.submit(SyncTestScheduler.class, "EUW", "normal", "normal", JobPriority.NORMAL, ignored -> {
            order.add("normal");
            return null;
        });
        var follower = router.submit(SyncTestScheduler.class, "EUW", "rank", "rank", JobPriority.IMMEDIATE,
            ignored -> calls.incrementAndGet());

        blockerRelease.countDown();
        blocker.get(2, TimeUnit.SECONDS);
        assertEquals(Integer.valueOf(1), source.get(2, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(1), follower.get(2, TimeUnit.SECONDS));
        normal.get(2, TimeUnit.SECONDS);
        assertEquals(List.of("rank", "normal"), order);
        assertEquals(1, calls.get());
    }

    @Test
    public void removesCompletedJobTreesFromTheRegistry() throws Exception {
        router.register(sync);

        router.submit(SyncTestScheduler.class, "EUW", "ordinary", "ordinary", JobPriority.IMMEDIATE, ignored -> null)
            .get(2, TimeUnit.SECONDS);
        router.submit(SyncTestScheduler.class, "EUW", "tracking", "tracking", JobPriority.IMMEDIATE, parent -> {
            router.submit(SyncTestScheduler.class, "EUW", "tracking-child", "tracking child", JobPriority.IMMEDIATE,
                ignored -> null);
            return null;
        }).get(2, TimeUnit.SECONDS);

        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    public void aggregatesDescendantProgressForEveryParent() throws Exception {
        router.register(sync);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, ignored -> {
            router.submit(SyncTestScheduler.class, "EUW", "shard", "shard", JobPriority.IMMEDIATE, shard -> {
                router.submit(SyncTestScheduler.class, "EUW", "summoner-1", "summoner 1", JobPriority.IMMEDIATE, job -> {
                    job.done("summoner-1");
                    return null;
                });
                router.submit(SyncTestScheduler.class, "EUW", "summoner-2", "summoner 2", JobPriority.IMMEDIATE, job -> {
                    secondStarted.countDown();
                    await(secondRelease);
                    job.done("summoner-2");
                    return null;
                });
                return null;
            });
            return null;
        });

        assertTrue(secondStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.statusSnapshot(3);
        assertEquals(new com.safjnest.lol.model.status.JobProgress(0, 1), jobs.get(0).progress());
        assertEquals(new com.safjnest.lol.model.status.JobProgress(1, 2), jobs.get(1).progress());

        secondRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(registry.snapshot().isEmpty());
    }

    @Test
    public void countsOnlyDirectChildrenInParentProgress() throws Exception {
        router.register(sync);
        CountDownLatch firstLeafStarted = new CountDownLatch(1);
        CountDownLatch firstLeafRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, ignored -> {
            router.submit(SyncTestScheduler.class, "EUW", "first", "first", JobPriority.IMMEDIATE, first -> {
                router.submit(SyncTestScheduler.class, "EUW", "first-a", "first a", JobPriority.IMMEDIATE, leaf -> {
                    firstLeafStarted.countDown();
                    await(firstLeafRelease);
                    return null;
                });
                router.submit(SyncTestScheduler.class, "EUW", "first-b", "first b", JobPriority.IMMEDIATE, leaf -> null);
                return null;
            });
            router.submit(SyncTestScheduler.class, "EUW", "second", "second", JobPriority.IMMEDIATE, second -> {
                router.submit(SyncTestScheduler.class, "EUW", "second-a", "second a", JobPriority.IMMEDIATE, leaf -> null);
                router.submit(SyncTestScheduler.class, "EUW", "second-b", "second b", JobPriority.IMMEDIATE, leaf -> null);
                return null;
            });
            return null;
        });

        assertTrue(firstLeafStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.statusSnapshot(3, 100);
        assertEquals(new com.safjnest.lol.model.status.JobProgress(0, 2), jobs.get(0).progress());

        firstLeafRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void capsTheStatusTreeAndOmitsPerItemDetails() throws Exception {
        router.register(sync);
        CountDownLatch fourthStarted = new CountDownLatch(1);
        CountDownLatch fourthRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, first -> {
            router.submit(SyncTestScheduler.class, "EUW", "second", "second", JobPriority.IMMEDIATE, second -> {
                second.trackItem("hidden-puuid");
                router.submit(SyncTestScheduler.class, "EUW", "third", "third", JobPriority.IMMEDIATE, third -> {
                    router.submit(SyncTestScheduler.class, "EUW", "fourth", "fourth", JobPriority.IMMEDIATE, fourth -> {
                        fourthStarted.countDown();
                        await(fourthRelease);
                        return null;
                    });
                    return null;
                });
                return null;
            });
            return null;
        });

        assertTrue(fourthStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.statusSnapshot(3);
        assertEquals(3, jobs.size());
        assertEquals("root", jobs.get(0).key());
        assertEquals("second", jobs.get(1).key());
        assertEquals("third", jobs.get(2).key());
        assertTrue(jobs.get(1).items().isEmpty());
        assertTrue(jobs.get(2).children().isEmpty());

        var fourthLevel = registry.statusSnapshot(3, 1);
        assertEquals(4, fourthLevel.size());
        assertEquals("fourth", fourthLevel.get(3).key());
        assertEquals(List.of(fourthLevel.get(3).pid()), fourthLevel.get(2).children());

        fourthRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(registry.snapshot().isEmpty());
    }

    private static final class SyncTestScheduler extends TestScheduler {

        private SyncTestScheduler(Registry registry) {
            super("sync-test", registry);
        }
    }

    private static final class RiotTestScheduler extends TestScheduler {

        private RiotTestScheduler(Registry registry) {
            super("riot-test", registry);
        }
    }

    private static final class MissingScheduler {
    }

    private abstract static class TestScheduler extends AbstractScheduler<String> {

        private TestScheduler(String id, Registry registry) {
            super(id, "test shutdown", registry);
        }

        @Override
        protected String routeName(String route) {
            return route;
        }

        @Override
        protected String workerThreadName(String route) {
            return "test-" + route + '-';
        }

        @Override
        protected String routeForJob(Object route) {
            if (route instanceof String value) return value;
            throw new IllegalArgumentException("route");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean awaitEmpty(Registry registry) {
        long timeout = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < timeout) {
            if (registry.snapshot().isEmpty()) return true;
            Thread.yield();
        }
        return registry.snapshot().isEmpty();
    }
}
