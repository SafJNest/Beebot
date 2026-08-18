package com.safjnest.lol.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class R4JQueue {

    public enum Priority {
        HIGH,
        LOW
    }

    private static final Map<LeagueShard, ShardQueue> QUEUES = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<?>> REQUESTS = new ConcurrentHashMap<>();
    private static volatile boolean loggingEnabled;

    private R4JQueue() {
    }

    public static <T> CompletableFuture<T> submit(
            LeagueShard shard,
            String operation,
            String id,
            Supplier<T> supplier) {
        return submit(shard, operation, id, Priority.HIGH, supplier);
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> submit(
            LeagueShard shard,
            String operation,
            String id,
            Priority priority,
            Supplier<T> supplier) {
        if (shard == null || operation == null || operation.isBlank() || id == null || id.isBlank() || supplier == null) {
            log("Rejected invalid request");
            return CompletableFuture.completedFuture(null);
        }

        String key = key(shard, operation, id);
        CompletableFuture<?> existing = REQUESTS.get(key);
        if (existing != null) {
            log("Reusing request key=" + key);
            return (CompletableFuture<T>) existing;
        }

        Priority requestPriority = priority == Priority.LOW ? Priority.LOW : Priority.HIGH;
        return (CompletableFuture<T>) REQUESTS.computeIfAbsent(key, ignored -> {
            CompletableFuture<T> future = queue(shard).submit(key, requestPriority, supplier);
            future.whenComplete((value, error) -> {
                if (error == null) {
                    log("Completed request key=" + key);
                } else {
                    log("Failed request key=" + key + " error=" + error.getClass().getSimpleName()
                        + (error.getMessage() == null ? "" : ": " + error.getMessage()));
                }
                REQUESTS.remove(key, future);
            });
            return future;
        });
    }

    public static synchronized boolean toggleLogging() {
        loggingEnabled = !loggingEnabled;
        return loggingEnabled;
    }

    public static boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public static void shutdown() {
        log("Shutting down queue");
        QUEUES.values().forEach(ShardQueue::shutdown);
        QUEUES.clear();
        REQUESTS.clear();
    }

    // ============================================================================

    private static ShardQueue queue(LeagueShard shard) {
        return QUEUES.computeIfAbsent(shard, ShardQueue::new);
    }

    private static String key(LeagueShard shard, String operation, String id) {
        return shard.name() + ":" + operation + ":" + id;
    }

    private static void log(String message) {
        if (loggingEnabled) BotLogger.info("[R4JQueue] " + message);
    }

    private static final class ShardQueue {

        private final LinkedBlockingQueue<Request<?>> high = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Request<?>> low = new LinkedBlockingQueue<>();
        private final Semaphore pending = new Semaphore(0);
        private final ExecutorService worker;

        private ShardQueue(LeagueShard shard) {
            worker = Executors.newSingleThreadExecutor(
                Thread.ofVirtual().name("r4j-" + shard.name().toLowerCase() + "-", 0).factory()
            );
            worker.submit(this::run);
        }

        private <T> CompletableFuture<T> submit(String key, Priority priority, Supplier<T> supplier) {
            CompletableFuture<T> future = new CompletableFuture<>();
            Request<T> request = new Request<>(key, priority, supplier, future);
            (priority == Priority.LOW ? low : high).offer(request);
            pending.release();
            log("Queued " + priority + " request key=" + key);
            return future;
        }

        private void shutdown() {
            worker.shutdownNow();
            high.clear();
            low.clear();
        }

        private void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    pending.acquire();
                    Request<?> request = high.poll();
                    if (request == null) request = low.poll();
                    if (request != null) request.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private record Request<T>(String key, Priority priority, Supplier<T> supplier, CompletableFuture<T> future) {

        private void run() {
            try {
                log("Started " + priority + " request key=" + key);
                future.complete(supplier.get());
            } catch (Throwable exception) {
                future.completeExceptionally(exception);
            }
        }
    }
}
