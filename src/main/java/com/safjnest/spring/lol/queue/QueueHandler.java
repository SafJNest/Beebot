package com.safjnest.spring.lol.queue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.hive.Hive;
import com.hive.HiveConfig;
import com.hive.job.Job;
import com.hive.job.ResultType;
import com.hive.job.JobPriority;
import com.hive.scheduler.AbstractScheduler;
import com.hive.status.JobStatus;
import com.safjnest.spring.lol.scheduler.ComputeScheduler;
import com.safjnest.s4b.scheduler.RiotScheduler;
import com.safjnest.spring.lol.scheduler.SyncScheduler;

public final class QueueHandler {

    private static volatile Hive runtime;

    private QueueHandler() {}

    public static synchronized void start() {
        if (runtime != null) return;
        Hive hive = new Hive(new HiveConfig("beebot", java.time.Clock.systemUTC(), 20, Integer.MAX_VALUE,
            java.time.Duration.ofSeconds(30), (job, event) -> {}));
        hive.register(new SyncScheduler(hive.registry()));
        hive.register(new RiotScheduler(hive.registry()));
        hive.register(new ComputeScheduler(hive.registry()));
        runtime = hive;
    }

    public static <R, T> CompletableFuture<T> immediate(Class<? extends AbstractScheduler<R>> scheduler, R route,
        String key, String name, Class<T> resultType, Function<Job<T>, T> work) {
        return immediate(scheduler, route, key, name, ResultType.of(resultType), work);
    }

    public static <R, T> CompletableFuture<T> normal(Class<? extends AbstractScheduler<R>> scheduler, R route,
        String key, String name, Class<T> resultType, Function<Job<T>, T> work) {
        return normal(scheduler, route, key, name, ResultType.of(resultType), work);
    }

    public static <R, T> CompletableFuture<T> background(Class<? extends AbstractScheduler<R>> scheduler, R route,
        String key, String name, Class<T> resultType, Function<Job<T>, T> work) {
        return background(scheduler, route, key, name, ResultType.of(resultType), work);
    }

    public static <R, T> CompletableFuture<T> immediate(Class<? extends AbstractScheduler<R>> scheduler, R route,
        String key, String name, ResultType<T> resultType, Function<Job<T>, T> work) {
        return active().router().submit(scheduler, route, key, name, JobPriority.IMMEDIATE, resultType, work);
    }

    public static <R, T> CompletableFuture<T> normal(Class<? extends AbstractScheduler<R>> scheduler, R route,
        String key, String name, ResultType<T> resultType, Function<Job<T>, T> work) {
        return active().router().submit(scheduler, route, key, name, JobPriority.NORMAL, resultType, work);
    }

    public static <R, T> CompletableFuture<T> background(Class<? extends AbstractScheduler<R>> scheduler, R route,
        String key, String name, ResultType<T> resultType, Function<Job<T>, T> work) {
        return active().router().submit(scheduler, route, key, name, JobPriority.BACKGROUND, resultType, work);
    }

    public static <T> java.util.concurrent.CompletionStage<T> termination(CompletableFuture<T> future) {
        return Hive.termination(future);
    }

    public static List<JobStatus> snapshot() { return active().registry().snapshot(); }
    public static List<JobStatus> statusSnapshot(int depth) { return active().registry().statusSnapshot(depth); }
    public static List<JobStatus> statusSnapshot(int depth, int limit) { return active().registry().statusSnapshot(depth, limit); }
    public static <R, S extends AbstractScheduler<R>> S scheduler(Class<S> type) { return active().router().scheduler(type); }
    public static <T, U> CompletableFuture<U> thenApply(CompletableFuture<T> source, Function<T, U> callback) {
        return active().thenApply(source, callback);
    }
    public static <T, U> CompletableFuture<U> thenCompose(CompletableFuture<T> source, Function<T, CompletableFuture<U>> callback) {
        return active().thenCompose(source, callback);
    }
    public static <T> CompletableFuture<T> whenComplete(CompletableFuture<T> source, java.util.function.BiConsumer<T, Throwable> callback) {
        return active().whenComplete(source, callback);
    }
    public static void retain(Job<?> job) { active().registry().retain(job); }
    public static void resume(Job<?> job, Runnable callback) { active().registry().resume(job, callback); }
    public static void cancel(Job<?> job) { active().registry().cancel(job); }

    public static synchronized void shutdown() {
        Hive hive = runtime;
        if (hive != null) hive.close();
    }

    // ============================================================================

    private static Hive active() {
        Hive hive = runtime;
        if (hive == null) throw new IllegalStateException("QueueHandler must be started explicitly before submission");
        return hive;
    }
}
