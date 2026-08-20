package com.safjnest.lol.model.status;

import java.util.List;

import com.safjnest.lol.queue.RequestState;

public record RequestRunStatus(
    String id,
    String type,
    RequestState state,
    Long queuedAt,
    Long startedAt,
    JobProgress progress,
    List<RequestTaskStatus> tasks
) {
}
