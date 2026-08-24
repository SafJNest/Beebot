package com.safjnest.lol.queue.job;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.safjnest.lol.model.status.JobProgress;

public final class Job<T> {

    private static final String PENDING = "PENDING";
    private static final String DONE = "DONE";
    private static final String MISSING = "MISSING";
    private static final String FAILED = "FAILED";

    private final long pid;
    private final long ppid;
    private final Class<?> scheduler;
    private final Object route;
    private final String key;
    private final String name;
    private final JobPriority priority;
    private final Function<Job<T>, T> work;
    private final ConcurrentHashMap<String, String> items;
    private final ConcurrentHashMap<String, String> itemLabels;
    private final AtomicInteger total;
    private final AtomicInteger processed;
    private volatile String phase;

    public Job(long pid, long ppid, Class<?> scheduler, Object route, String key, String name,
        JobPriority priority, Function<Job<T>, T> work) {
        this.pid = pid;
        this.ppid = ppid;
        this.scheduler = scheduler;
        this.route = route;
        this.key = key;
        this.name = name;
        this.priority = priority;
        this.work = work;
        items = new ConcurrentHashMap<>();
        itemLabels = new ConcurrentHashMap<>();
        total = new AtomicInteger();
        processed = new AtomicInteger();
    }

    public long pid() { return pid; }
    public long ppid() { return ppid; }
    public Class<?> scheduler() { return scheduler; }
    public Object route() { return route; }
    public String key() { return key; }
    public String name() { return name; }
    public JobPriority priority() { return priority; }
    public Function<Job<T>, T> work() { return work; }

    public void phase(String value) { phase = value; }
    public void trackItems(Collection<String> values) { if (values != null) for (String value : values) trackItem(value); }
    public void trackItem(String value) { if (value != null && !value.isBlank() && items.putIfAbsent(value, PENDING) == null) total.incrementAndGet(); }
    public void labelItem(String value, String label) { if (value != null && !value.isBlank() && label != null && !label.isBlank()) itemLabels.put(value, label); }
    public void done(String value) { terminal(value, DONE); }
    public void missing(String value) { terminal(value, MISSING); }
    public void failed(String value) { terminal(value, FAILED); }

    public String phase() { return phase; }
    public JobProgress progress() { int count = total.get(); return count == 0 ? null : new JobProgress(Math.min(processed.get(), count), count); }
    public Map<String, String> items() { return items.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(items)); }
    public Map<String, String> itemLabels() { return itemLabels.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(itemLabels)); }

    private void terminal(String value, String target) {
        trackItem(value);
        String previous = items.put(value, target);
        if (PENDING.equals(previous)) processed.incrementAndGet();
    }
}
