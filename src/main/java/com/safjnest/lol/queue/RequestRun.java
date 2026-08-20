package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.model.status.RequestRunStatus;
import com.safjnest.lol.model.status.RequestTaskStatus;

public final class RequestRun {

    private final String id;
    private final String type;
    private final long queuedAt;
    private final AtomicInteger total;
    private final AtomicInteger completed;
    private final AtomicReference<RequestState> state;
    private final ConcurrentMap<String, RequestTask<?, ?>> tasks;
    private volatile boolean sealed;
    private volatile long startedAt;

    RequestRun(String id, String type) {
        this.id = id;
        this.type = type;
        queuedAt = System.currentTimeMillis();
        total = new AtomicInteger();
        completed = new AtomicInteger();
        state = new AtomicReference<>(RequestState.QUEUED);
        tasks = new ConcurrentHashMap<>();
    }

    public String id() {
        return id;
    }

    void submitted(RequestTask<?, ?> task) {
        tasks.put(task.key(), task);
        total.incrementAndGet();
    }

    void started() {
        if (state.compareAndSet(RequestState.QUEUED, RequestState.RUNNING)) startedAt = System.currentTimeMillis();
    }

    void completed() {
        completed.incrementAndGet();
    }

    void seal() {
        sealed = true;
    }

    boolean complete() {
        return sealed && completed.get() >= total.get();
    }

    RequestRunStatus status() {
        int count = total.get();
        JobProgress progress = count == 0 ? null : new JobProgress(Math.min(completed.get(), count), count);
        List<RequestTaskStatus> statuses = new ArrayList<>(tasks.size());
        for (RequestTask<?, ?> task : tasks.values()) statuses.add(task.status());
        statuses.sort((left, right) -> left.queuedAt().compareTo(right.queuedAt()));
        return new RequestRunStatus(
            id,
            type,
            state.get(),
            queuedAt,
            startedAt > 0 ? startedAt : null,
            progress,
            List.copyOf(statuses)
        );
    }
}
