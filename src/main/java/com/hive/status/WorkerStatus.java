package com.hive.status;

import java.util.List;

import com.hive.worker.WorkerState;

public record WorkerStatus(
    int id,
    WorkerState state,
    JobStatus currentJob,
    int queuedCount,
    int inFlight,
    List<JobStatus> queuedJobs
) {
}
