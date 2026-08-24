package com.safjnest.lol.queue.job;

public enum JobState {
    QUEUED,
    RUNNING,
    WAITING_CHILDREN,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == COMPLETED_WITH_ERRORS || this == FAILED || this == CANCELLED;
    }
}
