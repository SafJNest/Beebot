package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.safjnest.lol.model.status.RequestDispatcherStatus;
import com.safjnest.lol.model.status.RequestQueueStatus;
import com.safjnest.lol.model.status.RequestRunStatus;

public abstract class AbstractRequestDispatcher<R> {

    private final String id;
    private final Object lifecycleLock;
    private final ConcurrentMap<String, RequestTask<R, ?>> tasks;
    private final ConcurrentMap<R, RequestQueue<R>> queues;
    private final ConcurrentMap<R, RequestWorker<R>> workers;
    private final List<R> routeOrder;
    private final ConcurrentMap<String, RequestRun> runs;
    private final String shutdownReason;

    protected AbstractRequestDispatcher(String id, String shutdownReason) {
        this.id = Objects.requireNonNull(id, "id");
        lifecycleLock = new Object();
        tasks = new ConcurrentHashMap<>();
        queues = new ConcurrentHashMap<>();
        workers = new ConcurrentHashMap<>();
        routeOrder = new ArrayList<>();
        runs = new ConcurrentHashMap<>();
        this.shutdownReason = Objects.requireNonNull(shutdownReason, "shutdownReason");
    }

    protected abstract String routeName(R route);

    protected abstract String workerThreadName(R route);

    protected <T> R queueFor(Request<R, T> request) {
        return request.route();
    }

    protected boolean promoteOnReuse(R route) {
        return false;
    }

    protected void onQueued(RequestTask<R, ?> task) {
    }

    protected void onReused(RequestTask<R, ?> task) {
    }

    protected void onStarted(RequestTask<R, ?> task) {
    }

    protected void onCompleted(RequestTask<R, ?> task) {
    }

    protected void onFailed(RequestTask<R, ?> task, Throwable failure) {
    }

    protected final Object lifecycleLock() {
        return lifecycleLock;
    }

    protected final int load(R route) {
        RequestWorker<R> worker = workers.get(route);
        return worker == null ? 0 : worker.load();
    }

    protected final int incompleteCount(R route) {
        int count = 0;
        for (RequestTask<R, ?> task : tasks.values()) {
            if (Objects.equals(task.route(), route) && !task.future().isDone()) count++;
        }
        return count;
    }

    protected final RequestRun createRun(String key, String type) {
        return runs.compute(key, (ignored, active) -> active == null || active.complete()
            ? new RequestRun(UUID.randomUUID().toString(), type)
            : active);
    }

    protected final void finishRun(String key, RequestRun run) {
        if (run == null) return;
        run.seal();
        removeCompletedRun(key, run);
    }

    protected final void finishRun(RequestRun run) {
        if (run == null) return;
        run.seal();
        runs.entrySet().removeIf(entry -> entry.getValue() == run && run.complete());
    }

    protected final <T> CompletableFuture<T> enqueue(Request<R, T> request) {
        Objects.requireNonNull(request, "request");

        synchronized (lifecycleLock) {
            while (true) {
                RequestTask<R, ?> existing = tasks.get(request.key());
                if (existing != null) {
                    if (!existing.future().isDone()) {
                        if (promoteOnReuse(request.route()) && existing.promote(request.priority())) queue(existing.queue()).promote(existing);
                        onReused(existing);
                        return cast(existing.future());
                    }
                    if (!tasks.remove(request.key(), existing)) continue;
                }

                R queueRoute = queueFor(request);
                registerRoute(queueRoute);
                RequestTask<R, T> task = new RequestTask<>(
                    request.key(),
                    request.name(),
                    request.route(),
                    queueRoute,
                    request.priority(),
                    request.work(),
                    request.run()
                );
                if (tasks.putIfAbsent(request.key(), task) == null) {
                    if (request.run() != null) request.run().submitted(task);
                    queue(queueRoute).offer(task);
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

    public final void shutdownDispatcher() {
        List<RequestWorker<R>> stoppedWorkers;
        synchronized (lifecycleLock) {
            stoppedWorkers = workersInOrder();
            for (RequestWorker<R> worker : stoppedWorkers) worker.requestStop();
            for (RequestQueue<R> queue : queues.values()) for (RequestTask<R, ?> task : queue.drain()) cancel(task);
            tasks.clear();
            workers.clear();
            routeOrder.clear();
            queues.clear();
            runs.clear();
        }
        for (RequestWorker<R> worker : stoppedWorkers) worker.awaitStopped();
    }

    public final RequestDispatcherStatus snapshot() {
        synchronized (lifecycleLock) {
            List<RequestQueueStatus> statuses = new ArrayList<>();
            for (R route : routeOrder) {
                RequestWorker<R> worker = workers.get(route);
                if (worker != null) statuses.add(new RequestQueueStatus(routeName(route), worker.status()));
            }
            List<RequestRunStatus> runStatuses = new ArrayList<>();
            for (RequestRun run : runs.values()) runStatuses.add(run.status());
            runStatuses.sort((left, right) -> left.queuedAt().compareTo(right.queuedAt()));
            return new RequestDispatcherStatus(id, List.copyOf(statuses), List.copyOf(runStatuses));
        }
    }

    // ============================================================================

    final Throwable executeTask(RequestTask<R, ?> task) {
        return task.execute(
            () -> tasks.remove(task.key(), task),
            () -> {
                task.completeRun();
                RequestRun run = task.run();
                if (run != null) runs.entrySet().removeIf(entry -> entry.getValue() == run && run.complete());
            }
        );
    }

    private void registerRoute(R route) {
        if (workers.containsKey(route)) return;
        RequestQueue<R> queue = new RequestQueue<>();
        RequestWorker<R> worker = new RequestWorker<>(this, routeOrder.size() + 1, queue);
        queues.put(route, queue);
        workers.put(route, worker);
        routeOrder.add(route);
        worker.start(workerThreadName(route));
    }

    private RequestQueue<R> queue(R route) {
        RequestQueue<R> queue = queues.get(route);
        if (queue == null) throw new IllegalStateException("Missing queue for route=" + route);
        return queue;
    }

    private List<RequestWorker<R>> workersInOrder() {
        List<RequestWorker<R>> result = new ArrayList<>();
        for (R route : routeOrder) {
            RequestWorker<R> worker = workers.get(route);
            if (worker != null) result.add(worker);
        }
        return result;
    }

    private void cancel(RequestTask<R, ?> task) {
        task.cancel(shutdownReason, () -> tasks.remove(task.key(), task));
    }

    private void removeCompletedRun(String key, RequestRun run) {
        if (run.complete()) runs.remove(key, run);
    }

    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<T> cast(CompletableFuture<?> future) {
        return (CompletableFuture<T>) future;
    }
}
