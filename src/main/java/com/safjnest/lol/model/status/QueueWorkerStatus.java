package com.safjnest.lol.model.status;

import java.util.List;

public record QueueWorkerStatus(
    int id,
    String type,
    String state,
    String currentJob,
    Long currentStartedAt,
    JobProgress progress,
    int queuedCount,
    int inFlight,
    List<String> queuedJobs
) {
}
