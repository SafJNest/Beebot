package com.safjnest.lol.queue;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Request<R, T> {

    private final String key;
    private final String name;
    private final R route;
    private final RequestPriority priority;
    private final Function<RequestTask<R, T>, T> work;
    private final RequestRun run;

    public Request(String key, String name, R route, RequestPriority priority, Supplier<T> supplier) {
        this(key, name, route, priority, supplier, null);
    }

    public Request(String key, String name, R route, RequestPriority priority, Supplier<T> supplier, RequestRun run) {
        this.key = Objects.requireNonNull(key, "key");
        this.name = Objects.requireNonNull(name, "name");
        this.route = Objects.requireNonNull(route, "route");
        this.priority = Objects.requireNonNull(priority, "priority");
        work = ignored -> Objects.requireNonNull(supplier, "supplier").get();
        this.run = run;
    }

    public Request(String key, String name, R route, RequestPriority priority, Function<RequestTask<R, T>, T> work, RequestRun run) {
        this.key = Objects.requireNonNull(key, "key");
        this.name = Objects.requireNonNull(name, "name");
        this.route = Objects.requireNonNull(route, "route");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.work = Objects.requireNonNull(work, "work");
        this.run = run;
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

    public RequestPriority priority() {
        return priority;
    }

    public Supplier<T> supplier() {
        return () -> work.apply(null);
    }

    Function<RequestTask<R, T>, T> work() {
        return work;
    }

    public RequestRun run() {
        return run;
    }
}
