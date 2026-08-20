package com.safjnest.lol.queue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.safjnest.lol.model.status.QueueWorkerStatus;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class R4JQueue extends AbstractQueueScheduler<LeagueShard> {

    private static final R4JQueue INSTANCE = new R4JQueue();
    private static volatile boolean loggingEnabled;

    private R4JQueue() {
        super("R4J task cancelled during shutdown");
    }

    public static <T> QueueRequest<LeagueShard, T> request(
        LeagueShard shard,
        String operation,
        String id,
        Supplier<T> supplier
    ) {
        return request(shard, operation, id, QueuePriority.IMMEDIATE, supplier);
    }

    public static <T> QueueRequest<LeagueShard, T> request(
        LeagueShard shard,
        String operation,
        String id,
        QueuePriority priority,
        Supplier<T> supplier
    ) {
        String key = shard.name() + ":" + operation + ":" + id;
        QueuePriority resolved = priority == QueuePriority.BACKGROUND
            ? QueuePriority.BACKGROUND
            : QueuePriority.IMMEDIATE;
        return new QueueRequest<>(key, key, shard, resolved, supplier);
    }

    public static <T> CompletableFuture<T> schedule(QueueRequest<LeagueShard, T> request) {
        if (request == null
            || request.route() == null
            || request.key() == null || request.key().isBlank()
            || request.supplier() == null) {
            log("Rejected invalid request");
            return CompletableFuture.completedFuture(null);
        }
        return INSTANCE.enqueue(request);
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
        INSTANCE.shutdownScheduler();
    }

    public static List<QueueWorkerStatus> workerStatuses() {
        return INSTANCE.schedulerWorkerStatuses();
    }

    // ============================================================================

    @Override
    protected String channelName(LeagueShard shard) {
        return shard.name().toLowerCase();
    }

    @Override
    protected String workerThreadName(LeagueShard shard) {
        return "r4j-" + shard.name().toLowerCase() + "-";
    }

    @Override
    protected void onQueued(QueueTask<LeagueShard, ?> task) {
        log("Queued " + task.priority() + " request key=" + task.key());
    }

    @Override
    protected void onReused(QueueTask<LeagueShard, ?> task) {
        log("Reusing request key=" + task.key());
    }

    @Override
    protected void onStarted(QueueTask<LeagueShard, ?> task) {
        log("Started " + task.priority() + " request key=" + task.key());
    }

    @Override
    protected void onCompleted(QueueTask<LeagueShard, ?> task) {
        log("Completed request key=" + task.key());
    }

    @Override
    protected void onFailed(QueueTask<LeagueShard, ?> task, Throwable failure) {
        log("Failed request key=" + task.key() + " error=" + failure.getClass().getSimpleName()
            + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
    }

    private static void log(String message) {
        if (loggingEnabled) BotLogger.info("[R4JQueue] " + message);
    }
}
