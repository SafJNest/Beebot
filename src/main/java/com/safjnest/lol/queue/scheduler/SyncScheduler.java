package com.safjnest.lol.queue.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.safjnest.lol.model.status.SchedulerStatus;
import com.safjnest.lol.queue.job.Job;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class SyncScheduler extends AbstractScheduler<LeagueShard> {

    private static final ExecutorService JOBS = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
    private static final SyncScheduler INSTANCE = new SyncScheduler();

    private SyncScheduler() {
        super("sync", "Sync request cancelled during shutdown");
    }

    public static AbstractScheduler<LeagueShard> scheduler() {
        return INSTANCE;
    }

    public static void shutdown() {
        JOBS.shutdownNow();
    }

    public static SchedulerStatus status() {
        return INSTANCE.snapshot();
    }

    @Override
    public <T> void enqueue(Job<T> job) {
        LeagueShard shard = routeForJob(job.route());
        JOBS.submit(() -> {
            try {
                execute(job, shard, 0);
            } finally {
                workerReleased(job);
            }
        });
    }

    @Override
    protected String routeName(LeagueShard shard) {
        return shard == LeagueShard.UNKNOWN ? "global" : shard.name();
    }

    @Override
    protected String workerThreadName(LeagueShard shard) {
        return shard == LeagueShard.UNKNOWN ? "lol-sync-global-" : "lol-sync-" + shard.name().toLowerCase() + "-";
    }

    @Override
    protected LeagueShard routeForJob(Object route) {
        if (route == null) return LeagueShard.UNKNOWN;
        if (route instanceof LeagueShard shard && shard != LeagueShard.UNKNOWN) return shard;
        throw new IllegalArgumentException("Sync jobs require a LeagueShard or the global route");
    }
}
