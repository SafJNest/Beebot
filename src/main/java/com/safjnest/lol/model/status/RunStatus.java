package com.safjnest.lol.model.status;

import java.util.List;

import com.safjnest.lol.queue.job.JobState;

public record RunStatus(
    String id,
    String type,
    JobState state,
    Long queuedAt,
    Long startedAt,
    JobProgress progress,
    List<JobStatus> jobs
) {
}
