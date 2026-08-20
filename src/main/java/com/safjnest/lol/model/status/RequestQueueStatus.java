package com.safjnest.lol.model.status;

public record RequestQueueStatus(
    String route,
    RequestWorkerStatus worker
) {
}
