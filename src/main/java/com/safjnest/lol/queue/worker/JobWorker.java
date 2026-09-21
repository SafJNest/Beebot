package com.safjnest.lol.queue.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.safjnest.lol.model.status.JobStatus;
import com.safjnest.lol.model.status.WorkerStatus;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.scheduler.AbstractScheduler;

public final class JobWorker<R> {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final int MAX_STATUS_QUEUED_TASKS = 20;

    private final AbstractScheduler<R> dispatcher;
    private final int id;
    private final R route;
    private final JobQueue queue;
    private volatile Job<?> currentJob;
    private volatile ExecutorService executor;

    public JobWorker(AbstractScheduler<R> dispatcher, R route, int id, JobQueue queue) {
        this.dispatcher = dispatcher;
        this.route = route;
        this.id = id;
        this.queue = queue;
    }

    public void start(String threadName) {
        executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().name(threadName, 0).factory());
        executor.submit(this::run);
    }

    public void requestStop() {
        ExecutorService current = executor;
        if (current != null) current.shutdownNow();
    }

    public void awaitStopped() {
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

    public int load() {
        return queue.size() + (currentJob == null ? 0 : 1);
    }

    public WorkerStatus status() {
        int queuedCount = queue.size();
        List<JobStatus> queued = new ArrayList<>(Math.min(queuedCount, MAX_STATUS_QUEUED_TASKS));
        for (Job<?> job : queue.snapshot(MAX_STATUS_QUEUED_TASKS)) queued.add(dispatcher.status(job));
        ExecutorService current = executor;
        WorkerState state = currentJob != null
            ? WorkerState.RUNNING
            : (current != null && !current.isShutdown() ? WorkerState.IDLE : WorkerState.STOPPED);
        return new WorkerStatus(
            id,
            state,
            currentJob == null ? null : dispatcher.status(currentJob),
            queuedCount,
            queuedCount + (currentJob == null ? 0 : 1),
            List.copyOf(queued)
        );
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Job<?> job;
            try {
                job = queue.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            currentJob = job;
            try {
                dispatcher.execute(job, route, id);
            } finally {
                currentJob = null;
                dispatcher.workerReleased(job);
            }
        }
    }
}
