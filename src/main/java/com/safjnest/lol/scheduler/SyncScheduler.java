package com.safjnest.lol.scheduler;

import com.R4B.lol.*;
import com.hive.scheduler.AbstractScheduler;

public final class SyncScheduler extends AbstractScheduler<LeagueShard> {

    public SyncScheduler(com.hive.Registry registry) {
        super("sync", "Sync request cancelled during shutdown", registry, ExecutionMode.DIRECT);
    }

    @Override
    protected String runType(String key) {
        if ("tracking".equals(key)) return "TRACKING";
        if (key.startsWith("sample-games:")) return "SAMPLE_GAMES";
        if ("rank-entries".equals(key)) return "RANK_ENTRIES";
        return null;
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
