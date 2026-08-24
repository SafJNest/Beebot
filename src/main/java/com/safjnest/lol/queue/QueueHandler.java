package com.safjnest.lol.queue;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.queue.scheduler.SyncScheduler;

public final class QueueHandler {

    private static volatile boolean started;

    private QueueHandler() {
    }

    public static synchronized void start() {
        if (started) return;
        Router router = Router.global();
        router.register(SyncScheduler.scheduler());
        router.register(RiotScheduler.scheduler());
        router.register(ComputeScheduler.scheduler());
        started = true;
    }

    public static <T> CompletableFuture<T> immediate(Class<?> scheduler, Object route, String key, String name,
        Function<Job<T>, T> work) {
        return submit(scheduler, route, key, name, JobPriority.IMMEDIATE, work);
    }

    public static <T> CompletableFuture<T> normal(Class<?> scheduler, Object route, String key, String name,
        Function<Job<T>, T> work) {
        return submit(scheduler, route, key, name, JobPriority.NORMAL, work);
    }

    public static <T> CompletableFuture<T> background(Class<?> scheduler, Object route, String key, String name,
        Function<Job<T>, T> work) {
        return submit(scheduler, route, key, name, JobPriority.BACKGROUND, work);
    }

    public static java.util.List<com.safjnest.lol.model.status.JobStatus> snapshot() {
        return Registry.instance().snapshot();
    }

    public static java.util.List<com.safjnest.lol.model.status.JobStatus> statusSnapshot(int maxDepth) {
        return Registry.instance().statusSnapshot(maxDepth);
    }

    public static java.util.List<com.safjnest.lol.model.status.JobStatus> statusSnapshot(int fullDepth, int nextDepthLimit) {
        return Registry.instance().statusSnapshot(fullDepth, nextDepthLimit);
    }

    public static void shutdown() {
        Registry.instance().shutdown("Application shutdown");
    }

    public static void retain(Job<?> job) {
        Registry.instance().retain(job);
    }

    public static void resume(Job<?> job, Runnable callback) {
        Registry.instance().resume(job, callback);
    }

    private static <T> CompletableFuture<T> submit(Class<?> scheduler, Object route, String key, String name,
        JobPriority priority, Function<Job<T>, T> work) {
        start();
        return Router.global().submit(scheduler, route, key, name, priority, work);
    }
}
