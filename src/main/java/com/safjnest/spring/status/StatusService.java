package com.safjnest.spring.status;

import java.util.List;
import java.util.Map;

import com.hive.status.SchedulerStatus;
import com.R4B.api.dto.status.BotStatus;
import com.R4B.api.dto.status.JvmMetrics;
import com.R4B.api.dto.status.LeagueMetrics;
import com.R4B.api.dto.status.MongoMetrics;
import com.R4B.api.dto.status.RedisMetrics;
import com.R4B.api.dto.status.SystemMetrics;
import com.hive.status.JobStatus;
import com.safjnest.spring.lol.queue.QueueHandler;
import com.safjnest.spring.lol.scheduler.ComputeScheduler;
import com.safjnest.s4b.scheduler.RiotScheduler;
import com.safjnest.spring.lol.scheduler.SyncScheduler;

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
            return List.of(QueueHandler.scheduler(SyncScheduler.class).snapshot(), QueueHandler.scheduler(RiotScheduler.class).snapshot(), QueueHandler.scheduler(ComputeScheduler.class).snapshot());
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
