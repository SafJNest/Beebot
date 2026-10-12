package com.hive;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.hive.job.Job;
import com.hive.job.ResultType;
import com.hive.job.JobPriority;
import com.hive.scheduler.AbstractScheduler;

public final class Router {

    private final Registry registry;
    private final Map<Class<?>, AbstractScheduler<?>> schedulers = new HashMap<>();
    private boolean closed;

    public Router(Registry registry) { this.registry = Objects.requireNonNull(registry, "registry"); }

    public synchronized void register(AbstractScheduler<?> scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        if (closed) throw new IllegalStateException("Hive is closed");
        if (scheduler.registry() != registry) throw new IllegalArgumentException("Scheduler belongs to another runtime");
        if (schedulers.putIfAbsent(scheduler.getClass(), scheduler) != null)
            throw new IllegalStateException("Scheduler already registered for type=" + scheduler.getClass().getSimpleName());
    }

    public <R, T> CompletableFuture<T> submit(Class<? extends AbstractScheduler<R>> type, R route,
        String key, String name, JobPriority priority, Class<T> resultType, Function<Job<T>, T> work) {
        return submit(type, route, key, name, priority, ResultType.of(resultType), work);
    }

    public <R, T> CompletableFuture<T> submit(Class<? extends AbstractScheduler<R>> type, R route,
        String key, String name, JobPriority priority, ResultType<T> resultType, Function<Job<T>, T> work) {
        Objects.requireNonNull(work, "work");
        AbstractScheduler<?> scheduler;
        synchronized (this) {
            if (closed) throw new IllegalStateException("Hive is closed");
            scheduler = schedulers.get(type);
        }
        if (scheduler == null) throw new IllegalArgumentException("No scheduler registered for type=" + type);
        if (!scheduler.acceptsRoute(route)) throw new IllegalArgumentException("Invalid route for scheduler type=" + type + ": " + route);
        Job<T> job = registry.create(type, route, key, name, priority, resultType, work);
        CompletableFuture<T> future = job.future();
        if (!registry.following(job)) {
            try { scheduler.enqueue(job); }
            catch (Throwable failure) { registry.cancelled(job, failure); registry.released(job); }
        } else {
            try {
                Job<?> source = registry.source(job);
                if (source != null && scheduler.promote(source, job.priority())) registry.observe(source, "PROMOTED");
            } finally { registry.released(job); }
        }
        return future;
    }

    public synchronized <R, S extends AbstractScheduler<R>> S scheduler(Class<S> type) {
        return type.cast(schedulers.get(type));
    }

    public synchronized void close() { closed = true; }

    public void shutdownSchedulers() {
        java.util.List<AbstractScheduler<?>> all;
        synchronized (this) { all = java.util.List.copyOf(schedulers.values()); }
        for (AbstractScheduler<?> scheduler : all) scheduler.shutdownDispatcher();
    }
}
