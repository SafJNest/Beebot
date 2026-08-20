package com.safjnest.lol.model.status;

import java.util.List;

import com.safjnest.lol.queue.RequestWorkerState;

public record RequestWorkerStatus(
    int id,
    RequestWorkerState state,
    RequestTaskStatus currentTask,
    int queuedCount,
    int inFlight,
    List<RequestTaskStatus> queuedTasks
) {
}
