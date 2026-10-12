package com.hive.status;

public record QueueStatus(
    String route,
    WorkerStatus worker
) {
}
