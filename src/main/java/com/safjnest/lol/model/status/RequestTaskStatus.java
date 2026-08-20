package com.safjnest.lol.model.status;

import java.util.Map;

import com.safjnest.lol.queue.RequestPriority;
import com.safjnest.lol.queue.RequestState;

public record RequestTaskStatus(
    String key,
    String name,
    String route,
    RequestPriority priority,
    RequestState state,
    String runId,
    Long queuedAt,
    Long startedAt,
    String phase,
    JobProgress progress,
    Map<String, String> items
) {
}
