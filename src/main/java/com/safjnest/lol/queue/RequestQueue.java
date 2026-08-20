package com.safjnest.lol.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

final class RequestQueue<R> {

    private final Deque<RequestTask<R, ?>> immediate;
    private final Deque<RequestTask<R, ?>> normal;
    private final Deque<RequestTask<R, ?>> background;
    private final ReentrantLock lock;
    private final Condition available;

    RequestQueue() {
        immediate = new ArrayDeque<>();
        normal = new ArrayDeque<>();
        background = new ArrayDeque<>();
        lock = new ReentrantLock();
        available = lock.newCondition();
    }

    void offer(RequestTask<R, ?> task) {
        lock.lock();
        try {
            lane(task.priority()).addLast(task);
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    void promote(RequestTask<R, ?> task) {
        lock.lock();
        try {
            if (immediate.remove(task) || normal.remove(task) || background.remove(task)) lane(task.priority()).addLast(task);
        } finally {
            lock.unlock();
        }
    }

    RequestTask<R, ?> take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            RequestTask<R, ?> task;
            while ((task = next()) == null) available.await();
            return task;
        } finally {
            lock.unlock();
        }
    }

    int size() {
        lock.lock();
        try {
            return immediate.size() + normal.size() + background.size();
        } finally {
            lock.unlock();
        }
    }

    List<RequestTask<R, ?>> snapshot(int maxSize) {
        lock.lock();
        try {
            List<RequestTask<R, ?>> result = new ArrayList<>();
            addSnapshot(result, immediate, maxSize);
            addSnapshot(result, normal, maxSize);
            addSnapshot(result, background, maxSize);
            return result;
        } finally {
            lock.unlock();
        }
    }

    List<RequestTask<R, ?>> drain() {
        lock.lock();
        try {
            List<RequestTask<R, ?>> drained = new ArrayList<>();
            RequestTask<R, ?> task;
            while ((task = next()) != null) drained.add(task);
            return drained;
        } finally {
            lock.unlock();
        }
    }

    private RequestTask<R, ?> next() {
        RequestTask<R, ?> task = immediate.pollFirst();
        if (task != null) return task;
        task = normal.pollFirst();
        return task != null ? task : background.pollFirst();
    }

    private Deque<RequestTask<R, ?>> lane(RequestPriority priority) {
        return switch (priority) {
            case IMMEDIATE -> immediate;
            case NORMAL -> normal;
            case BACKGROUND -> background;
        };
    }

    private static <R> void addSnapshot(
        List<RequestTask<R, ?>> result,
        Deque<RequestTask<R, ?>> lane,
        int maxSize
    ) {
        if (result.size() >= maxSize) return;
        for (RequestTask<R, ?> task : lane) {
            result.add(task);
            if (result.size() >= maxSize) return;
        }
    }
}
