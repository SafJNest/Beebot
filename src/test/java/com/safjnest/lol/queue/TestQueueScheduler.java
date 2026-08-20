package com.safjnest.lol.queue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.safjnest.lol.model.status.QueueWorkerStatus;

class TestQueueScheduler extends AbstractQueueScheduler<String> {

    TestQueueScheduler() {
        super("test shutdown");
        registerRoutes(List.of("main", "helper"));
    }

    <T> CompletableFuture<T> publicSchedule(QueueRequest<String, T> request) {
        return enqueue(request);
    }

    List<QueueWorkerStatus> publicWorkerStatuses() {
        return schedulerWorkerStatuses();
    }

    int publicIncompleteCount(String route) {
        return incompleteCount(route);
    }

    @Override
    protected String channelName(String route) {
        return route;
    }

    @Override
    protected String workerThreadName(String route) {
        return "test-" + route + "-";
    }
}
