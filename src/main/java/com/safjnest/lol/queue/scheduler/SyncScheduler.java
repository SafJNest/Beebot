package com.safjnest.lol.queue.scheduler;

import com.safjnest.lol.model.status.SchedulerStatus;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class SyncScheduler extends AbstractScheduler<LeagueShard> {

    private static final SyncScheduler INSTANCE = new SyncScheduler();

    private SyncScheduler() {
        super("sync", "Sync request cancelled during shutdown");
    }

    public static AbstractScheduler<LeagueShard> scheduler() {
        return INSTANCE;
    }

    public static void shutdown() {
        INSTANCE.shutdownDispatcher();
    }

    public static SchedulerStatus status() {
        return INSTANCE.snapshot();
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
