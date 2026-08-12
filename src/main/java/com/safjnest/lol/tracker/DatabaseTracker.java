package com.safjnest.lol.tracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.ChampionService;
import com.safjnest.lol.service.ProfileService;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class DatabaseTracker {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final ProfileTaskQueues PROFILE_TASK_QUEUES = new ProfileTaskQueues();
    private static final BlockingQueue<DatabaseTask<?>> CHAMPION_TASK_QUEUE = new LinkedBlockingQueue<>();
    private static final WorkerState PROFILE_WORKER = new WorkerState(1, "profile", null, PROFILE_TASK_QUEUES);
    private static final WorkerState CHAMPION_WORKER = new WorkerState(2, "champion", CHAMPION_TASK_QUEUE, PROFILE_TASK_QUEUES);
    private static final ConcurrentMap<String, DatabaseTask<?>> TASKS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ChampionMatrixRequest> CHAMPION_MATRICES = new ConcurrentHashMap<>();
    private static final ProfileService PROFILE_SERVICE = new ProfileService();
    private static final ChampionService CHAMPION_SERVICE = new ChampionService();
    private static ExecutorService profileWorkerExecutor;
    private static ExecutorService championWorkerExecutor;

    private DatabaseTracker() {}

    public static <T> CompletableFuture<T> submit(String key, Supplier<T> supplier) {
        return submit(key, key, supplier, PROFILE_WORKER, TaskPriority.ON_DEMAND);
    }

    static <T> CompletableFuture<T> submitBuild(String key, Supplier<T> supplier) {
        return submit(key, key, supplier, CHAMPION_WORKER, TaskPriority.ON_DEMAND);
    }

    static <T> CompletableFuture<T> submitManual(String key, Supplier<T> supplier) {
        return submit(key, key, supplier, PROFILE_WORKER, TaskPriority.MANUAL);
    }

    static <T> CompletableFuture<T> submitStale(String key, Supplier<T> supplier) {
        return submit(key, key, supplier, PROFILE_WORKER, TaskPriority.STALE);
    }

    private static <T> CompletableFuture<T> submit(
        String key,
        String name,
        Supplier<T> supplier,
        WorkerState worker,
        TaskPriority priority
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");

        synchronized (LIFECYCLE_LOCK) {
            startWorkers();
            while (true) {
                DatabaseTask<?> existing = TASKS.get(key);
                if (existing != null) {
                    if (!existing.future().isDone()) {
                        if (worker == PROFILE_WORKER && existing.promote(priority)) {
                            PROFILE_TASK_QUEUES.promote(existing);
                        }
                        return cast(existing.future());
                    }
                    if (!TASKS.remove(key, existing)) continue;
                }

                CompletableFuture<T> future = new CompletableFuture<>();
                DatabaseTask<T> task = new DatabaseTask<>(key, name, supplier, future, priority);
                if (TASKS.putIfAbsent(key, task) == null) {
                    worker.enqueue(task);
                    return future;
                }
            }
        }
    }

    public static CompletableFuture<Boolean> startProfileStatistics(
        Summoner summoner,
        SeasonUtils.SeasonRange season
    ) {
        if (season == null) return CompletableFuture.completedFuture(false);
        return startProfileStatistics(summoner, Filter.summoner(season.start(), season.end()));
    }

    public static CompletableFuture<Boolean> startProfileStatistics(Summoner summoner, Filter filter) {
        return startProfileStatistics(summoner, filter, false);
    }

    public static CompletableFuture<Boolean> startProfileStatistics(
        Summoner summoner,
        Filter filter,
        boolean rebuild
    ) {
        return startProfileStatistics(summoner, filter, rebuild, TaskPriority.ON_DEMAND);
    }

    private static CompletableFuture<Boolean> startProfileStatistics(
        Summoner summoner,
        Filter filter,
        boolean rebuild,
        TaskPriority priority
    ) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || filter == null)
            return CompletableFuture.completedFuture(false);

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        ProfileStatisticsRequest request = ProfileStatisticsRequest.from(summoner, requestFilter, rebuild);
        String key = "profile-statistics:" + request.puuid() + ":" + requestFilter.toSummonerKey();
        String name = "profile statistics " + (rebuild ? "rebuild " : "") + "puuid=" + request.puuid();
        return submit(key, name, () -> refreshProfileStatistics(request), PROFILE_WORKER, priority);
    }

    public static CompletableFuture<Boolean> startStaleProfileStatistics(Summoner summoner, Filter filter) {
        return startProfileStatistics(summoner, filter, false, TaskPriority.STALE);
    }

    public static CompletableFuture<Boolean> startProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileMatchups(puuid, shard, filter, TaskPriority.ON_DEMAND);
    }

    private static CompletableFuture<Boolean> startProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter,
        TaskPriority priority
    ) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null)
            return CompletableFuture.completedFuture(false);

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        ProfileMatchupsRequest request = new ProfileMatchupsRequest(puuid, shard, requestFilter);
        String key = "profile-matchups:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile matchups puuid=" + puuid;
        return submit(key, name, () -> refreshProfileMatchups(request), PROFILE_WORKER, priority);
    }

    public static CompletableFuture<Boolean> startStaleProfileMatchups(String puuid, LeagueShard shard, Filter filter) {
        return startProfileMatchups(puuid, shard, filter, TaskPriority.STALE);
    }

    public static CompletableFuture<Boolean> startProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileActivity(puuid, shard, filter, TaskPriority.ON_DEMAND);
    }

    private static CompletableFuture<Boolean> startProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter,
        TaskPriority priority
    ) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null)
            return CompletableFuture.completedFuture(false);
        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        String key = "profile-activity:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile activity puuid=" + puuid;
        return submit(key, name, () -> refreshProfileActivity(new ProfileActivityRequest(puuid, shard, requestFilter)), PROFILE_WORKER, priority);
    }

    public static CompletableFuture<Boolean> startStaleProfileActivity(String puuid, LeagueShard shard, Filter filter) {
        return startProfileActivity(puuid, shard, filter, TaskPriority.STALE);
    }

    public static CompletableFuture<Boolean> startProfileRefresh(
        Summoner summoner,
        LeagueShard shard
    ) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || shard == null) {
            return CompletableFuture.completedFuture(false);
        }
        ProfileRefreshRequest request = new ProfileRefreshRequest(summoner.puuid(), shard);
        String key = profileRefreshKey(request.puuid());
        String name = "profile refresh puuid=" + request.puuid();
        return submit(key, name, () -> refreshProfileAggregates(request), PROFILE_WORKER, TaskPriority.MANUAL);
    }

    public static CompletableFuture<Void> startChampionData(
        Filter filter,
        boolean statsMissing,
        boolean buildMissing
    ) {
        if (filter == null || filter.champion() == 0) return CompletableFuture.completedFuture(null);
        if (!statsMissing && !buildMissing) return CompletableFuture.completedFuture(null);

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        if (!statsMissing) return buildMissing
            ? startChampionBuild(requestFilter).thenApply(ignored -> null)
            : CompletableFuture.completedFuture(null);
        List<String> patches = PatchUtils.getRecentPatches(3);
        if (!patches.contains(requestFilter.patch())) return enqueueChampionStatsMatrix(
            requestFilter.patch(), requestFilter.queue(), buildMissing ? requestFilter : null
        ).thenApply(ignored -> null);
        List<CompletableFuture<ChampionService.MatrixRefreshResult>> futures = new ArrayList<>();
        for (String patch : patches) futures.add(enqueueChampionStatsMatrix(
            patch, requestFilter.queue(), buildMissing && patch.equals(requestFilter.patch()) ? requestFilter : null
        ));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public static CompletableFuture<Void> enqueueChampionDataRefresh() {
        String patch = new Filter().patch();
        String key = "champion-data-refresh:" + patch;
        return submit(key, "champion data refresh patch=" + patch, () -> {
            CHAMPION_SERVICE.refresh();
            return null;
        }, CHAMPION_WORKER, TaskPriority.ON_DEMAND);
    }

    public static CompletableFuture<ChampionService.MatrixRefreshResult> enqueueChampionStatsMatrix(
            String patch, GameQueueType queue) {
        return enqueueChampionStatsMatrix(patch, queue, null);
    }

    private static CompletableFuture<ChampionService.MatrixRefreshResult> enqueueChampionStatsMatrix(
            String patch, GameQueueType queue, Filter buildFilter) {
        if (patch == null || patch.isBlank() || queue == null)
            return CompletableFuture.completedFuture(new ChampionService.MatrixRefreshResult(0, 0, 0, 0, 0));
        String key = championStatsMatrixKey(patch, queue);
        String name = "champion stats matrix patch=" + patch + " queue=" + queue.name();
        synchronized (LIFECYCLE_LOCK) {
            ChampionMatrixRequest existing = CHAMPION_MATRICES.get(key);
            if (existing != null) {
                if (!existing.running()) existing.addBuild(buildFilter);
                else if (buildFilter != null) startChampionBuild(buildFilter);
                return existing.future();
            }
            ChampionMatrixRequest request = new ChampionMatrixRequest();
            request.addBuild(buildFilter);
            CHAMPION_MATRICES.put(key, request);
            CompletableFuture<ChampionService.MatrixRefreshResult> future = submit(key, name, () -> {
                request.start();
                try {
                    return CHAMPION_SERVICE.refreshStatisticsMatrix(patch, queue, request.buildFilters());
                } finally {
                    CHAMPION_MATRICES.remove(key, request);
                }
            }, CHAMPION_WORKER, TaskPriority.ON_DEMAND);
            request.setFuture(future);
            return future;
        }
    }

    public static CompletableFuture<Void> enqueueRecentChampionStatsMatrices(GameQueueType queue) {
        if (queue == null) return CompletableFuture.completedFuture(null);
        List<String> patches = PatchUtils.getRecentPatches(3);
        if (patches.isEmpty()) return CompletableFuture.completedFuture(null);
        List<CompletableFuture<ChampionService.MatrixRefreshResult>> futures = new ArrayList<>();
        for (String patch : patches) futures.add(enqueueChampionStatsMatrix(patch, queue));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    static String championStatsMatrixKey(String patch, GameQueueType queue) {
        return "champion-stats-matrix:" + patch + ":" + queue.name();
    }

    static String profileRefreshKey(String puuid) {
        return "profile-refresh:" + puuid;
    }

    public static void shutdown() {
        ExecutorService buildExecutor;
        ExecutorService taskExecutor;
        synchronized (LIFECYCLE_LOCK) {
            buildExecutor = championWorkerExecutor;
            taskExecutor = profileWorkerExecutor;
            championWorkerExecutor = null;
            profileWorkerExecutor = null;
            if (buildExecutor == null && taskExecutor == null) return;

            if (buildExecutor != null) buildExecutor.shutdownNow();
            if (taskExecutor != null) taskExecutor.shutdownNow();
            cancelPendingTasks(CHAMPION_WORKER);
            cancelPendingTasks(PROFILE_WORKER);
        }

        awaitTermination(buildExecutor);
        awaitTermination(taskExecutor);
    }

    public static List<WorkerStatus> workerStatuses() {
        synchronized (LIFECYCLE_LOCK) {
            boolean profileRunning = profileWorkerExecutor != null && !profileWorkerExecutor.isShutdown();
            boolean championRunning = championWorkerExecutor != null && !championWorkerExecutor.isShutdown();
            return List.of(PROFILE_WORKER.status(profileRunning), CHAMPION_WORKER.status(championRunning));
        }
    }

    // ============================================================================

    private static CompletableFuture<Boolean> startChampionBuild(Filter filter) {
        String key = "champion-build:" + filter.toKey();
        String name = "champion build champion=" + filter.champion()
            + " patch=" + filter.patch()
            + " queue=" + filter.queue()
            + " rank=" + filter.rank()
            + " region=" + filter.region()
            + " lane=" + filter.lane();
        return submit(key, name, () -> refreshChampionBuild(filter), CHAMPION_WORKER, TaskPriority.ON_DEMAND);
    }

    private static boolean refreshProfileStatistics(ProfileStatisticsRequest request) {
        try {
            LeagueShard shard = LeagueShard.valueOf(request.region());
            if (!PROFILE_SERVICE.refreshStatistics(request.puuid(), shard, request.filter(), request.rebuild())) {
                BotLogger.error("Profile statistics refresh failed for summoner=" + request.puuid());
                return false;
            }

            ProfileService.invalidate(request.puuid(), shard);
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile statistics refresh failed for summoner=" + request.puuid()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshProfileMatchups(ProfileMatchupsRequest request) {
        try {
            if (!PROFILE_SERVICE.refreshMatchups(request.puuid(), request.shard(), request.filter())) {
                BotLogger.error("Profile matchups refresh failed for puuid=" + request.puuid());
                return false;
            }
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile matchups refresh failed for puuid=" + request.puuid()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshProfileActivity(ProfileActivityRequest request) {
        try {
            if (!PROFILE_SERVICE.refreshActivity(request.shard(), request.puuid(), request.filter())) {
                BotLogger.error("Profile activity refresh failed for puuid=" + request.puuid());
                return false;
            }
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile activity refresh failed for puuid=" + request.puuid()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshProfileAggregates(ProfileRefreshRequest request) {
        try {
            if (!PROFILE_SERVICE.refreshCanonicalAggregates(request.shard(), request.puuid())) {
                BotLogger.error("Profile aggregate refresh failed for puuid=" + request.puuid());
                return false;
            }
            ProfileService.invalidate(request.puuid(), request.shard());
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile aggregate refresh failed for puuid=" + request.puuid()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshChampionBuild(Filter filter) {
        try {
            boolean refreshed = CHAMPION_SERVICE.refreshBuild(filter);
            if (!refreshed) BotLogger.error("Champion build refresh failed for filter=" + filter.toKey());
            return refreshed;
        } catch (Exception exception) {
            BotLogger.error("Champion build refresh failed for filter=" + filter.toKey()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static void startWorkers() {
        if (profileWorkerExecutor != null && !profileWorkerExecutor.isShutdown()
            && championWorkerExecutor != null && !championWorkerExecutor.isShutdown()) return;

        profileWorkerExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("lol-db-profile-worker-", 0).factory()
        );
        championWorkerExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("lol-db-champion-worker-", 0).factory()
        );
        profileWorkerExecutor.submit(() -> runWorker(PROFILE_WORKER));
        championWorkerExecutor.submit(() -> runWorker(CHAMPION_WORKER));
    }

    private static void runWorker(WorkerState worker) {
        while (!Thread.currentThread().isInterrupted()) {
            DatabaseTask<?> task;
            try {
                task = worker.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            worker.currentTask = task;
            worker.currentStartedAt = System.currentTimeMillis();
            worker.started.incrementAndGet();
            try {
                task.execute();
            } finally {
                worker.finished.incrementAndGet();
                worker.currentTask = null;
                worker.currentStartedAt = 0;
            }
        }
    }

    private static void cancelPendingTasks(WorkerState worker) {
        DatabaseTask<?> task;
        while ((task = worker.poll()) != null) {
            task.cancel();
            TASKS.remove(task.key(), task);
        }
    }

    private static void awaitTermination(ExecutorService executor) {
        if (executor == null) return;
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> CompletableFuture<T> cast(CompletableFuture<?> future) {
        return (CompletableFuture<T>) future;
    }

    private record ProfileStatisticsRequest(
        int summonerId,
        String puuid,
        String riotId,
        String region,
        Filter filter,
        boolean rebuild
    ) {

        private static ProfileStatisticsRequest from(Summoner summoner, Filter filter, boolean rebuild) {
            return new ProfileStatisticsRequest(
                summoner.summonerId(),
                summoner.puuid(),
                summoner.riotId(),
                summoner.region(),
                filter,
                rebuild
            );
        }
    }

    private record ProfileMatchupsRequest(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {}

    private record ProfileActivityRequest(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {}

    private record ProfileRefreshRequest(
        String puuid,
        LeagueShard shard
    ) {}

    private static final class ChampionMatrixRequest {

        private final ConcurrentMap<String, Filter> buildFilters = new ConcurrentHashMap<>();
        private CompletableFuture<ChampionService.MatrixRefreshResult> future;
        private boolean running;

        private synchronized void addBuild(Filter filter) {
            if (filter != null) buildFilters.putIfAbsent(filter.toKey(), Filter.fromStateKey(filter.toStateKey()));
        }

        private synchronized void start() {
            running = true;
        }

        private synchronized boolean running() {
            return running;
        }

        private synchronized void setFuture(CompletableFuture<ChampionService.MatrixRefreshResult> value) {
            future = value;
        }

        private synchronized CompletableFuture<ChampionService.MatrixRefreshResult> future() {
            return future;
        }

        private List<Filter> buildFilters() {
            return new ArrayList<>(buildFilters.values());
        }
    }

    private static final class ProfileTaskQueues {

        private final BlockingQueue<DatabaseTask<?>> manual = new LinkedBlockingQueue<>();
        private final BlockingQueue<DatabaseTask<?>> onDemand = new LinkedBlockingQueue<>();
        private final BlockingQueue<DatabaseTask<?>> stale = new LinkedBlockingQueue<>();
        private final Semaphore available = new Semaphore(0);

        private void offer(DatabaseTask<?> task) {
            queue(task.priority()).offer(task);
            available.release();
        }

        private void promote(DatabaseTask<?> task) {
            if (manual.remove(task) || onDemand.remove(task) || stale.remove(task)) {
                queue(task.priority()).offer(task);
            }
        }

        private DatabaseTask<?> take() throws InterruptedException {
            available.acquire();
            return next();
        }

        private DatabaseTask<?> poll(long timeout, TimeUnit unit) throws InterruptedException {
            return available.tryAcquire(timeout, unit) ? next() : null;
        }

        private DatabaseTask<?> poll() {
            return available.tryAcquire() ? next() : null;
        }

        private List<DatabaseTask<?>> snapshot() {
            List<DatabaseTask<?>> result = new ArrayList<>();
            result.addAll(manual);
            result.addAll(onDemand);
            result.addAll(stale);
            return result;
        }

        private DatabaseTask<?> next() {
            DatabaseTask<?> task = manual.poll();
            if (task != null) return task;
            task = onDemand.poll();
            return task != null ? task : stale.poll();
        }

        private BlockingQueue<DatabaseTask<?>> queue(TaskPriority priority) {
            return switch (priority) {
                case MANUAL -> manual;
                case ON_DEMAND -> onDemand;
                case STALE -> stale;
            };
        }
    }

    private static final class WorkerState {

        private final int id;
        private final String type;
        private final BlockingQueue<DatabaseTask<?>> queue;
        private final ProfileTaskQueues profileQueues;
        private final AtomicLong submitted = new AtomicLong();
        private final AtomicLong started = new AtomicLong();
        private final AtomicLong finished = new AtomicLong();
        private volatile DatabaseTask<?> currentTask;
        private volatile long currentStartedAt;

        private WorkerState(int id, String type, BlockingQueue<DatabaseTask<?>> queue,
                            ProfileTaskQueues profileQueues) {
            this.id = id;
            this.type = type;
            this.queue = queue;
            this.profileQueues = profileQueues;
        }

        private void enqueue(DatabaseTask<?> task) {
            submitted.incrementAndGet();
            if (queue == null) profileQueues.offer(task);
            else queue.offer(task);
        }

        private DatabaseTask<?> take() throws InterruptedException {
            if (queue == null) return profileQueues.take();
            while (true) {
                DatabaseTask<?> task = queue.poll();
                if (task != null) return task;
                task = profileQueues.poll(250, TimeUnit.MILLISECONDS);
                if (task != null) return task;
            }
        }

        private DatabaseTask<?> poll() {
            return queue == null ? profileQueues.poll() : queue.poll();
        }

        private WorkerStatus status(boolean running) {
            List<String> queued = new ArrayList<>();
            if (queue == null) for (DatabaseTask<?> task : profileQueues.snapshot()) queued.add(task.name());
            else for (DatabaseTask<?> task : queue) queued.add(task.name());
            DatabaseTask<?> task = currentTask;
            return new WorkerStatus(
                id,
                type,
                running,
                task == null ? null : task.name(),
                currentStartedAt,
                submitted.get(),
                started.get(),
                finished.get(),
                List.copyOf(queued)
            );
        }
    }

    public record WorkerStatus(
        int id,
        String type,
        boolean running,
        String currentJob,
        long currentStartedAt,
        long submitted,
        long started,
        long finished,
        List<String> queuedJobs
    ) {}

    private enum TaskPriority {
        MANUAL,
        ON_DEMAND,
        STALE
    }

    private static final class DatabaseTask<T> {
        private final String key;
        private final String name;
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future;
        private volatile TaskPriority priority;

        private DatabaseTask(
            String key,
            String name,
            Supplier<T> supplier,
            CompletableFuture<T> future,
            TaskPriority priority
        ) {
            this.key = key;
            this.name = name;
            this.supplier = supplier;
            this.future = future;
            this.priority = priority;
        }

        private String key() {
            return key;
        }

        private String name() {
            return name;
        }

        private CompletableFuture<T> future() {
            return future;
        }

        private TaskPriority priority() {
            return priority;
        }

        private boolean promote(TaskPriority value) {
            if (value.ordinal() >= priority.ordinal()) return false;
            priority = value;
            return true;
        }

        private Throwable execute() {
            T result = null;
            Throwable failure = null;
            try {
                result = supplier.get();
            } catch (Throwable exception) {
                failure = exception;
            } finally {
                try {
                    if (failure == null) {
                        future.complete(result);
                    } else {
                        future.completeExceptionally(failure);
                        try {
                            BotLogger.error("Database task failed for key=" + key + " message=" + failure.getMessage());
                        } catch (Throwable ignored) { }
                    }
                } finally {
                    TASKS.remove(key, this);
                }
            }
            return failure;
        }

        private void cancel() {
            future.completeExceptionally(new CancellationException("Database task cancelled during shutdown"));
        }
    }
}
