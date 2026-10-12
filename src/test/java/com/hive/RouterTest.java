package com.hive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Test;

import com.hive.job.JobPriority;
import com.hive.Registry;
import com.hive.Router;
import com.hive.Hive;
import com.hive.HiveConfig;
import com.hive.scheduler.AbstractScheduler;

public class RouterTest {

    private final Hive hive = new Hive(HiveConfig.defaults("router-test"));
    private final Registry registry = hive.registry();
    private final Router router = new Router(registry);
    private final SyncTestScheduler sync = new SyncTestScheduler(registry);
    private final RiotTestScheduler riot = new RiotTestScheduler(registry);

    @After
    public void shutdown() {
        hive.close();
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
            () -> router.submit(MissingScheduler.class, "EUW", "key", "name", JobPriority.IMMEDIATE, Void.class, ignored -> null));
    }

    @Test
    public void assignsPidAndPpidAcrossSchedulers() throws Exception {
        router.register(sync);
        router.register(riot);
        CountDownLatch childStarted = new CountDownLatch(1);
        CountDownLatch childRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "global", "root", "root", JobPriority.IMMEDIATE, Void.class, parent -> {
            router.submit(RiotTestScheduler.class, "EUW", "child", "child", JobPriority.IMMEDIATE, Void.class, ignored -> {
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

        var blocker = router.submit(SyncTestScheduler.class, "EUW", "euw-running", "euw running", JobPriority.NORMAL, Integer.class, ignored -> {
            euwStarted.countDown();
            await(euwRelease);
            return calls.incrementAndGet();
        });
        assertTrue(euwStarted.await(2, TimeUnit.SECONDS));
        var background = router.submit(SyncTestScheduler.class, "EUW", "euw-background", "euw background", JobPriority.BACKGROUND, Integer.class,
            ignored -> calls.incrementAndGet());
        var immediate = router.submit(SyncTestScheduler.class, "EUW", "euw-immediate", "euw immediate", JobPriority.IMMEDIATE, Integer.class,
            ignored -> calls.incrementAndGet());
        var na = router.submit(SyncTestScheduler.class, "NA", "na", "na", JobPriority.BACKGROUND, Integer.class, ignored -> {
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

        var root = router.submit(SyncTestScheduler.class, "EUW", "background-root", "background root", JobPriority.BACKGROUND, Void.class,
            parent -> {
                router.submit(SyncTestScheduler.class, "EUW", "immediate-child", "immediate child", JobPriority.IMMEDIATE, Void.class, child -> {
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

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, Void.class, parent -> {
            router.submit(SyncTestScheduler.class, "EUW", "child", "child", JobPriority.IMMEDIATE, Void.class, ignored -> {
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

        var parent = router.submit(SyncTestScheduler.class, "EUW", "parent", "parent", JobPriority.NORMAL, Void.class, job -> {
            parentStarted.countDown();
            await(releaseParent);
            router.submit(SyncTestScheduler.class, "EUW", "child", "child", JobPriority.BACKGROUND, Void.class, ignored -> {
                order.add("child");
                return null;
            });
            order.add("parent");
            return null;
        });
        assertTrue(parentStarted.await(2, TimeUnit.SECONDS));
        router.submit(SyncTestScheduler.class, "EUW", "immediate", "immediate", JobPriority.IMMEDIATE, Void.class, ignored -> {
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

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, Void.class, parent -> {
            registry.retain(parent);
            CompletableFuture.runAsync(() -> registry.resume(parent, () ->
                router.submit(SyncTestScheduler.class, "EUW", "child", "child", JobPriority.IMMEDIATE, Void.class, ignored -> {
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

        var first = router.submit(SyncTestScheduler.class, "EUW", "same", "same", JobPriority.NORMAL, Integer.class, ignored -> {
            started.countDown();
            await(release);
            return calls.incrementAndGet();
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        var follower = router.submit(SyncTestScheduler.class, "EUW", "same", "same", JobPriority.IMMEDIATE, Integer.class,
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

        var blocker = router.submit(SyncTestScheduler.class, "EUW", "blocker", "blocker", JobPriority.IMMEDIATE, Void.class, ignored -> {
            blockerStarted.countDown();
            await(blockerRelease);
            return null;
        });
        assertTrue(blockerStarted.await(2, TimeUnit.SECONDS));
        var source = router.submit(SyncTestScheduler.class, "EUW", "rank", "rank", JobPriority.BACKGROUND, Integer.class, ignored -> {
            order.add("rank");
            return calls.incrementAndGet();
        });
        var normal = router.submit(SyncTestScheduler.class, "EUW", "normal", "normal", JobPriority.NORMAL, Void.class, ignored -> {
            order.add("normal");
            return null;
        });
        var follower = router.submit(SyncTestScheduler.class, "EUW", "rank", "rank", JobPriority.IMMEDIATE, Integer.class,
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

        router.submit(SyncTestScheduler.class, "EUW", "ordinary", "ordinary", JobPriority.IMMEDIATE, Void.class, ignored -> null)
            .get(2, TimeUnit.SECONDS);
        router.submit(SyncTestScheduler.class, "EUW", "tracking", "tracking", JobPriority.IMMEDIATE, Void.class, parent -> {
            router.submit(SyncTestScheduler.class, "EUW", "tracking-child", "tracking child", JobPriority.IMMEDIATE, Void.class,
                ignored -> null);
            return null;
        }).get(2, TimeUnit.SECONDS);

        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void aggregatesDescendantProgressForEveryParent() throws Exception {
        router.register(sync);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, Void.class, ignored -> {
            router.submit(SyncTestScheduler.class, "EUW", "shard", "shard", JobPriority.IMMEDIATE, Void.class, shard -> {
                router.submit(SyncTestScheduler.class, "EUW", "summoner-1", "summoner 1", JobPriority.IMMEDIATE, Void.class, job -> {
                    job.done("summoner-1");
                    return null;
                });
                router.submit(SyncTestScheduler.class, "EUW", "summoner-2", "summoner 2", JobPriority.IMMEDIATE, Void.class, job -> {
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
        assertEquals(new com.hive.status.JobProgress(0, 1), jobs.get(0).progress());
        assertEquals(new com.hive.status.JobProgress(1, 2), jobs.get(1).progress());

        secondRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void countsOnlyDirectChildrenInParentProgress() throws Exception {
        router.register(sync);
        CountDownLatch firstLeafStarted = new CountDownLatch(1);
        CountDownLatch firstLeafRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, Void.class, ignored -> {
            router.submit(SyncTestScheduler.class, "EUW", "first", "first", JobPriority.IMMEDIATE, Void.class, first -> {
                router.submit(SyncTestScheduler.class, "EUW", "first-a", "first a", JobPriority.IMMEDIATE, Void.class, leaf -> {
                    firstLeafStarted.countDown();
                    await(firstLeafRelease);
                    return null;
                });
                router.submit(SyncTestScheduler.class, "EUW", "first-b", "first b", JobPriority.IMMEDIATE, Void.class, leaf -> null);
                return null;
            });
            router.submit(SyncTestScheduler.class, "EUW", "second", "second", JobPriority.IMMEDIATE, Void.class, second -> {
                router.submit(SyncTestScheduler.class, "EUW", "second-a", "second a", JobPriority.IMMEDIATE, Void.class, leaf -> null);
                router.submit(SyncTestScheduler.class, "EUW", "second-b", "second b", JobPriority.IMMEDIATE, Void.class, leaf -> null);
                return null;
            });
            return null;
        });

        assertTrue(firstLeafStarted.await(2, TimeUnit.SECONDS));
        var jobs = registry.statusSnapshot(3, 100);
        assertEquals(new com.hive.status.JobProgress(0, 2), jobs.get(0).progress());

        firstLeafRelease.countDown();
        root.get(2, TimeUnit.SECONDS);
        assertTrue(awaitEmpty(registry));
    }

    @Test
    public void capsTheStatusTreeAndOmitsPerItemDetails() throws Exception {
        router.register(sync);
        CountDownLatch fourthStarted = new CountDownLatch(1);
        CountDownLatch fourthRelease = new CountDownLatch(1);

        var root = router.submit(SyncTestScheduler.class, "EUW", "root", "root", JobPriority.IMMEDIATE, Void.class, first -> {
            router.submit(SyncTestScheduler.class, "EUW", "second", "second", JobPriority.IMMEDIATE, Void.class, second -> {
                second.trackItem("hidden-puuid");
                router.submit(SyncTestScheduler.class, "EUW", "third", "third", JobPriority.IMMEDIATE, Void.class, third -> {
                    router.submit(SyncTestScheduler.class, "EUW", "fourth", "fourth", JobPriority.IMMEDIATE, Void.class, fourth -> {
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
        assertTrue(awaitEmpty(registry));
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

    private static final class MissingScheduler extends TestScheduler {
        private MissingScheduler(Registry registry) { super("missing", registry); }
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
            assertTrue("latch timed out", latch.await(2, TimeUnit.SECONDS));
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
