package com.hive.status;

import java.util.List;
import java.util.Map;

import com.hive.job.JobPriority;
import com.hive.job.JobState;

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
    String currentItem,
    JobProgress progress,
    Map<String, String> items,
    Map<String, String> itemLabels,
    List<Long> children
) {
}
