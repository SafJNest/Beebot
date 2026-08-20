package com.safjnest.lol.model.status;

import java.util.List;

public record RiotMetrics(
    int totalInFlight,
    List<RiotQueue> queues
) {

    public record RiotQueue(
        String shard,
        String state,
        String currentJob,
        Long currentStartedAt,
        JobProgress progress,
        int queuedCount,
        int inFlight,
        List<String> queuedJobs
    ) {

        public static RiotQueue from(QueueWorkerStatus worker) {
            return new RiotQueue(
                worker.type(),
                worker.state(),
                worker.currentJob(),
                worker.currentStartedAt(),
                worker.progress(),
                worker.queuedCount(),
                worker.inFlight(),
                worker.queuedJobs()
            );
        }
    }
}
