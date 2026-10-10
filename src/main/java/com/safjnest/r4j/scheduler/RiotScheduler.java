package com.safjnest.r4j.scheduler;

import com.safjnest.utils.log.BotLogger;
import com.hive.Registry;
import com.hive.scheduler.AbstractScheduler;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class RiotScheduler extends AbstractScheduler<LeagueShard> {

    private volatile boolean loggingEnabled;

    public RiotScheduler(Registry registry) {
        super("riot", "Riot request cancelled during shutdown", registry);
    }

    public synchronized boolean toggleLogging() {
        loggingEnabled = !loggingEnabled;
        return loggingEnabled;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
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
    protected void onQueued(com.hive.job.Job<?> job) {
        log("Queued " + job.priority() + " request key=" + job.key());
    }

    @Override
    protected void onStarted(com.hive.job.Job<?> job) {
        log("Started " + job.priority() + " request key=" + job.key());
    }

    @Override
    protected void onBodyCompleted(com.hive.job.Job<?> job) {
        log("Released worker key=" + job.key());
    }

    @Override
    protected void onBodyFailed(com.hive.job.Job<?> job, Throwable failure) {
        log("Failed request key=" + job.key() + " error=" + failure.getClass().getSimpleName()
            + (failure.getMessage() == null ? "" : ": " + failure.getMessage()));
    }

    private void log(String message) {
        if (loggingEnabled) BotLogger.info("[RiotScheduler] " + message);
    }
}
