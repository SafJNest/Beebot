package com.safjnest.lol.queue.scheduler;

import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class RiotScheduler extends AbstractScheduler<LeagueShard> {

    private static final RiotScheduler INSTANCE = new RiotScheduler();
    private static volatile boolean loggingEnabled;

    private RiotScheduler() {
        super("riot", "Riot request cancelled during shutdown");
    }

    public static AbstractScheduler<LeagueShard> scheduler() {
        return INSTANCE;
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

    public static com.safjnest.lol.model.status.SchedulerStatus status() {
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
    protected LeagueShard routeForJob(Object route) {
        if (route instanceof LeagueShard shard && shard != LeagueShard.UNKNOWN) return shard;
        throw new IllegalArgumentException("Riot jobs require a LeagueShard");
    }

    @Override
    protected void onQueued(com.safjnest.lol.queue.job.Job<?> job) {
        log("Queued " + job.priority() + " request key=" + job.key());
    }

    @Override
    protected void onStarted(com.safjnest.lol.queue.job.Job<?> job) {
        log("Started " + job.priority() + " request key=" + job.key());
    }

    @Override
    protected void onBodyCompleted(com.safjnest.lol.queue.job.Job<?> job) {
        log("Released worker key=" + job.key());
    }

    @Override
    protected void onBodyFailed(com.safjnest.lol.queue.job.Job<?> job, Throwable failure) {
        log("Failed request key=" + job.key() + " error=" + failure.getClass().getSimpleName()
            + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
    }

    private static void log(String message) {
        if (loggingEnabled) BotLogger.info("[RiotScheduler] " + message);
    }
}
