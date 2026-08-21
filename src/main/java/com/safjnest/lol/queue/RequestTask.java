package com.safjnest.lol.queue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.model.status.RequestTaskStatus;

public final class RequestTask<R, T> {

    private static final String PENDING = "PENDING";
    private static final String DONE = "DONE";
    private static final String MISSING = "MISSING";
    private static final String FAILED = "FAILED";

    private final String key;
    private final String name;
    private final R route;
    private final R queue;
    private final Function<RequestTask<R, T>, T> work;
    private final CompletableFuture<T> future;
    private final RequestRun run;
    private final long queuedAt;
    private final ConcurrentHashMap<String, String> items;
    private final ConcurrentHashMap<String, String> itemLabels;
    private final AtomicInteger total;
    private final AtomicInteger processed;
    private volatile RequestPriority priority;
    private volatile RequestState state;
    private volatile long startedAt;
    private volatile String phase;

    RequestTask(String key, String name, R route, R queue, RequestPriority priority, Function<RequestTask<R, T>, T> work, RequestRun run) {
        this.key = key;
        this.name = name;
        this.route = route;
        this.queue = queue;
        this.priority = priority;
        this.work = work;
        this.run = run;
        future = new CompletableFuture<>();
        queuedAt = System.currentTimeMillis();
        items = new ConcurrentHashMap<>();
        itemLabels = new ConcurrentHashMap<>();
        total = new AtomicInteger();
        processed = new AtomicInteger();
        state = RequestState.QUEUED;
    }

    String key() { return key; }

    String name() { return name; }

    R route() { return route; }

    R queue() { return queue; }

    RequestPriority priority() { return priority; }

    CompletableFuture<T> future() { return future; }

    void start() {
        state = RequestState.RUNNING;
        startedAt = System.currentTimeMillis();
        if (run != null) run.started();
    }

    void completeRun() {
        if (run != null) run.completed();
    }

    RequestRun run() { return run; }

    boolean promote(RequestPriority value) {
        if (value.ordinal() >= priority.ordinal()) return false;
        priority = value;
        return true;
    }

    public void phase(String value) {
        phase = value;
    }

    public void trackItems(Collection<String> values) {
        if (values == null) return;
        for (String value : values) trackItem(value);
    }

    public void trackItem(String value) {
        if (value == null || value.isBlank()) return;
        if (items.putIfAbsent(value, PENDING) == null) total.incrementAndGet();
    }

    public void labelItem(String value, String label) {
        if (value == null || value.isBlank() || label == null || label.isBlank()) return;
        itemLabels.put(value, label);
    }

    public void done(String value) { terminal(value, DONE); }

    public void missing(String value) { terminal(value, MISSING); }

    public void failed(String value) { terminal(value, FAILED); }

    Throwable execute(Runnable cleanup, Runnable beforeFutureCompletion) {
        T result = null;
        Throwable failure = null;
        try {
            result = work.apply(this);
        } catch (Throwable exception) {
            failure = exception;
        } finally {
            cleanup.run();
        }

        state = failure == null ? RequestState.COMPLETED : RequestState.FAILED;
        beforeFutureCompletion.run();
        if (failure == null) future.complete(result);
        else future.completeExceptionally(failure);
        return failure;
    }

    void cancel(String reason, Runnable cleanup) {
        cleanup.run();
        state = RequestState.FAILED;
        future.completeExceptionally(new CancellationException(reason));
        completeRun();
    }

    RequestTaskStatus status() {
        Map<String, String> snapshot = items.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(items));
        Map<String, String> labels = itemLabels.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(itemLabels));
        int count = total.get();
        JobProgress progress = count == 0 ? null : new JobProgress(Math.min(processed.get(), count), count);
        return new RequestTaskStatus(
            key,
            name,
            route == null ? null : route.toString(),
            priority,
            state,
            run == null ? null : run.id(),
            queuedAt,
            startedAt > 0 ? startedAt : null,
            phase,
            progress,
            snapshot,
            labels
        );
    }

    private void terminal(String value, String target) {
        trackItem(value);
        String previous = items.put(value, target);
        if (PENDING.equals(previous)) processed.incrementAndGet();
    }
}
