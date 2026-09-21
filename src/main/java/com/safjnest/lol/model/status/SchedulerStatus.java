package com.safjnest.lol.model.status;

import java.util.List;

public record SchedulerStatus(
    String id,
    List<QueueStatus> queues,
    List<RunStatus> runs
) {
}
