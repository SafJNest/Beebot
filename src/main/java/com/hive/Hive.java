package com.hive;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.hive.job.Job;
import com.hive.scheduler.AbstractScheduler;

public final class Hive implements AutoCloseable {

    private final HiveConfig config;
    private final Registry registry;
    private final Router router;
    private final ExecutorService continuations;

    public Hive(HiveConfig config) {
        this.config = config;
        continuations = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(config.identity() + "-callback-", 0).factory());
        registry = new Registry(config, continuations);
        router = new Router(registry);
    }

    public static <T> java.util.concurrent.CompletionStage<T> termination(CompletableFuture<T> future) {
        if (!(future instanceof JobFuture<T> jobFuture)) throw new IllegalArgumentException("Not a Hive job future");
        return jobFuture.termination.minimalCompletionStage();
    }

    public Registry registry() { return registry; }
    public Router router() { return router; }
    public HiveConfig config() { return config; }
    public void register(AbstractScheduler<?> scheduler) { router.register(scheduler); }

    public <T, U> CompletableFuture<U> thenApply(
        CompletableFuture<T> source, Function<T, U> callback) {
        Job<?> parent = registry.currentJob();
        if (parent != null) registry.retain(parent);
        CompletableFuture<U> result = new CompletableFuture<>();
        source.whenComplete((value, failure) -> {
            Runnable work = () -> {
                try {
                    if (failure != null) result.completeExceptionally(failure);
                    else result.complete(callback.apply(value));
                } catch (Throwable exception) {
                    result.completeExceptionally(exception);
                    if (exception instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException(exception);
                }
            };
            Runnable contextual = () -> {
                if (parent == null) work.run();
                else {
                    try { registry.resume(parent, work); }
                    catch (Throwable exception) { result.completeExceptionally(exception); }
                }
            };
            try { continuations.execute(contextual); }
            catch (RuntimeException exception) {
                if (parent != null) {
                    try { registry.resume(parent, () -> result.completeExceptionally(exception)); }
                    catch (RuntimeException ignored) { result.completeExceptionally(exception); }
                } else result.completeExceptionally(exception);
            }
        });
        return result;
    }

    public <T, U> CompletableFuture<U> thenCompose(
        CompletableFuture<T> source,
        Function<T, CompletableFuture<U>> callback) {
        return thenApply(source, value -> {
            Job<?> parent = registry.currentJob();
            CompletableFuture<U> inner = Objects.requireNonNull(callback.apply(value), "callback future");
            if (parent == null) return inner;
            registry.retain(parent);
            CompletableFuture<U> contextual = new CompletableFuture<>();
            inner.whenComplete((result, failure) -> {
                Runnable completion = () -> {
                    try {
                        registry.resume(parent, () -> {
                            if (failure != null) {
                                contextual.completeExceptionally(failure);
                                if (failure instanceof RuntimeException runtime) throw runtime;
                                throw new IllegalStateException(failure);
                            }
                            contextual.complete(result);
                        });
                    } catch (Throwable exception) { contextual.completeExceptionally(exception); }
                };
                try { continuations.execute(completion); }
                catch (RuntimeException exception) { completion.run(); }
            });
            return contextual;
        }).thenCompose(Function.identity());
    }

    public <T> CompletableFuture<T> whenComplete(
        CompletableFuture<T> source, BiConsumer<T, Throwable> callback) {
        return thenApply(source.handle(Completion<T>::new), completion -> {
            callback.accept(completion.value(), completion.failure());
            return completion;
        }).thenCompose(completion -> completion.failure() == null
            ? CompletableFuture.completedFuture(completion.value())
            : CompletableFuture.failedFuture(completion.failure()));
    }

    @Override
    public void close() {
        router.close();
        registry.shutdown("Application shutdown");
        router.shutdownSchedulers();
        continuations.shutdown();
        try {
            if (!continuations.awaitTermination(config.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) continuations.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            continuations.shutdownNow();
        }
    }

    // ============================================================================

    private record Completion<T>(T value, Throwable failure) {}
}
