package com.safjnest.lol.queue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class RiotRequestDispatcher extends AbstractRequestDispatcher<LeagueShard> {

    private static final RiotRequestDispatcher INSTANCE = new RiotRequestDispatcher();
    private static volatile boolean loggingEnabled;

    private RiotRequestDispatcher() {
        super("riot", "Riot request cancelled during shutdown");
    }

    public static <T> Request<LeagueShard, T> request(
        LeagueShard shard,
        String operation,
        String id,
        Supplier<T> supplier
    ) {
        return request(shard, operation, id, RequestPriority.IMMEDIATE, supplier);
    }

    public static <T> Request<LeagueShard, T> request(
        LeagueShard shard,
        String operation,
        String id,
        RequestPriority priority,
        Supplier<T> supplier
    ) {
        String key = shard.name() + ":" + operation + ":" + id;
        RequestPriority resolved = priority == RequestPriority.BACKGROUND
            ? RequestPriority.BACKGROUND
            : RequestPriority.IMMEDIATE;
        return new Request<>(key, key, shard, resolved, supplier);
    }

    public static <T> CompletableFuture<T> schedule(Request<LeagueShard, T> request) {
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
        INSTANCE.shutdownDispatcher();
    }

    public static com.safjnest.lol.model.status.RequestDispatcherStatus status() {
        return INSTANCE.snapshot();
    }

    // ============================================================================

    @Override
    protected String routeName(LeagueShard shard) {
        return shard.name();
    }

    @Override
    protected String workerThreadName(LeagueShard shard) {
        return "r4j-" + shard.name().toLowerCase() + "-";
    }

    @Override
    protected void onQueued(RequestTask<LeagueShard, ?> task) {
        log("Queued " + task.priority() + " request key=" + task.key());
    }

    @Override
    protected void onReused(RequestTask<LeagueShard, ?> task) {
        log("Reusing request key=" + task.key());
    }

    @Override
    protected void onStarted(RequestTask<LeagueShard, ?> task) {
        log("Started " + task.priority() + " request key=" + task.key());
    }

    @Override
    protected void onCompleted(RequestTask<LeagueShard, ?> task) {
        log("Completed request key=" + task.key());
    }

    @Override
    protected void onFailed(RequestTask<LeagueShard, ?> task, Throwable failure) {
        log("Failed request key=" + task.key() + " error=" + failure.getClass().getSimpleName()
            + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
    }

    private static void log(String message) {
        if (loggingEnabled) BotLogger.info("[RiotRequestDispatcher] " + message);
    }
}
