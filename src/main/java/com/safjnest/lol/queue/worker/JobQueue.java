package com.safjnest.lol.queue.worker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;

public final class JobQueue {

    private final Deque<Job<?>> immediate;
    private final Deque<Job<?>> normal;
    private final Deque<Job<?>> background;
    private final ReentrantLock lock;
    private final Condition available;

    public JobQueue() {
        immediate = new ArrayDeque<>();
        normal = new ArrayDeque<>();
        background = new ArrayDeque<>();
        lock = new ReentrantLock();
        available = lock.newCondition();
    }

    public void offer(Job<?> job) {
        lock.lock();
        try {
            lane(job.priority()).addLast(job);
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    public Job<?> take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            Job<?> job;
            while ((job = next()) == null) available.await();
            return job;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return immediate.size() + normal.size() + background.size();
        } finally {
            lock.unlock();
        }
    }

    public List<Job<?>> snapshot(int maxSize) {
        lock.lock();
        try {
            List<Job<?>> result = new ArrayList<>();
            addSnapshot(result, immediate, maxSize);
            addSnapshot(result, normal, maxSize);
            addSnapshot(result, background, maxSize);
            return result;
        } finally {
            lock.unlock();
        }
    }

    public List<Job<?>> drain() {
        lock.lock();
        try {
            List<Job<?>> drained = new ArrayList<>();
            Job<?> job;
            while ((job = next()) != null) drained.add(job);
            return drained;
        } finally {
            lock.unlock();
        }
    }

    private Job<?> next() {
        Job<?> job = immediate.pollFirst();
        if (job != null) return job;
        job = normal.pollFirst();
        return job != null ? job : background.pollFirst();
    }

    private Deque<Job<?>> lane(JobPriority priority) {
        return switch (priority) {
            case IMMEDIATE -> immediate;
            case NORMAL -> normal;
            case BACKGROUND -> background;
        };
    }

    private static void addSnapshot(
        List<Job<?>> result,
        Deque<Job<?>> lane,
        int maxSize
    ) {
        if (result.size() >= maxSize) return;
        for (Job<?> job : lane) {
            result.add(job);
            if (result.size() >= maxSize) return;
        }
    }
}
