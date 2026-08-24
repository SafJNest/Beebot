package com.safjnest.lol.queue;

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

import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.model.status.JobStatus;
import com.safjnest.lol.model.status.RunStatus;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.queue.job.JobState;

public final class Registry {

    private static final Registry INSTANCE = new Registry(Clock.systemUTC());

    private final AtomicLong nextPid;
    private final ConcurrentMap<Long, Entry<?>> entries;
    private final ConcurrentMap<String, Long> active;
    private final ThreadLocal<Entry<?>> current;
    private final Clock clock;

    public Registry(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        nextPid = new AtomicLong();
        entries = new ConcurrentHashMap<>();
        active = new ConcurrentHashMap<>();
        current = new ThreadLocal<>();
    }

    public static Registry instance() {
        return INSTANCE;
    }

    synchronized <T> Job<T> create(Class<?> scheduler, Object route, String key, String name, JobPriority priority,
        Function<Job<T>, T> work) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(priority, "priority");
        Entry<?> parent = current.get();
        long ppid = parent == null ? 0 : parent.job.pid();
        long pid = nextPid.incrementAndGet();
        JobPriority effectivePriority = parent == null ? priority : inheritedPriority(parent.job.priority(), priority);
        Job<T> job = new Job<>(pid, ppid, scheduler, route, key, name, effectivePriority, work);
        String dedupeKey = dedupeKey(scheduler, route, key);
        Long followingPid = active.get(dedupeKey);
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
            source.future.whenComplete((value, failure) -> completeFollower(entry, value, failure));
        }
        return job;
    }

    public <T> CompletableFuture<T> future(Job<T> job) {
        return entry(job).future;
    }

    public boolean following(Job<?> job) {
        return entry(job).followingPid != null;
    }

    public void started(Job<?> job) {
        synchronized (this) {
            Entry<?> entry = entry(job);
            if (entry.followingPid != null || entry.state.terminal()) return;
            entry.startedAt = clock.millis();
            entry.state = JobState.RUNNING;
        }
    }

    public <T> T execute(Job<T> job) {
        Entry<T> entry = entry(job);
        Throwable failure = null;
        T result = null;
        Entry<?> previous = current.get();
        current.set(entry);
        try {
            result = job.work().apply(job);
            return result;
        } catch (Throwable exception) {
            failure = exception;
            throw rethrow(exception);
        } finally {
            if (previous == null) current.remove();
            else current.set(previous);
            completeBody(entry, result, failure);
        }
    }

    public boolean bodyFinished(Job<?> job) {
        return entry(job).bodyFinished;
    }

    public void retain(Job<?> job) {
        synchronized (this) {
            Entry<?> entry = entry(job);
            if (entry.state.terminal()) throw new IllegalStateException("Job is not active");
            entry.pendingCallbacks++;
        }
    }

    public void resume(Job<?> job, Runnable callback) {
        Entry<?> entry = entry(job);
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

    public JobStatus status(Job<?> job) {
        return status(entry(job), null, false);
    }

    public List<JobStatus> snapshot() {
        List<JobStatus> result = new ArrayList<>();
        for (Entry<?> entry : entries.values()) result.add(status(entry, null, true));
        result.sort(Comparator.comparingLong(JobStatus::pid));
        return List.copyOf(result);
    }

    public List<JobStatus> statusSnapshot(int maxDepth) {
        return statusSnapshot(maxDepth, 0);
    }

    public List<JobStatus> statusSnapshot(int fullDepth, int nextDepthLimit) {
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
        for (int i = 0; i < nextDepth.size() && i < nextDepthLimit; i++) visible.add(nextDepth.get(i).job.pid());
        List<JobStatus> result = new ArrayList<>();
        for (Long pid : visible) {
            Entry<?> entry = entries.get(pid);
            if (entry != null) result.add(status(entry, visible, false));
        }
        result.sort(Comparator.comparingLong(JobStatus::pid));
        return List.copyOf(result);
    }

    public synchronized void shutdown(String reason) {
        Throwable failure = new java.util.concurrent.CancellationException(reason);
        List<Entry<?>> roots = new ArrayList<>();
        for (Entry<?> entry : entries.values()) if (entry.job.ppid() == 0 && !entry.state.terminal()) roots.add(entry);
        for (Entry<?> root : roots) cancelSubtree(root, failure);
    }

    public List<RunStatus> runs(Class<?> scheduler) {
        List<RunStatus> result = new ArrayList<>();
        for (Entry<?> entry : entries.values()) {
            Job<?> job = entry.job;
            if (job.scheduler() != scheduler || job.route() != null || entry.followingPid != null || entry.state.terminal()) continue;
            String runType = runType(job.key());
            if (runType == null) continue;
            List<JobStatus> children = new ArrayList<>();
            for (Long childPid : entry.children) {
                Entry<?> child = entries.get(childPid);
                if (child == null) continue;
                children.add(status(child, null, false));
            }
            result.add(new RunStatus(
                String.valueOf(job.pid()), runType, entry.state, entry.queuedAt,
                entry.startedAt == 0 ? null : entry.startedAt,
                progress(entry), List.copyOf(children)
            ));
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
        if (entry.failure == null) complete(entry);
        else entry.future.completeExceptionally(entry.failure);
        Entry<?> parent = entries.get(job.ppid());
        if (parent != null) {
            parent.activeChildren--;
            if (entry.state != JobState.COMPLETED) parent.childFailed = true;
            parent.completedChildren++;
            completeIfReady(parent);
        }
        removeIfReleased(entry);
    }

    private void cancelSubtree(Entry<?> entry, Throwable failure) {
        if (entry.state.terminal()) return;
        for (Long childPid : entry.children) {
            Entry<?> child = entries.get(childPid);
            if (child != null) cancelSubtree(child, failure);
        }
        entry.result = null;
        entry.failure = failure;
        entry.bodyFinished = true;
        entry.state = JobState.CANCELLED;
        terminal(entry);
    }

    private <T> void completeFollower(Entry<T> follower, T result, Throwable failure) {
        synchronized (this) {
            if (follower.state.terminal()) return;
            follower.result = result;
            follower.failure = failure;
            follower.bodyFinished = true;
            Entry<?> source = follower.followingPid == null ? null : entries.get(follower.followingPid);
            follower.state = source == null ? JobState.COMPLETED : source.state;
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
        if (!visible.add(entry.job.pid())) return;
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
            entry.completedAt == 0 ? null : entry.completedAt, job.phase(), progress(entry),
            includeItems ? job.items() : java.util.Map.of(), includeItems ? job.itemLabels() : java.util.Map.of(),
            activeChildren(entry, visible)
        );
    }

    private JobProgress progress(Entry<?> entry) {
        if (entry.childrenTotal == 0) return entry.job.progress();
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
        return entry(job.pid());
    }

    private static String runType(String key) {
        if ("tracking".equals(key)) return "TRACKING";
        if (key.startsWith("sample-games:")) return "SAMPLE_GAMES";
        if ("rank-entries".equals(key)) return "RANK_ENTRIES";
        return null;
    }

    private static String dedupeKey(Class<?> scheduler, Object route, String key) {
        return scheduler.getName() + ':' + (route == null ? "<global>" : route) + ':' + key;
    }

    private static JobPriority inheritedPriority(JobPriority parent, JobPriority requested) {
        return parent.ordinal() >= requested.ordinal() ? parent : requested;
    }

    @SuppressWarnings("unchecked")
    private static <T> void complete(Entry<?> entry) {
        ((CompletableFuture<T>) entry.future).complete((T) entry.result);
    }

    private static RuntimeException rethrow(Throwable exception) {
        if (exception instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException(exception);
    }

    private static final class Entry<T> {

        private final Job<T> job;
        private final CompletableFuture<T> future;
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
        private volatile boolean bodyFinished;
        private volatile boolean childFailed;
        private volatile boolean released;
        private volatile T result;
        private volatile Throwable failure;

        private Entry(Job<T> job, Long followingPid, long queuedAt) {
            this.job = job;
            this.followingPid = followingPid;
            this.queuedAt = queuedAt;
            future = new CompletableFuture<>();
            children = new CopyOnWriteArrayList<>();
            state = JobState.QUEUED;
        }
    }
}
