package com.safjnest.lol.model.status;

public record QueueStatus(
    String route,
    WorkerStatus worker
) {
}
