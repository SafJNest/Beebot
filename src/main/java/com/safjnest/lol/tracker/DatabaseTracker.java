package com.safjnest.lol.tracker;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.BuildService;
import com.safjnest.lol.service.ChampionDataRefreshService;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.service.ProfileStatisticsService;
import com.safjnest.lol.service.ProfileMatchupsService;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class DatabaseTracker {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final BlockingQueue<DatabaseTask<?>> TASK_QUEUE = new LinkedBlockingQueue<>();
    private static final BlockingQueue<DatabaseTask<?>> BUILD_TASK_QUEUE = new LinkedBlockingQueue<>();
    private static final WorkerState BUILD_WORKER = new WorkerState(1, "build", BUILD_TASK_QUEUE);
    private static final WorkerState GENERAL_WORKER = new WorkerState(2, "general", TASK_QUEUE);
    private static final ConcurrentMap<String, CompletableFuture<?>> TASKS = new ConcurrentHashMap<>();
    private static final ProfileStatisticsService PROFILE_STATISTICS_SERVICE = new ProfileStatisticsService();
    private static final ProfileMatchupsService PROFILE_MATCHUPS_SERVICE = new ProfileMatchupsService();
    private static final ChampionDataRefreshService CHAMPION_DATA_REFRESH_SERVICE = new ChampionDataRefreshService();
    private static ExecutorService buildWorkerExecutor;
    private static ExecutorService generalWorkerExecutor;

    private DatabaseTracker() {}

    public static <T> CompletableFuture<T> submit(String key, Supplier<T> supplier) {
        return submit(key, key, supplier, false);
    }

    static <T> CompletableFuture<T> submitBuild(String key, Supplier<T> supplier) {
        return submit(key, key, supplier, true);
    }

    private static <T> CompletableFuture<T> submit(
        String key,
        String name,
        Supplier<T> supplier,
        boolean buildTask
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(supplier, "supplier");

        synchronized (LIFECYCLE_LOCK) {
            startWorkers();
            while (true) {
                CompletableFuture<?> existing = TASKS.get(key);
                if (existing != null) {
                    if (!existing.isDone()) return cast(existing);
                    if (!TASKS.remove(key, existing)) continue;
                }

                CompletableFuture<T> future = new CompletableFuture<>();
                if (TASKS.putIfAbsent(key, future) == null) {
                    DatabaseTask<T> task = new DatabaseTask<>(key, name, supplier, future);
                    (buildTask ? BUILD_WORKER : GENERAL_WORKER).enqueue(task);
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
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || filter == null)
            return CompletableFuture.completedFuture(false);

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        ProfileStatisticsRequest request = ProfileStatisticsRequest.from(summoner, requestFilter);
        String key = "profile-statistics:" + request.puuid() + ":" + requestFilter.toSummonerKey();
        String name = "profile statistics puuid=" + request.puuid();
        return submit(key, name, () -> refreshProfileStatistics(request), false);
    }

    public static CompletableFuture<Boolean> startProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null)
            return CompletableFuture.completedFuture(false);

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        ProfileMatchupsRequest request = new ProfileMatchupsRequest(puuid, shard, requestFilter);
        String key = "profile-matchups:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile matchups puuid=" + puuid;
        return submit(key, name, () -> refreshProfileMatchups(request), false);
    }

    public static CompletableFuture<Void> startChampionData(
        Filter filter,
        boolean statsMissing,
        boolean buildMissing
    ) {
        if (filter == null || filter.champion() == 0) return CompletableFuture.completedFuture(null);
        if (!statsMissing && !buildMissing) return CompletableFuture.completedFuture(null);

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        CompletableFuture<?> statistics = statsMissing
            ? startChampionStatsMatrix(requestFilter)
            : CompletableFuture.completedFuture(null);
        CompletableFuture<Boolean> build = buildMissing
            ? startChampionBuild(requestFilter)
            : CompletableFuture.completedFuture(true);
        return CompletableFuture.allOf(statistics, build);
    }

    public static CompletableFuture<Void> enqueueChampionDataRefresh() {
        String patch = new Filter().patch();
        String key = "champion-data-refresh:" + patch;
        return submit(key, "champion data refresh patch=" + patch, () -> {
            CHAMPION_DATA_REFRESH_SERVICE.refresh();
            return null;
        }, false);
    }

    public static CompletableFuture<ChampionDataRefreshService.MatrixRefreshResult> enqueueChampionStatsMatrix(
            String patch, GameQueueType queue) {
        if (patch == null || patch.isBlank() || queue == null)
            return CompletableFuture.completedFuture(new ChampionDataRefreshService.MatrixRefreshResult(0, 0, 0, 0, 0));
        String key = championStatsMatrixKey(patch, queue);
        String name = "champion stats matrix patch=" + patch + " queue=" + queue.name();
        return submit(key, name, () -> CHAMPION_DATA_REFRESH_SERVICE.refreshStatsMatrix(patch, queue), false);
    }

    static String championStatsMatrixKey(String patch, GameQueueType queue) {
        return "champion-stats-matrix:" + patch + ":" + queue.name();
    }

    public static void shutdown() {
        ExecutorService buildExecutor;
        ExecutorService taskExecutor;
        synchronized (LIFECYCLE_LOCK) {
            buildExecutor = buildWorkerExecutor;
            taskExecutor = generalWorkerExecutor;
            buildWorkerExecutor = null;
            generalWorkerExecutor = null;
            if (buildExecutor == null && taskExecutor == null) return;

            if (buildExecutor != null) buildExecutor.shutdownNow();
            if (taskExecutor != null) taskExecutor.shutdownNow();
            cancelPendingTasks(BUILD_WORKER);
            cancelPendingTasks(GENERAL_WORKER);
        }

        awaitTermination(buildExecutor);
        awaitTermination(taskExecutor);
    }

    // ============================================================================

    private static CompletableFuture<ChampionDataRefreshService.MatrixRefreshResult> startChampionStatsMatrix(Filter filter) {
        return enqueueChampionStatsMatrix(filter.patch(), filter.queue());
    }

    private static CompletableFuture<Boolean> startChampionBuild(Filter filter) {
        if (BuildService.hasStored(filter)) return CompletableFuture.completedFuture(true);
        String key = "champion-build:" + filter.toKey();
        String name = "champion build champion=" + filter.champion()
            + " patch=" + filter.patch()
            + " queue=" + filter.queue()
            + " rank=" + filter.rank()
            + " region=" + filter.region()
            + " lane=" + filter.lane();
        return submit(key, name, () -> refreshChampionBuild(filter), true);
    }

    private static boolean refreshProfileStatistics(ProfileStatisticsRequest request) {
        try {
            LeagueShard shard = LeagueShard.valueOf(request.region());
            if (!PROFILE_STATISTICS_SERVICE.refresh(request.puuid(), shard, request.filter(), false)) {
                BotLogger.error("Profile statistics refresh failed for summoner=" + request.puuid());
                return false;
            }

            LeagueService.invalidateProfilePage(request.puuid(), shard);
            BotLogger.info("[LPTracker] Updated summoner overview for "
                + request.riotId() + " (" + shard + ", id="
                + request.summonerId() + ") | profile statistics persisted, Redis profile page invalidated");
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile statistics refresh failed for summoner=" + request.puuid()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshProfileMatchups(ProfileMatchupsRequest request) {
        try {
            if (!PROFILE_MATCHUPS_SERVICE.refresh(request.puuid(), request.shard(), request.filter())) {
                BotLogger.error("Profile matchups refresh failed for puuid=" + request.puuid());
                return false;
            }
            BotLogger.info("[LPTracker] Updated profile matchups for puuid=" + request.puuid()
                + " (" + request.shard() + ") | aggregate persisted");
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile matchups refresh failed for puuid=" + request.puuid()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshChampionBuild(Filter filter) {
        try {
            boolean refreshed = CHAMPION_DATA_REFRESH_SERVICE.refreshBuild(filter);
            if (!refreshed) BotLogger.error("Champion build refresh failed for filter=" + filter.toKey());
            return refreshed;
        } catch (Exception exception) {
            BotLogger.error("Champion build refresh failed for filter=" + filter.toKey()
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static void startWorkers() {
        if (buildWorkerExecutor != null && !buildWorkerExecutor.isShutdown()
            && generalWorkerExecutor != null && !generalWorkerExecutor.isShutdown()) return;

        buildWorkerExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("lol-db-build-worker-", 0).factory()
        );
        generalWorkerExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("lol-db-worker-", 0).factory()
        );
        buildWorkerExecutor.submit(() -> runWorker(BUILD_WORKER));
        generalWorkerExecutor.submit(() -> runWorker(GENERAL_WORKER));
    }

    private static void runWorker(WorkerState worker) {
        while (!Thread.currentThread().isInterrupted()) {
            DatabaseTask<?> task;
            try {
                task = worker.queue.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            long started = worker.started.incrementAndGet();
            long submitted = worker.submitted.get();
            logInfo("[DatabaseTracker] doing job " + task.name()
                + " on worker " + worker.id + " (" + worker.type + "), "
                + started + "/" + submitted + ", queue " + worker.queue.size());

            Throwable failure = task.execute();
            long finished = worker.finished.incrementAndGet();
            String state = failure == null ? "SUCCESS" : "FAILED";
            logInfo("[DatabaseTracker] finished job " + task.name()
                + " on worker " + worker.id + " (" + worker.type + "), "
                + finished + "/" + worker.submitted.get()
                + ", queue " + worker.queue.size() + ", state=" + state);
        }
    }

    private static void cancelPendingTasks(WorkerState worker) {
        DatabaseTask<?> task;
        while ((task = worker.queue.poll()) != null) {
            task.cancel();
            TASKS.remove(task.key(), task.future());
            logInfo("[DatabaseTracker] finished job " + task.name()
                + " on worker " + worker.id + " (" + worker.type + ")"
                + ", queue " + worker.queue.size() + ", state=CANCELLED");
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

    private static void logInfo(String message) {
        try {
            BotLogger.info(message);
        } catch (Throwable ignored) { }
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
        Filter filter
    ) {

        private static ProfileStatisticsRequest from(Summoner summoner, Filter filter) {
            return new ProfileStatisticsRequest(
                summoner.summonerId(),
                summoner.puuid(),
                summoner.riotId(),
                summoner.region(),
                filter
            );
        }
    }

    private record ProfileMatchupsRequest(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {}

    private static final class WorkerState {

        private final int id;
        private final String type;
        private final BlockingQueue<DatabaseTask<?>> queue;
        private final AtomicLong submitted = new AtomicLong();
        private final AtomicLong started = new AtomicLong();
        private final AtomicLong finished = new AtomicLong();

        private WorkerState(int id, String type, BlockingQueue<DatabaseTask<?>> queue) {
            this.id = id;
            this.type = type;
            this.queue = queue;
        }

        private void enqueue(DatabaseTask<?> task) {
            long total = submitted.incrementAndGet();
            queue.offer(task);
            logInfo("[DatabaseTracker] add job " + task.name()
                + " to worker " + id + " (" + type + "), queue " + queue.size()
                + ", total " + total);
        }
    }

    private record DatabaseTask<T>(String key, String name, Supplier<T> supplier, CompletableFuture<T> future) {

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
                    TASKS.remove(key, future);
                }
            }
            return failure;
        }

        private void cancel() {
            future.completeExceptionally(new CancellationException("Database task cancelled during shutdown"));
        }
    }
}
