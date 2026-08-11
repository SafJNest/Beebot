package com.safjnest.lol.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class R4JQueue {

    private static final Map<LeagueShard, ExecutorService> EXECUTORS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<?>> REQUESTS = new ConcurrentHashMap<>();

    private R4JQueue() {
    }

    @SuppressWarnings("unchecked")
    public static <T> CompletableFuture<T> submit(
            LeagueShard shard,
            String operation,
            String id,
            Supplier<T> supplier) {
        if (shard == null || operation == null || operation.isBlank() || id == null || id.isBlank() || supplier == null) {
            return CompletableFuture.completedFuture(null);
        }

        String key = key(shard, operation, id);
        return (CompletableFuture<T>) REQUESTS.computeIfAbsent(key, ignored -> {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier, executor(shard));
            future.whenComplete((value, error) -> REQUESTS.remove(key, future));
            return future;
        });
    }

    public static void shutdown() {
        EXECUTORS.values().forEach(ExecutorService::shutdownNow);
        EXECUTORS.clear();
        REQUESTS.clear();
    }

    // ============================================================================

    private static ExecutorService executor(LeagueShard shard) {
        return EXECUTORS.computeIfAbsent(shard, ignored -> Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("r4j-" + shard.name().toLowerCase() + "-", 0).factory()
        ));
    }

    private static String key(LeagueShard shard, String operation, String id) {
        return shard.name() + ":" + operation + ":" + id;
    }
}
