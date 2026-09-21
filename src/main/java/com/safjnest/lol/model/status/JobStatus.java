package com.safjnest.lol.model.status;

import java.util.List;
import java.util.Map;

import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.job.JobState;

public record JobStatus(
    long pid,
    long ppid,
    String type,
    String key,
    String name,
    String route,
    JobPriority priority,
    JobState state,
    Long followingPid,
    long queuedAt,
    Long startedAt,
    Long completedAt,
    String phase,
    JobProgress progress,
    Map<String, String> items,
    Map<String, String> itemLabels,
    List<Long> children
) {
}
