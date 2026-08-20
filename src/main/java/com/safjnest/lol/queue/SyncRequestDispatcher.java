package com.safjnest.lol.queue;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import com.safjnest.lol.model.status.RequestDispatcherStatus;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class SyncRequestDispatcher extends AbstractRequestDispatcher<LeagueShard> {

    private static final SyncRequestDispatcher INSTANCE = new SyncRequestDispatcher();

    private SyncRequestDispatcher() {
        super("sync", "Sync request cancelled during shutdown");
    }

    public static <T> CompletableFuture<T> schedule(Request<LeagueShard, T> request) {
        return INSTANCE.enqueue(request);
    }

    public static <T> CompletableFuture<T> submit(
            String key,
            String name,
            LeagueShard shard,
            RequestPriority priority,
            Supplier<T> supplier) {
        return schedule(new Request<>(key, name, shard, priority, supplier));
    }

    public static <T> CompletableFuture<T> submit(
            String key,
            String name,
            LeagueShard shard,
            RequestPriority priority,
            Function<RequestTask<LeagueShard, T>, T> work) {
        return schedule(new Request<>(key, name, shard, priority, work, null));
    }

    public static <T> CompletableFuture<T> submit(
            String key,
            String name,
            LeagueShard shard,
            RequestPriority priority,
            RequestRun run,
            Supplier<T> supplier) {
        return schedule(new Request<>(key, name, shard, priority, supplier, run));
    }

    public static <T> CompletableFuture<T> submit(
            String key,
            String name,
            LeagueShard shard,
            RequestPriority priority,
            RequestRun run,
            Function<RequestTask<LeagueShard, T>, T> work) {
        return schedule(new Request<>(key, name, shard, priority, work, run));
    }

    public static RequestRun beginRun(String key, String type) {
        return INSTANCE.createRun(key, type);
    }

    public static void sealRun(RequestRun run) {
        INSTANCE.finishRun(run);
    }

    public static void shutdown() {
        INSTANCE.shutdownDispatcher();
    }

    public static RequestDispatcherStatus status() {
        return INSTANCE.snapshot();
    }

    @Override
    protected String routeName(LeagueShard shard) {
        return shard.name();
    }

    @Override
    protected String workerThreadName(LeagueShard shard) {
        return "lol-sync-" + shard.name().toLowerCase() + "-";
    }
}
