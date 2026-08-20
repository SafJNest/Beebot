package com.safjnest.status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.status.BotStatus;
import com.safjnest.lol.model.status.LeagueMetrics;
import com.safjnest.lol.model.status.JvmMetrics;
import com.safjnest.lol.model.status.QueueWorkerStatus;
import com.safjnest.lol.model.status.RedisMetrics;
import com.safjnest.lol.model.status.SystemMetrics;
import com.safjnest.lol.model.status.RiotMetrics;
import com.safjnest.lol.model.status.TrackerMetrics;
import com.safjnest.lol.model.status.WorkerMetrics;
import com.safjnest.lol.queue.DatabaseTracker;
import com.safjnest.lol.queue.R4JQueue;

public class StatusService {

    public BotStatus current() {
        SampledMetrics sampled = SystemMetricsSampler.snapshot();
        if (sampled == null) sampled = SampledMetrics.empty();

        LeagueMetrics league = leagueMetrics();
        TrackerMetrics tracker = trackerMetrics(league.gameQueue());
        WorkerMetrics workers = workerMetrics();
        RiotMetrics riot = riotMetrics();
        JvmMetrics process = sampled.jvm();
        SystemMetrics system = sampled.system();
        RedisMetrics redis = sampled.redis();
        return BotStatus.online(league, tracker, workers, riot, process, system, redis);
    }

    // ============================================================================

    private static LeagueMetrics leagueMetrics() {
        try {
            return LeagueMetricsStore.snapshot();
        } catch (Exception ignored) {
            return new LeagueMetrics(0, 0, 0, 0, 0, Map.of());
        }
    }

    private static TrackerMetrics trackerMetrics(long pendingGames) {
        try {
            return TrackerMetricsStore.snapshot(pendingGames);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static WorkerMetrics workerMetrics() {
        try {
            return new WorkerMetrics(DatabaseTracker.workerStatuses());
        } catch (Exception ignored) {
            return new WorkerMetrics(List.of());
        }
    }

    private static RiotMetrics riotMetrics() {
        try {
            List<QueueWorkerStatus> queues = new ArrayList<>(R4JQueue.workerStatuses());
            queues.sort(Comparator.comparing(QueueWorkerStatus::type));
            int totalInFlight = 0;
            List<RiotMetrics.RiotQueue> statusQueues = new ArrayList<>(queues.size());
            for (QueueWorkerStatus queue : queues) {
                totalInFlight += queue.inFlight();
                statusQueues.add(RiotMetrics.RiotQueue.from(queue));
            }
            return new RiotMetrics(totalInFlight, List.copyOf(statusQueues));
        } catch (Exception ignored) {
            return new RiotMetrics(0, List.of());
        }
    }
}
