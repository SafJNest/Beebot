package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractQueueScheduler<R> {

    private final Object lifecycleLock;
    private final ConcurrentMap<String, QueueTask<R, ?>> tasks;
    private final ConcurrentMap<R, QueueChannel<R>> channels;
    private final ConcurrentMap<R, QueueWorker<R>> workers;
    private final List<QueueWorker<R>> workerOrder;
    private final String shutdownReason;

    protected AbstractQueueScheduler(String shutdownReason) {
        this.lifecycleLock = new Object();
        this.tasks = new ConcurrentHashMap<>();
        this.channels = new ConcurrentHashMap<>();
        this.workers = new ConcurrentHashMap<>();
        this.workerOrder = new ArrayList<>();
        this.shutdownReason = Objects.requireNonNull(shutdownReason, "shutdownReason");
    }

    // ============================================================================

    protected abstract String channelName(R route);

    protected abstract String workerThreadName(R route);

    protected <T> R queueFor(QueueRequest<R, T> request) {
        return request.route();
    }

    protected boolean promoteOnReuse(R route) {
        return false;
    }

    protected void onQueued(QueueTask<R, ?> task) {
    }

    protected void onReused(QueueTask<R, ?> task) {
    }

    protected void onStarted(QueueTask<R, ?> task) {
    }

    protected void onCompleted(QueueTask<R, ?> task) {
    }

    protected void onFailed(QueueTask<R, ?> task, Throwable failure) {
    }

    protected final Object lifecycleLock() {
        return lifecycleLock;
    }

    protected final int load(R route) {
        QueueWorker<R> worker = workers.get(route);
        return worker == null ? 0 : worker.load();
    }

    protected final <T> CompletableFuture<T> enqueue(QueueRequest<R, T> request) {
        Objects.requireNonNull(request, "request");

        synchronized (lifecycleLock) {
            while (true) {
                QueueTask<R, ?> existing = tasks.get(request.key());
                if (existing != null) {
                    if (!existing.future().isDone()) {
                        if (promoteOnReuse(request.route()) && existing.promote(request.priority())) {
                            channel(existing.queue()).promote(existing);
                        }
                        onReused(existing);
                        return cast(existing.future());
                    }
                    if (!tasks.remove(request.key(), existing)) continue;
                }

                R queue = queueFor(request);
                registerRoute(queue);
                QueueTask<R, T> task = new QueueTask<>(
                    request.key(),
                    request.name(),
                    request.route(),
                    request.priority(),
                    request.supplier()
                );
                task.assignQueue(queue);
                if (tasks.putIfAbsent(request.key(), task) == null) {
                    QueueWorker<R> worker = workers.get(queue);
                    channel(queue).offer(task);
                    if (worker != null) worker.submitted();
                    onQueued(task);
                    return task.future();
                }
            }
        }
    }

    protected final void registerRoutes(Iterable<R> routes) {
        synchronized (lifecycleLock) {
            for (R route : routes) registerRoute(route);
        }
    }

    public final void shutdownScheduler() {
        List<QueueWorker<R>> stoppedWorkers;
        synchronized (lifecycleLock) {
            stoppedWorkers = List.copyOf(workerOrder);
            stopWorkers(stoppedWorkers);
            cancelPendingTasks();
            clearSchedulerState();
        }
        awaitWorkers(stoppedWorkers);
    }

    protected final List<QueueWorkerStatus> schedulerWorkerStatuses() {
        synchronized (lifecycleLock) {
            List<QueueWorkerStatus> statuses = new ArrayList<>();
            for (QueueWorker<R> worker : workerOrder) statuses.add(worker.status());
            return List.copyOf(statuses);
        }
    }

    // ============================================================================

    final Throwable executeTask(QueueTask<R, ?> task) {
        return task.execute(() -> tasks.remove(task.key(), task));
    }

    private void registerRoute(R route) {
        if (workers.containsKey(route)) return;

        QueueChannel<R> channel = new QueueChannel<>();
        QueueWorker<R> worker = new QueueWorker<>(this, workerOrder.size() + 1, channelName(route), channel);
        channels.put(route, channel);
        workers.put(route, worker);
        workerOrder.add(worker);
        worker.start(workerThreadName(route));
    }

    private QueueChannel<R> channel(R route) {
        QueueChannel<R> channel = channels.get(route);
        if (channel == null) throw new IllegalStateException("Missing channel for route=" + route);
        return channel;
    }

    private void stopWorkers(List<QueueWorker<R>> stoppedWorkers) {
        for (QueueWorker<R> worker : stoppedWorkers) worker.requestStop();
    }

    private void cancelPendingTasks() {
        for (QueueChannel<R> channel : channels.values()) {
            for (QueueTask<R, ?> task : channel.drain()) cancel(task);
        }
    }

    private void clearSchedulerState() {
        tasks.clear();
        workers.clear();
        workerOrder.clear();
        channels.clear();
    }

    private void awaitWorkers(List<QueueWorker<R>> stoppedWorkers) {
        for (QueueWorker<R> worker : stoppedWorkers) worker.awaitStopped();
    }

    private void cancel(QueueTask<R, ?> task) {
        task.cancel(shutdownReason, () -> tasks.remove(task.key(), task));
    }

    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<T> cast(CompletableFuture<?> future) {
        return (CompletableFuture<T>) future;
    }
}
