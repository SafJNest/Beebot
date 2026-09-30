package com.safjnest.lol.queue.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.safjnest.lol.model.Filter;

public class ChampionMatrixRequestTest {

    @Test
    public void startReturnsImmutableSnapshotAndRejectsLaterBuilds() {
        ChampionMatrixRequest request = new ChampionMatrixRequest();
        Filter first = buildFilter(27);
        Filter later = buildFilter(412);

        assertTrue(request.tryAddBuild(first));
        List<Filter> buildFilters = request.startAndGetBuildFilters();

        assertEquals(1, buildFilters.size());
        assertEquals(first.toKey(), buildFilters.get(0).toKey());
        assertFalse(request.tryAddBuild(later));
        assertThrows(UnsupportedOperationException.class, () -> buildFilters.add(later));
        assertEquals(1, buildFilters.size());
    }

    @Test
    public void concurrentBuildRequestIsEitherInSnapshotOrRejected() throws Exception {
        ChampionMatrixRequest request = new ChampionMatrixRequest();
        Filter buildFilter = buildFilter(27);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> add = executor.submit(() -> {
                ready.countDown();
                start.await();
                return request.tryAddBuild(buildFilter);
            });
            Future<List<Filter>> snapshot = executor.submit(() -> {
                ready.countDown();
                start.await();
                return request.startAndGetBuildFilters();
            });

            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            boolean accepted = add.get(2, TimeUnit.SECONDS);
            List<Filter> buildFilters = snapshot.get(2, TimeUnit.SECONDS);
            assertEquals(accepted ? 1 : 0, buildFilters.size());
            if (accepted) assertEquals(buildFilter.toKey(), buildFilters.get(0).toKey());
            assertFalse(request.tryAddBuild(buildFilter(412)));
        } finally {
            executor.shutdownNow();
        }
    }

    private static Filter buildFilter(int champion) {
        return new Filter().setChampion(champion);
    }
}
