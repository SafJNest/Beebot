package com.hive.status;

import java.util.List;

public record SchedulerStatus(
    String id,
    List<QueueStatus> queues,
    List<RunStatus> runs
) {
}
