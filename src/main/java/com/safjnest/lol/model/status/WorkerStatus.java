package com.safjnest.lol.model.status;

import java.util.List;

import com.safjnest.lol.queue.worker.WorkerState;

public record WorkerStatus(
    int id,
    WorkerState state,
    JobStatus currentJob,
    int queuedCount,
    int inFlight,
    List<JobStatus> queuedJobs
) {
}
