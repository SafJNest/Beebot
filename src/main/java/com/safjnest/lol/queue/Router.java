package com.safjnest.lol.queue;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.scheduler.AbstractScheduler;

public final class Router {

    private static final Router GLOBAL = new Router(Registry.instance());

    private final Registry registry;
    private final Map<Class<?>, AbstractScheduler<?>> schedulers;

    public Router(Registry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        schedulers = new HashMap<>();
    }

    public static Router global() {
        return GLOBAL;
    }

    public synchronized void register(AbstractScheduler<?> scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        AbstractScheduler<?> existing = schedulers.putIfAbsent(scheduler.getClass(), scheduler);
        if (existing != null) throw new IllegalStateException("Scheduler already registered for type=" + scheduler.getClass().getSimpleName());
    }

    public <T> CompletableFuture<T> submit(Class<?> type, Object route, String key, String name,
        JobPriority priority, java.util.function.Function<Job<T>, T> work) {
        Objects.requireNonNull(work, "work");
        AbstractScheduler<?> scheduler;
        synchronized (this) {
            scheduler = schedulers.get(type);
        }
        if (scheduler == null) throw new IllegalArgumentException("No scheduler registered for type=" + type);
        if (!scheduler.acceptsRoute(route)) {
            throw new IllegalArgumentException("Invalid route for scheduler type=" + type + ": " + route);
        }
        Job<T> job = registry.create(type, route, key, name, priority, work);
        CompletableFuture<T> future = registry.future(job);
        if (!registry.following(job)) {
            try {
                scheduler.enqueue(job);
            } catch (Throwable failure) {
                registry.cancelled(job, failure);
                registry.released(job);
            }
        } else {
            registry.released(job);
        }
        return future;
    }
}
