package com.hive.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import com.hive.Registry;
import com.hive.job.Job;
import com.hive.job.JobPriority;
import com.hive.worker.JobQueue;
import com.hive.worker.JobWorker;
import com.hive.status.SchedulerStatus;
import com.hive.status.QueueStatus;
import com.hive.status.RunStatus;
import com.hive.status.JobStatus;

public abstract class AbstractScheduler<R> {

    private final String id;
    private final Registry registry;
    private final Object lifecycleLock;
    private final ConcurrentMap<Long, Job<?>> tasks;
    private final ConcurrentMap<Long, R> taskQueues;
    private final ConcurrentMap<R, JobQueue> queues;
    private final ConcurrentMap<R, JobWorker<R>> workers;
    private final List<R> routeOrder;
    private final String shutdownReason;
    private final ExecutionMode executionMode;
    private final java.util.concurrent.ExecutorService direct;
    private boolean stopped;

    public enum ExecutionMode { QUEUED, DIRECT }

    protected AbstractScheduler(String id, String shutdownReason, Registry registry) {
        this(id, shutdownReason, registry, ExecutionMode.QUEUED);
    }

    protected AbstractScheduler(String id, String shutdownReason, Registry registry, ExecutionMode executionMode) {
        this.id = Objects.requireNonNull(id, "id");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executionMode = Objects.requireNonNull(executionMode, "executionMode");
        lifecycleLock = new Object();
        tasks = new ConcurrentHashMap<>();
        taskQueues = new ConcurrentHashMap<>();
        queues = new ConcurrentHashMap<>();
        workers = new ConcurrentHashMap<>();
        routeOrder = new ArrayList<>();
        this.shutdownReason = Objects.requireNonNull(shutdownReason, "shutdownReason");
        direct = executionMode == ExecutionMode.DIRECT ? java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name(registry.config().identity() + "-" + id + "-", 0).factory()) : null;
    }

    public final Registry registry() { return registry; }

    protected abstract String routeName(R route);

    protected abstract String workerThreadName(R route);

    protected abstract R routeForJob(Object route);

    protected R queueFor(R route) {
        return route;
    }

    public boolean acceptsRoute(Object route) {
        try {
            routeForJob(route);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public JobStatus status(Job<?> job) {
        return registry.status(job);
    }

    protected String runType(String key) { return null; }

    protected void onQueued(Job<?> job) {
    }

    protected void onStarted(Job<?> job) {
    }

    protected void onBodyCompleted(Job<?> job) {
    }

    protected void onBodyFailed(Job<?> job, Throwable failure) {
    }

    protected final Object lifecycleLock() {
        return lifecycleLock;
    }

    protected final int load(R route) {
        JobWorker<R> worker = workers.get(route);
        return worker == null ? 0 : worker.load();
    }

    protected final boolean hasIncompleteTask(R queueRoute, Predicate<Job<?>> filter) {
        for (Job<?> job : tasks.values()) {
            if (Objects.equals(taskQueues.get(job.pid()), queueRoute) && !registry.bodyFinished(job) && filter.test(job)) return true;
        }
        return false;
    }

    public <T> void enqueue(Job<T> job) {
        R route = routeForJob(job.route());
        synchronized (lifecycleLock) {
            if (stopped) throw new IllegalStateException(shutdownReason);
            if (executionMode == ExecutionMode.DIRECT) {
                tasks.put(job.pid(), job);
                direct.execute(() -> {
                    Thread.currentThread().setName(workerThreadName(route) + job.pid());
                    try { execute(job, route, 0); }
                    finally { workerReleased(job); }
                });
                return;
            }
            R queueRoute = queueFor(route);
            registerRoute(queueRoute);
            tasks.put(job.pid(), job);
            taskQueues.put(job.pid(), queueRoute);
            try { onQueued(job); } catch (Throwable ignored) {}
            queue(queueRoute).offer(job);
        }
    }

    public final boolean promote(Job<?> job, JobPriority priority) {
        if (job == null || priority == null) return false;
        synchronized (lifecycleLock) {
            R queueRoute = taskQueues.get(job.pid());
            if (tasks.get(job.pid()) != job) return registry.promotePending(job, priority);
            if (queueRoute == null) return false;
            return queue(queueRoute).promote(job, priority);
        }
    }

    protected final void registerRoutes(Iterable<R> routes) {
        synchronized (lifecycleLock) {
            for (R route : routes) registerRoute(route);
        }
    }

    public final void shutdownDispatcher() {
        List<JobWorker<R>> stoppedWorkers;
        synchronized (lifecycleLock) {
            stopped = true;
            if (direct != null) direct.shutdownNow();
            stoppedWorkers = workersInOrder();
            for (JobWorker<R> worker : stoppedWorkers) worker.requestStop();
            for (JobQueue queue : queues.values()) for (Job<?> job : queue.drain()) cancel(job);
            tasks.clear();
            taskQueues.clear();
            workers.clear();
            routeOrder.clear();
            queues.clear();
        }
        for (JobWorker<R> worker : stoppedWorkers) worker.awaitStopped();
        if (direct != null) {
            try { direct.awaitTermination(registry.config().shutdownTimeout().toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
        }
    }

    public final SchedulerStatus snapshot() {
        synchronized (lifecycleLock) {
            List<QueueStatus> statuses = new ArrayList<>();
            for (R route : routeOrder) {
                JobWorker<R> worker = workers.get(route);
                if (worker != null) statuses.add(new QueueStatus(routeName(route), worker.status()));
            }
            List<RunStatus> runStatuses = new ArrayList<>();
            runStatuses.addAll(registry.runs(getClass(), this::runType));
            runStatuses.sort((left, right) -> left.queuedAt().compareTo(right.queuedAt()));
            return new SchedulerStatus(id, List.copyOf(statuses), List.copyOf(runStatuses));
        }
    }

    // ============================================================================

    public final Throwable execute(Job<?> job, R queueRoute, int worker) {
        try {
            if (!registry.started(job)) return null;
            onStarted(job);
            registry.execute(job);
            onBodyCompleted(job);
            return null;
        } catch (Throwable failure) {
            registry.failed(job, failure);
            try { onBodyFailed(job, failure); } catch (Throwable ignored) {}
            return failure;
        } finally {
            tasks.remove(job.pid(), job);
            taskQueues.remove(job.pid());
        }
    }

    public final void workerReleased(Job<?> job) {
        registry.released(job);
    }

    private void registerRoute(R route) {
        if (workers.containsKey(route)) return;
        JobQueue queue = new JobQueue();
        JobWorker<R> worker = new JobWorker<>(this, route, routeOrder.size() + 1, queue);
        queues.put(route, queue);
        workers.put(route, worker);
        routeOrder.add(route);
        worker.start(workerThreadName(route));
    }

    private JobQueue queue(R route) {
        JobQueue queue = queues.get(route);
        if (queue == null) throw new IllegalStateException("Missing queue for route=" + route);
        return queue;
    }

    private List<JobWorker<R>> workersInOrder() {
        List<JobWorker<R>> result = new ArrayList<>();
        for (R route : routeOrder) {
            JobWorker<R> worker = workers.get(route);
            if (worker != null) result.add(worker);
        }
        return result;
    }

    private void cancel(Job<?> job) {
        tasks.remove(job.pid(), job);
        taskQueues.remove(job.pid());
        registry.cancelled(job, new java.util.concurrent.CancellationException(shutdownReason));
        registry.released(job);
    }
}
