package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.safjnest.lol.model.status.RequestTaskStatus;
import com.safjnest.lol.model.status.RequestWorkerStatus;

final class RequestWorker<R> {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final int MAX_STATUS_QUEUED_TASKS = 20;

    private final AbstractRequestDispatcher<R> dispatcher;
    private final int id;
    private final RequestQueue<R> queue;
    private volatile RequestTask<R, ?> currentTask;
    private volatile ExecutorService executor;

    RequestWorker(AbstractRequestDispatcher<R> dispatcher, int id, RequestQueue<R> queue) {
        this.dispatcher = dispatcher;
        this.id = id;
        this.queue = queue;
    }

    void start(String threadName) {
        executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name(threadName, 0).factory());
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

    int load() {
        return queue.size() + (currentTask == null ? 0 : 1);
    }

    RequestWorkerStatus status() {
        int queuedCount = queue.size();
        List<RequestTaskStatus> queued = new ArrayList<>(Math.min(queuedCount, MAX_STATUS_QUEUED_TASKS));
        for (RequestTask<R, ?> task : queue.snapshot(MAX_STATUS_QUEUED_TASKS)) queued.add(task.status());
        ExecutorService current = executor;
        RequestWorkerState state = currentTask != null
            ? RequestWorkerState.RUNNING
            : (current != null && !current.isShutdown() ? RequestWorkerState.IDLE : RequestWorkerState.STOPPED);
        return new RequestWorkerStatus(
            id,
            state,
            currentTask == null ? null : currentTask.status(),
            queuedCount,
            queuedCount + (currentTask == null ? 0 : 1),
            List.copyOf(queued)
        );
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            RequestTask<R, ?> task;
            try {
                task = queue.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            currentTask = task;
            task.start();
            dispatcher.onStarted(task);
            try {
                Throwable failure = dispatcher.executeTask(task);
                if (failure == null) dispatcher.onCompleted(task);
                else dispatcher.onFailed(task, failure);
            } finally {
                currentTask = null;
            }
        }
    }
}
