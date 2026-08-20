package com.safjnest.lol.model.status;

import java.util.List;

public record RequestDispatcherStatus(
    String id,
    List<RequestQueueStatus> queues,
    List<RequestRunStatus> runs
) {
}
