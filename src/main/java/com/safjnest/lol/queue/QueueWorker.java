package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class QueueWorker<R> {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final AbstractQueueScheduler<R> scheduler;
    private final int id;
    private final String type;
    private final QueueChannel<R> channel;
    private final AtomicLong submitted;
    private final AtomicLong started;
    private final AtomicLong finished;
    private volatile QueueTask<R, ?> currentTask;
    private volatile long currentStartedAt;
    private volatile ExecutorService executor;

    QueueWorker(AbstractQueueScheduler<R> scheduler, int id, String type, QueueChannel<R> channel) {
        this.scheduler = scheduler;
        this.id = id;
        this.type = type;
        this.channel = channel;
        submitted = new AtomicLong();
        started = new AtomicLong();
        finished = new AtomicLong();
    }

    void start(String threadName) {
        executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name(threadName, 0).factory()
        );
        executor.submit(this::run);
    }

    void requestStop() {
        ExecutorService current = executor;
        if (current != null) current.shutdownNow();
    }

    void awaitStopped() {
        ExecutorService current = executor;
        executor = null;
        if (current == null) return;
        try {
            if (!current.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) current.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            current.shutdownNow();
        }
    }

    void submitted() {
        submitted.incrementAndGet();
    }

    int load() {
        return channel.size() + (currentTask == null ? 0 : 1);
    }

    QueueWorkerStatus status() {
        List<String> queued = new ArrayList<>();
        for (QueueTask<R, ?> task : channel.snapshot()) queued.add(task.name());
        QueueTask<R, ?> task = currentTask;
        ExecutorService current = executor;
        boolean running = current != null && !current.isShutdown();
        return new QueueWorkerStatus(
            id,
            type,
            running,
            task == null ? null : task.name(),
            currentStartedAt,
            submitted.get(),
            started.get(),
            finished.get(),
            List.copyOf(queued)
        );
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            QueueTask<R, ?> task;
            try {
                task = channel.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            if (task == null) continue;
            currentTask = task;
            currentStartedAt = System.currentTimeMillis();
            started.incrementAndGet();
            scheduler.onStarted(task);
            try {
                Throwable failure = scheduler.executeTask(task);
                if (failure == null) scheduler.onCompleted(task);
                else scheduler.onFailed(task, failure);
            } finally {
                finished.incrementAndGet();
                currentTask = null;
                currentStartedAt = 0;
            }
        }
    }
}
