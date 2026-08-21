package com.safjnest.status;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.status.BotStatus;
import com.safjnest.lol.model.status.LeagueMetrics;
import com.safjnest.lol.model.status.JvmMetrics;
import com.safjnest.lol.model.status.MongoMetrics;
import com.safjnest.lol.model.status.RequestDispatcherStatus;
import com.safjnest.lol.model.status.RedisMetrics;
import com.safjnest.lol.model.status.SystemMetrics;
import com.safjnest.lol.queue.ComputeRequestDispatcher;
import com.safjnest.lol.queue.RiotRequestDispatcher;
import com.safjnest.lol.queue.SyncRequestDispatcher;

public class StatusService {

    public BotStatus current() {
        SampledMetrics sampled = SystemMetricsSampler.snapshot();
        if (sampled == null) sampled = SampledMetrics.empty();

        LeagueMetrics league = leagueMetrics();
        List<RequestDispatcherStatus> dispatchers = dispatcherStatus();
        JvmMetrics process = sampled.jvm();
        SystemMetrics system = sampled.system();
        RedisMetrics redis = sampled.redis();
        MongoMetrics mongo = sampled.mongo();
        return BotStatus.online(league, dispatchers, process, system, redis, mongo);
    }

    // ============================================================================

    private static LeagueMetrics leagueMetrics() {
        try {
            return LeagueMetricsStore.snapshot();
        } catch (Exception ignored) {
            return new LeagueMetrics(0, 0, 0, Map.of());
        }
    }

    private static List<RequestDispatcherStatus> dispatcherStatus() {
        try {
            return List.of(SyncRequestDispatcher.status(), RiotRequestDispatcher.status(), ComputeRequestDispatcher.status());
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
