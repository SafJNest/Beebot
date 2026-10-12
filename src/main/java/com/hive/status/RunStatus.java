package com.hive.status;

import java.util.List;

import com.hive.job.JobState;

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
