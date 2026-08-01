package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class R4JQueueTest {

    @Test
    public void shouldShareTheFutureForTheSameRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> first = R4JQueue.submit(LeagueShard.EUW1, "test-dedup", "same", () -> {
            calls.incrementAndGet();
            started.countDown();
            await(release);
            return calls.get();
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        CompletableFuture<Integer> second = R4JQueue.submit(LeagueShard.EUW1, "test-dedup", "same", calls::incrementAndGet);

        assertSame(first, second);
        release.countDown();
        assertEquals(1, first.join().intValue());
        assertEquals(1, calls.get());
    }

    @Test
    public void shouldSerializeRequestsForTheSameShard() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> first = R4JQueue.submit(LeagueShard.EUW1, "test-serial", "first", () -> {
            started.countDown();
            await(release);
            return 1;
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));

        CompletableFuture<Integer> second = R4JQueue.submit(LeagueShard.EUW1, "test-serial", "second", () -> 2);
        assertFalse(second.isDone());
        release.countDown();

        assertEquals(1, first.join().intValue());
        assertEquals(2, second.join().intValue());
    }

    @Test
    public void shouldAllowDifferentShardsToAdvanceIndependently() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> first = R4JQueue.submit(LeagueShard.EUW1, "test-shard", "first", () -> {
            firstStarted.countDown();
            await(release);
            return 1;
        });
        assertTrue(firstStarted.await(2, TimeUnit.SECONDS));

        CompletableFuture<Integer> second = R4JQueue.submit(LeagueShard.EUN1, "test-shard", "second", () -> 2);
        assertEquals(2, second.get(2, TimeUnit.SECONDS).intValue());
        release.countDown();
        assertEquals(1, first.join().intValue());
    }

    @Test
    public void shouldRemoveFailedRequestsForRetry() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Integer> failed = R4JQueue.submit(LeagueShard.EUW1, "test-retry", "same", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("failure");
        });
        try {
            failed.join();
        } catch (Exception ignored) {
        }

        CompletableFuture<Integer> retry = R4JQueue.submit(LeagueShard.EUW1, "test-retry", "same", calls::incrementAndGet);
        assertEquals(2, retry.join().intValue());
        assertEquals(2, calls.get());
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
