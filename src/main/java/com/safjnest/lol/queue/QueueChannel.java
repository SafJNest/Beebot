package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

final class QueueChannel<R> {

    private final BlockingQueue<QueueTask<R, ?>> immediate;
    private final BlockingQueue<QueueTask<R, ?>> normal;
    private final BlockingQueue<QueueTask<R, ?>> background;
    private final Semaphore available;

    QueueChannel() {
        immediate = new LinkedBlockingQueue<>();
        normal = new LinkedBlockingQueue<>();
        background = new LinkedBlockingQueue<>();
        available = new Semaphore(0);
    }

    void offer(QueueTask<R, ?> task) {
        lane(task.priority()).offer(task);
        available.release();
    }

    void promote(QueueTask<R, ?> task) {
        if (immediate.remove(task) || normal.remove(task) || background.remove(task)) {
            lane(task.priority()).offer(task);
        }
    }

    QueueTask<R, ?> take() throws InterruptedException {
        available.acquire();
        return next();
    }

    QueueTask<R, ?> poll() {
        return available.tryAcquire() ? next() : null;
    }

    int size() {
        return immediate.size() + normal.size() + background.size();
    }

    List<QueueTask<R, ?>> snapshot() {
        List<QueueTask<R, ?>> result = new ArrayList<>();
        result.addAll(immediate);
        result.addAll(normal);
        result.addAll(background);
        return result;
    }

    List<QueueTask<R, ?>> drain() {
        List<QueueTask<R, ?>> drained = new ArrayList<>();
        QueueTask<R, ?> task;
        while ((task = poll()) != null) drained.add(task);
        return drained;
    }

    private QueueTask<R, ?> next() {
        QueueTask<R, ?> task = immediate.poll();
        if (task != null) return task;
        task = normal.poll();
        return task != null ? task : background.poll();
    }

    private BlockingQueue<QueueTask<R, ?>> lane(QueuePriority priority) {
        return switch (priority) {
            case IMMEDIATE -> immediate;
            case NORMAL -> normal;
            case BACKGROUND -> background;
        };
    }
}
