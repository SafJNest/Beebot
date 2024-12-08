package com.safjnest.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Chronos {
    private static final int THREAD_POOL = 2; // insane beebot thread pool pls no one can stop us
    private static ScheduledExecutorService executorService;
    public static final ChronoTask NULL = new ChronoTask() {
        @Override
        public void run() {}
    };

    static{
        executorService = Executors.newScheduledThreadPool(THREAD_POOL);
    }

    public static void shutdown() throws InterruptedException {
        executorService.shutdown();
        if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
    }

    @FunctionalInterface
    public static interface ChronoTask extends Runnable {
        
        @Override
        void run();

        public default void queue() {
            CompletableFuture.runAsync(this, executorService);
        }

        public default void complete() {
            run();
        }

        public default CompletableFuture<Void> queueFuture() {
            return CompletableFuture.runAsync(this, executorService);
        }

        public default void completeWithException() throws ExecutionException, InterruptedException {
            queueFuture().get();
        }

        public default ScheduledFuture<?>  schedule(long delay, TimeUnit unit) {
            return executorService.schedule(this, delay, unit);
        }

        public default ScheduledFuture<?> scheduleAtFixedRate(long initialDelay, long period, TimeUnit unit) {
            return executorService.scheduleAtFixedRate(this, initialDelay, period, unit);
        }

        public default ScheduledFuture<?> scheduleWithFixedDelay(long initialDelay, long delay, TimeUnit unit) {
            return executorService.scheduleWithFixedDelay(this, initialDelay, delay, unit);
        }
    }

    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return executorService.schedule(task, delay, unit);
    }

    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        return executorService.scheduleAtFixedRate(task, initialDelay, period, unit);
    }

    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        return executorService.scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }

}
