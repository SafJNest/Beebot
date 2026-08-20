package com.safjnest.lol.queue;

import java.util.List;

public record QueueWorkerStatus(
    int id,
    String type,
    boolean running,
    String currentJob,
    long currentStartedAt,
    long submitted,
    long started,
    long finished,
    List<String> queuedJobs
) {
}
