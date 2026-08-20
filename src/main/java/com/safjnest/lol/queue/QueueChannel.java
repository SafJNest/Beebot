package com.safjnest.lol.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

final class QueueChannel<R> {

    private final Deque<QueueTask<R, ?>> immediate;
    private final Deque<QueueTask<R, ?>> normal;
    private final Deque<QueueTask<R, ?>> background;
    private final ReentrantLock lock;
    private final Condition available;

    QueueChannel() {
        immediate = new ArrayDeque<>();
        normal = new ArrayDeque<>();
        background = new ArrayDeque<>();
        lock = new ReentrantLock();
        available = lock.newCondition();
    }

    void offer(QueueTask<R, ?> task) {
        lock.lock();
        try {
            lane(task.priority()).addLast(task);
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    void promote(QueueTask<R, ?> task) {
        lock.lock();
        try {
            if (immediate.remove(task) || normal.remove(task) || background.remove(task)) {
                lane(task.priority()).addLast(task);
            }
        } finally {
            lock.unlock();
        }
    }

    QueueTask<R, ?> take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            QueueTask<R, ?> task;
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

    List<QueueTask<R, ?>> snapshot(int maxSize) {
        lock.lock();
        try {
            List<QueueTask<R, ?>> result = new ArrayList<>();
            addSnapshot(result, immediate, maxSize);
            addSnapshot(result, normal, maxSize);
            addSnapshot(result, background, maxSize);
            return result;
        } finally {
            lock.unlock();
        }
    }

    List<QueueTask<R, ?>> drain() {
        lock.lock();
        try {
            List<QueueTask<R, ?>> drained = new ArrayList<>();
            QueueTask<R, ?> task;
            while ((task = next()) != null) drained.add(task);
            return drained;
        } finally {
            lock.unlock();
        }
    }

    private QueueTask<R, ?> next() {
        QueueTask<R, ?> task = immediate.pollFirst();
        if (task != null) return task;
        task = normal.pollFirst();
        return task != null ? task : background.pollFirst();
    }

    private Deque<QueueTask<R, ?>> lane(QueuePriority priority) {
        return switch (priority) {
            case IMMEDIATE -> immediate;
            case NORMAL -> normal;
            case BACKGROUND -> background;
        };
    }

    private static <R> void addSnapshot(
        List<QueueTask<R, ?>> result,
        Deque<QueueTask<R, ?>> lane,
        int maxSize
    ) {
        if (result.size() >= maxSize) return;
        for (QueueTask<R, ?> task : lane) {
            result.add(task);
            if (result.size() >= maxSize) return;
        }
    }
}
