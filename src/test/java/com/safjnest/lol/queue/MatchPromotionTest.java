package com.safjnest.lol.queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.queue.scheduler.SyncScheduler;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class MatchPromotionTest {
    @Test
    public void immediateMatchPromotesTheWaitingRiotChild() throws Exception {
        LeagueShard shard = LeagueShard.EUW1;
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queued = new CountDownLatch(1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        var blocker = QueueHandler.immediate(RiotScheduler.class, shard, "promotion-test-blocker", "blocker", job -> {
            blocked.countDown();
            try { release.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            return null;
        });
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            var match = QueueHandler.background(SyncScheduler.class, shard, "match:promotion-test", "match", job -> {
                var child = QueueHandler.background(RiotScheduler.class, shard, "promotion-test-riot", "riot", request -> {
                    order.add("match");
                    return 1;
                });
                queued.countDown();
                return child.join();
            });
            assertTrue(queued.await(5, TimeUnit.SECONDS));
            var normal = QueueHandler.normal(RiotScheduler.class, shard, "promotion-test-normal", "normal", job -> {
                order.add("normal");
                return null;
            });
            var follower = QueueHandler.immediate(SyncScheduler.class, shard, "match:promotion-test", "match", job -> 2);
            release.countDown();
            blocker.get(5, TimeUnit.SECONDS);
            assertEquals(Integer.valueOf(1), match.get(5, TimeUnit.SECONDS));
            assertEquals(Integer.valueOf(1), follower.get(5, TimeUnit.SECONDS));
            normal.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("match", "normal"), order);
        } finally {
            release.countDown();
        }
    }
}
