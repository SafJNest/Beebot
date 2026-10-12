package com.hive.worker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.hive.status.JobStatus;
import com.hive.status.WorkerStatus;
import com.hive.job.Job;
import com.hive.scheduler.AbstractScheduler;

public final class JobWorker<R> {

    private final AbstractScheduler<R> dispatcher;
    private final int id;
    private final R route;
    private final JobQueue queue;
    private volatile Job<?> currentJob;
    private volatile boolean stopped;
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
        stopped = true;
        ExecutorService current = executor;
        if (current != null) current.shutdownNow();
    }

    public void awaitStopped() {
        ExecutorService current = executor;
        executor = null;
        if (current == null) return;
        try {
            if (!current.awaitTermination(dispatcher.registry().config().shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) current.shutdownNow();
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
        List<JobStatus> queued = new ArrayList<>(Math.min(queuedCount, dispatcher.registry().config().queuedSnapshotLimit()));
        for (Job<?> job : queue.snapshot(dispatcher.registry().config().queuedSnapshotLimit())) {
            JobStatus status = dispatcher.status(job);
            if (status != null) queued.add(status);
        }
        ExecutorService current = executor;
        Job<?> running = currentJob;
        WorkerState state = running != null
            ? WorkerState.RUNNING
            : (current != null && !current.isShutdown() ? WorkerState.IDLE : WorkerState.STOPPED);
        return new WorkerStatus(
            id,
            state,
            dispatcher.status(running),
            queuedCount,
            queuedCount + (running == null ? 0 : 1),
            List.copyOf(queued)
        );
    }

    private void run() {
        while (!stopped) {
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
                if (!stopped) Thread.interrupted();
            }
        }
    }
}
