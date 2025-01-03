package com.safjnest.core;

import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class Chronos {
    private static final int THREAD_POOL = 10; // insane beebot thread pool pls no one can stop us
    private static ScheduledExecutorService executorService;
    public static final ChronoTask NULL = () -> {};

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

        public default void queue(Consumer<Void> onSuccess, Consumer<Throwable> onFailure) {
            CompletableFuture.runAsync(this, executorService)
                .thenAccept(result -> onSuccess.accept(null))
                .exceptionally(ex -> {
                    onFailure.accept(ex);
                    return null;
                });
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

         public default ScheduledFuture<?> scheduleAtFixedTime(int hour, int minute, int second) {
            return executorService.scheduleAtFixedRate(this, computeInitialDelay(hour, minute, second), TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
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


    private static long computeInitialDelay(int hour, int minute, int second) {
        Calendar current = Calendar.getInstance();
        Calendar nextRun = (Calendar) current.clone();

        nextRun.set(Calendar.HOUR_OF_DAY, hour);
        nextRun.set(Calendar.MINUTE, minute);
        nextRun.set(Calendar.SECOND, second);
        nextRun.set(Calendar.MILLISECOND, 0);

        if (nextRun.before(current)) {
            nextRun.add(Calendar.DAY_OF_MONTH, 1);
        }

        return nextRun.getTimeInMillis() - current.getTimeInMillis();
    }

}
