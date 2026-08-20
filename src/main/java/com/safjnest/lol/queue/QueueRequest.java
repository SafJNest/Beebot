package com.safjnest.lol.queue;

import java.util.Objects;
import java.util.function.Supplier;

public final class QueueRequest<R, T> {

    private final String key;
    private final String name;
    private final R route;
    private final QueuePriority priority;
    private final Supplier<T> supplier;

    public QueueRequest(String key, String name, R route, QueuePriority priority, Supplier<T> supplier) {
        this.key = Objects.requireNonNull(key, "key");
        this.name = Objects.requireNonNull(name, "name");
        this.route = Objects.requireNonNull(route, "route");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.supplier = Objects.requireNonNull(supplier, "supplier");
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public R route() {
        return route;
    }

    public QueuePriority priority() {
        return priority;
    }

    public Supplier<T> supplier() {
        return supplier;
    }
}
