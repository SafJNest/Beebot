package com.safjnest.status;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.status.BotStatus;
import com.safjnest.lol.model.status.LeagueMetrics;
import com.safjnest.lol.model.status.JvmMetrics;
import com.safjnest.lol.model.status.MongoMetrics;
import com.safjnest.lol.model.status.SchedulerStatus;
import com.safjnest.lol.model.status.JobStatus;
import com.safjnest.lol.model.status.RedisMetrics;
import com.safjnest.lol.model.status.SystemMetrics;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.queue.scheduler.SyncScheduler;

public class StatusService {

    private static final int STATUS_FULL_JOB_DEPTH = 3;
    private static final int STATUS_NEXT_JOB_DEPTH_LIMIT = 100;

    public BotStatus current() {
        SampledMetrics sampled = SystemMetricsSampler.snapshot();
        if (sampled == null) sampled = SampledMetrics.empty();

        LeagueMetrics league = leagueMetrics();
        List<SchedulerStatus> dispatchers = dispatcherStatus();
        List<JobStatus> jobs = QueueHandler.statusSnapshot(STATUS_FULL_JOB_DEPTH, STATUS_NEXT_JOB_DEPTH_LIMIT);
        JvmMetrics process = sampled.jvm();
        SystemMetrics system = sampled.system();
        RedisMetrics redis = sampled.redis();
        MongoMetrics mongo = sampled.mongo();
        return BotStatus.online(league, dispatchers, jobs, process, system, redis, mongo);
    }

    // ============================================================================

    private static LeagueMetrics leagueMetrics() {
        try {
            return LeagueMetricsStore.snapshot();
        } catch (Exception ignored) {
            return new LeagueMetrics(0, 0, 0, Map.of());
        }
    }

    private static List<SchedulerStatus> dispatcherStatus() {
        try {
            return List.of(SyncScheduler.status(), RiotScheduler.status(), ComputeScheduler.status());
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
