package com.hive;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import com.hive.status.JobProgress;
import com.hive.status.JobStatus;
import com.hive.status.RunStatus;
import com.hive.job.Job;
import com.hive.job.ResultType;
import com.hive.job.JobPriority;
import com.hive.job.JobState;

public final class Registry {

    private final AtomicLong nextPid;
    private final ConcurrentMap<Long, Entry<?>> entries;
    private final ConcurrentMap<DedupKey, Long> active;
    private final ThreadLocal<Entry<?>> current;
    private final Clock clock;
    private final HiveConfig config;
    private final java.util.concurrent.Executor notifications;
    private boolean closed;

    Registry(HiveConfig config, java.util.concurrent.Executor notifications) {
        this.config = Objects.requireNonNull(config, "config");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.clock = config.clock();
        nextPid = new AtomicLong();
        entries = new ConcurrentHashMap<>();
        active = new ConcurrentHashMap<>();
        current = new ThreadLocal<>();
    }

    public HiveConfig config() { return config; }
    public Job<?> currentJob() { Entry<?> entry = current.get(); return entry == null ? null : entry.job; }
    public void observe(Job<?> job, String event) {
        try {
            notifications.execute(() -> {
                synchronized (Registry.this) {}
                try { config.observer().accept(job, event); } catch (Throwable ignored) {}
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {}

    }

    synchronized <T> Job<T> create(Class<?> scheduler, Object route, String key, String name, JobPriority priority, ResultType<T> resultType,
        Function<Job<T>, T> work) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(priority, "priority");
        if (closed) throw new IllegalStateException("Hive is closed");
        Objects.requireNonNull(resultType, "resultType");
        Entry<?> parent = current.get();
        if (parent != null && parent.state.terminal()) throw new IllegalStateException("Parent is not active");
        DedupKey dedupeKey = dedupeKey(scheduler, route, key);
        Long followingPid = active.get(dedupeKey);
        Entry<?> sourceEntry = followingPid == null ? null : entries.get(followingPid);
        if (sourceEntry != null && !sourceEntry.job.resultType().equals(resultType)) throw new IllegalArgumentException("Incompatible result type for active key=" + key);
        if (parent != null && sourceEntry != null && dependsOn(sourceEntry, parent))
            throw new IllegalArgumentException("Dedup would create a parent/child cycle");
        long ppid = parent == null ? 0 : parent.job.pid();
        long pid = nextPid.incrementAndGet();
        JobPriority effectivePriority = parent == null ? priority : inheritedPriority(parent.job.priority(), priority);
        Job<T> job = new Job<>(pid, ppid, scheduler, route, key, name, effectivePriority, resultType, work, new JobFuture<>());
        Entry<T> entry = new Entry<>(job, followingPid, clock.millis());
        entries.put(pid, entry);
        if (parent != null) {
            parent.children.add(pid);
            parent.activeChildren++;
            parent.childrenTotal++;
        }
        if (followingPid == null) {
            active.put(dedupeKey, pid);
        } else {
            Entry<T> source = entry(followingPid);
            source.lifecycleFuture.whenComplete((value, failure) -> completeFollower(entry, source.state, value, failure));
        }
        return job;
    }

    public <T> CompletableFuture<T> future(Job<T> job) {
        entry(job);
        return job.future();
    }

    public boolean following(Job<?> job) {
        return entry(job).followingPid != null;
    }

    public Job<?> source(Job<?> job) {
        Entry<?> entry = entry(job);
        Entry<?> source = entry.followingPid == null ? null : entries.get(entry.followingPid);
        return source == null ? null : source.job;
    }

    public boolean started(Job<?> job) {
        synchronized (this) {
            Entry<?> entry = entry(job);
            if (entry.followingPid != null || entry.state.terminal()) return false;
            entry.startedAt = clock.millis();
            entry.state = JobState.RUNNING;
            observe(job, "STARTED");
            return true;
        }
    }

    public <T> T execute(Job<T> job) {
        Entry<T> entry = entry(job);
        synchronized (this) { if (entry.state.terminal()) return null; }
        Throwable failure = null;
        T result = null;
        Entry<?> previous = current.get();
        current.set(entry);
        entry.runner = Thread.currentThread();
        try {
            result = job.work().apply(job);
            job.resultType().cast(result);
            return result;
        } catch (Throwable exception) {
            failure = exception;
            throw rethrow(exception);
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
            entry.runner = null;
            completeBody(entry, result, failure);
        }
    }

    public synchronized boolean promotePending(Job<?> job, JobPriority priority) {
        Entry<?> entry = entries.get(job.pid());
        if (entry == null || entry.state.terminal()) return false;
        if (entry.job != job) throw new IllegalArgumentException("Job belongs to another runtime");
        return entry.state == JobState.QUEUED && entry.followingPid == null && job.promote(priority);
    }

    public boolean bodyFinished(Job<?> job) {
        Entry<?> entry = entries.get(job.pid());
        if (entry == null) return true;
        if (entry.job != job) throw new IllegalArgumentException("Job belongs to another runtime");
        return entry.bodyFinished;
    }

    public void retain(Job<?> job) {
        synchronized (this) {
            Entry<?> entry = entry(job);
            if (entry.state.terminal()) throw new IllegalStateException("Job is not active");
            entry.pendingCallbacks++;
        }
    }

    public void resume(Job<?> job, Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        Entry<?> entry = entry(job);
        synchronized (this) {
            if (entry.state.terminal() || entry.pendingCallbacks <= entry.runningCallbacks) throw new IllegalStateException("No retained callback for active job");
            entry.runningCallbacks++;
        }
        Entry<?> previous = current.get();
        current.set(entry);
        try {
            callback.run();
        } catch (Throwable failure) {
            synchronized (this) {
                if (entry.failure == null) entry.failure = failure;
            }
            throw rethrow(failure);
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
            release(entry);
        }
    }

    public void cancel(Job<?> job) {
        synchronized (this) { cancelSubtree(entry(job), new java.util.concurrent.CancellationException("Job cancelled")); }
    }

    public void cancelled(Job<?> job, Throwable failure) {
        synchronized (this) {
            Entry<?> entry = entry(job);
            if (entry.state.terminal()) return;
            entry.result = null;
            entry.failure = failure;
            entry.bodyFinished = true;
            entry.state = JobState.CANCELLED;
            terminal(entry);
        }
    }

    public void failed(Job<?> job, Throwable failure) {
        synchronized (this) {
            Entry<?> entry = entry(job);
            if (entry.state.terminal() || entry.bodyFinished) return;
            entry.failure = failure == null ? new IllegalStateException("Job execution failed") : failure;
            entry.bodyFinished = true;
            completeIfReady(entry);
        }
    }

    public synchronized JobStatus status(Job<?> job) {
        if (job == null) return null;
        Entry<?> entry = entries.get(job.pid());
        if (entry == null) return null;
        if (entry.job != job) throw new IllegalArgumentException("Job belongs to another runtime");
        return status(entry, null, false);
    }

    public synchronized List<JobStatus> snapshot() {
        List<JobStatus> result = new ArrayList<>();
        for (Entry<?> entry : entries.values()) {
            if (result.size() >= config.snapshotLimit()) break;
            result.add(status(entry, null, true));
        }
        result.sort(Comparator.comparingLong(JobStatus::pid));
        return List.copyOf(result);
    }

    public List<JobStatus> statusSnapshot(int maxDepth) {
        return statusSnapshot(maxDepth, 0);
    }

    public synchronized List<JobStatus> statusSnapshot(int fullDepth, int nextDepthLimit) {
        if (fullDepth < 1) throw new IllegalArgumentException("fullDepth must be positive");
        if (nextDepthLimit < 0) throw new IllegalArgumentException("nextDepthLimit cannot be negative");
        Set<Long> visible = new HashSet<>();
        List<Entry<?>> nextDepth = new ArrayList<>();
        for (Entry<?> entry : entries.values()) {
            if (entry.job.ppid() == 0) collectVisible(entry, 1, fullDepth, visible, nextDepth);
        }
        nextDepth.sort(Comparator
            .comparing((Entry<?> entry) -> entry.job.priority())
            .thenComparingLong(entry -> entry.queuedAt)
            .thenComparingLong(entry -> entry.job.pid()));
        for (int i = 0; i < nextDepth.size() && i < nextDepthLimit && visible.size() < config.snapshotLimit(); i++) visible.add(nextDepth.get(i).job.pid());
        List<JobStatus> result = new ArrayList<>();
        for (Long pid : visible) {
            Entry<?> entry = entries.get(pid);
            if (entry != null) result.add(status(entry, visible, false));
        }
        result.sort(Comparator.comparingLong(JobStatus::pid));
        return List.copyOf(result);
    }

    public synchronized void shutdown(String reason) {
        closed = true;
        Throwable failure = new java.util.concurrent.CancellationException(reason);
        List<Entry<?>> roots = new ArrayList<>();
        for (Entry<?> entry : entries.values()) if (entry.job.ppid() == 0 && !entry.state.terminal()) roots.add(entry);
        for (Entry<?> root : roots) cancelSubtree(root, failure);
    }

    public synchronized List<RunStatus> runs(Class<?> scheduler, Function<String, String> classifier) {
        List<RunStatus> result = new ArrayList<>();
        for (Entry<?> entry : entries.values()) {
            Job<?> job = entry.job;
            if (job.scheduler() != scheduler || job.route() != null || entry.followingPid != null || entry.state.terminal()) continue;
            String runType = classifier.apply(job.key());
            if (runType == null) continue;
            List<JobStatus> children = new ArrayList<>();
            for (Long childPid : entry.children) {
                Entry<?> child = entries.get(childPid);
                if (child != null && children.size() < config.snapshotLimit()) children.add(status(child, null, false));
            }
            result.add(new RunStatus(String.valueOf(job.pid()), runType, entry.state, entry.queuedAt,
                entry.startedAt == 0 ? null : entry.startedAt, progress(entry), List.copyOf(children)));
            if (result.size() >= config.snapshotLimit()) break;
        }
        result.sort(Comparator.comparing(RunStatus::queuedAt));
        return List.copyOf(result);
    }

    // ============================================================================

    private synchronized <T> void completeBody(Entry<T> entry, T result, Throwable failure) {
        if (entry.state.terminal()) return;
        entry.result = result;
        entry.failure = failure;
        entry.bodyFinished = true;
        completeIfReady(entry);
    }

    private void release(Entry<?> entry) {
        synchronized (this) {
            entry.runningCallbacks--;
            entry.pendingCallbacks--;
            completeIfReady(entry);
        }
    }

    private void completeIfReady(Entry<?> entry) {
        if (!entry.bodyFinished || entry.activeChildren > 0 || entry.pendingCallbacks > 0 || entry.state.terminal()) {
            if (entry.bodyFinished && !entry.state.terminal()) entry.state = JobState.WAITING_CHILDREN;
            return;
        }
        if (entry.failure != null) entry.state = JobState.FAILED;
        else if (entry.childFailed) entry.state = JobState.COMPLETED_WITH_ERRORS;
        else entry.state = JobState.COMPLETED;
        terminal(entry);
    }

    private void terminal(Entry<?> entry) {
        entry.completedAt = clock.millis();
        Job<?> job = entry.job;
        active.remove(dedupeKey(job.scheduler(), job.route(), job.key()), job.pid());

        Entry<?> parent = entries.get(job.ppid());
        if (parent != null) {
            parent.activeChildren--;
            if (entry.state != JobState.COMPLETED) parent.childFailed = true;
            parent.completedChildren++;
            completeIfReady(parent);
        }
        removeIfReleased(entry);
        observe(job, entry.state.name());
        notifyCompletion(entry);
    }

    private void cancelSubtree(Entry<?> entry, Throwable failure) {
        if (entry.state.terminal()) return;
        entry.result = null;
        entry.failure = failure;
        entry.bodyFinished = true;
        entry.state = JobState.CANCELLED;
        if (entry.runner != null) entry.runner.interrupt();
        for (Long childPid : entry.children) {
            Entry<?> child = entries.get(childPid);
            if (child != null) cancelSubtree(child, failure);
        }
        terminal(entry);
    }

    private <T> void completeFollower(Entry<T> follower, JobState sourceState, T result, Throwable failure) {
        synchronized (this) {
            if (follower.state.terminal()) return;
            follower.result = result;
            follower.failure = failure;
            follower.bodyFinished = true;
            follower.state = sourceState;
            terminal(follower);
        }
    }

    public void released(Job<?> job) {
        synchronized (this) {
            Entry<?> entry = entry(job);
            entry.released = true;
            removeIfReleased(entry);
        }
    }

    private void removeIfReleased(Entry<?> entry) {
        if (entry.released && entry.state.terminal()) entries.remove(entry.job.pid(), entry);
    }

    private void collectVisible(Entry<?> entry, int depth, int fullDepth, Set<Long> visible, List<Entry<?>> nextDepth) {
        if (visible.size() >= config.snapshotLimit() || !visible.add(entry.job.pid())) return;
        if (depth == fullDepth) {
            for (Long childPid : entry.children) {
                Entry<?> child = entries.get(childPid);
                if (child != null) nextDepth.add(child);
            }
            return;
        }
        for (Long childPid : entry.children) {
            Entry<?> child = entries.get(childPid);
            if (child != null) collectVisible(child, depth + 1, fullDepth, visible, nextDepth);
        }
    }

    private JobStatus status(Entry<?> entry, Set<Long> visible, boolean includeItems) {
        Job<?> job = entry.job;
        return new JobStatus(
            job.pid(), job.ppid(), job.scheduler().getSimpleName(), job.key(), job.name(),
            job.route() == null ? null : job.route().toString(), job.priority(), entry.state,
            entry.followingPid, entry.queuedAt, entry.startedAt == 0 ? null : entry.startedAt,
            entry.completedAt == 0 ? null : entry.completedAt, job.phase(), job.currentItem(), progress(entry),
            includeItems ? job.items() : java.util.Map.of(), includeItems ? job.itemLabels() : java.util.Map.of(),
            activeChildren(entry, visible)
        );
    }

    private JobProgress progress(Entry<?> entry) {
        JobProgress jobProgress = entry.job.progress();
        if (jobProgress != null) return jobProgress;
        if (entry.childrenTotal == 0) return null;
        return new JobProgress(Math.min(entry.completedChildren, entry.childrenTotal), entry.childrenTotal);
    }

    private List<Long> activeChildren(Entry<?> entry, Set<Long> visible) {
        List<Long> result = new ArrayList<>();
        for (Long childPid : entry.children) {
            if (entries.containsKey(childPid) && (visible == null || visible.contains(childPid))) result.add(childPid);
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private <T> Entry<T> entry(long pid) {
        Entry<?> entry = entries.get(pid);
        if (entry == null) throw new IllegalArgumentException("Unknown job pid=" + pid);
        return (Entry<T>) entry;
    }

    private <T> Entry<T> entry(Job<T> job) {
        Entry<T> entry = entry(job.pid());
        if (entry.job != job) throw new IllegalArgumentException("Job belongs to another runtime");
        return entry;
    }

    private boolean dependsOn(Entry<?> source, Entry<?> parent) {
        Set<Long> visited = new HashSet<>();
        java.util.ArrayDeque<Entry<?>> pending = new java.util.ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty()) {
            Entry<?> dependency = pending.removeFirst();
            if (dependency == parent) return true;
            if (!visited.add(dependency.job.pid())) continue;
            for (Long childPid : dependency.children) {
                Entry<?> child = entries.get(childPid);
                if (child != null && !child.state.terminal()) pending.add(child);
            }
            Entry<?> following = dependency.followingPid == null ? null : entries.get(dependency.followingPid);
            if (following != null && !following.state.terminal()) pending.add(following);
        }
        return false;
    }

    private record DedupKey(Class<?> scheduler, Object route, String key) {}

    private static DedupKey dedupeKey(Class<?> scheduler, Object route, String key) {
        return new DedupKey(scheduler, route, key);
    }

    private void notifyCompletion(Entry<?> entry) {
        notifications.execute(() -> {
            synchronized (Registry.this) {} // Leave the lifecycle monitor before invoking user continuations.
            if (entry.failure == null) complete(entry);
            else { entry.lifecycleFuture.completeExceptionally(entry.failure); entry.future.completeExceptionally(entry.failure); }
        });
    }

    private static JobPriority inheritedPriority(JobPriority parent, JobPriority requested) {
        return parent.ordinal() <= requested.ordinal() ? parent : requested;
    }

    @SuppressWarnings("unchecked")
    private static <T> void complete(Entry<?> entry) {
        ((CompletableFuture<T>) entry.lifecycleFuture).complete((T) entry.result);
        ((CompletableFuture<T>) entry.future).complete((T) entry.result);
    }

    private static RuntimeException rethrow(Throwable exception) {
        if (exception instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException(exception);
    }

    private static final class Entry<T> {

        private final Job<T> job;
        private final CompletableFuture<T> future;
        private final CompletableFuture<T> lifecycleFuture;
        private volatile Thread runner;
        private final List<Long> children;
        private final Long followingPid;
        private final long queuedAt;
        private volatile JobState state;
        private volatile long startedAt;
        private volatile long completedAt;
        private volatile int activeChildren;
        private volatile int completedChildren;
        private volatile int childrenTotal;
        private volatile int pendingCallbacks;
        private int runningCallbacks;
        private volatile boolean bodyFinished;
        private volatile boolean childFailed;
        private volatile boolean released;
        private volatile T result;
        private volatile Throwable failure;

        private Entry(Job<T> job, Long followingPid, long queuedAt) {
            this.job = job;
            this.followingPid = followingPid;
            this.queuedAt = queuedAt;
            future = job.future();
            lifecycleFuture = ((JobFuture<T>) future).termination;
            children = new CopyOnWriteArrayList<>();
            state = JobState.QUEUED;
        }
    }
}
