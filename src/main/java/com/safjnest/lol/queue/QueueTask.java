package com.safjnest.lol.queue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class QueueTask<R, T> {

    private final String key;
    private final String name;
    private final R route;
    private final Supplier<T> supplier;
    private final CompletableFuture<T> future;
    private volatile QueuePriority priority;
    private volatile R queue;

    QueueTask(String key, String name, R route, QueuePriority priority, Supplier<T> supplier) {
        this.key = key;
        this.name = name;
        this.route = route;
        this.priority = priority;
        this.supplier = supplier;
        this.future = new CompletableFuture<>();
    }

    // ============================================================================

    String key() {
        return key;
    }

    String name() {
        return name;
    }

    R route() {
        return route;
    }

    R queue() {
        return queue != null ? queue : route;
    }

    void assignQueue(R queue) {
        this.queue = queue;
    }

    QueuePriority priority() {
        return priority;
    }

    CompletableFuture<T> future() {
        return future;
    }

    boolean promote(QueuePriority value) {
        if (value.ordinal() >= priority.ordinal()) return false;
        priority = value;
        return true;
    }

    Throwable execute(Runnable cleanup) {
        T result = null;
        Throwable failure = null;
        try {
            result = supplier.get();
        } catch (Throwable exception) {
            failure = exception;
        } finally {
            cleanup.run();
        }

        if (failure == null) future.complete(result);
        else future.completeExceptionally(failure);
        return failure;
    }

    void cancel(String reason, Runnable cleanup) {
        cleanup.run();
        future.completeExceptionally(new CancellationException(reason));
    }
}
