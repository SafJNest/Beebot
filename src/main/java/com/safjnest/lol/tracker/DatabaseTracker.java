package com.safjnest.lol.tracker;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.BuildService;
import com.safjnest.lol.service.ChampionDataRefreshService;
import com.safjnest.lol.service.ChampionStatsService;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.service.ProfileStatisticsService;
import com.safjnest.lol.service.ProfileMatchupsService;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class DatabaseTracker {

    private static final int WORKER_COUNT = 2;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30;
    private static final Object LIFECYCLE_LOCK = new Object();
    private static final BlockingQueue<DatabaseTask<?>> TASK_QUEUE = new LinkedBlockingQueue<>();
    private static final ConcurrentMap<String, CompletableFuture<?>> TASKS = new ConcurrentHashMap<>();
    private static final Set<String> CHAMPION_STATS_COMPLETED = ConcurrentHashMap.newKeySet();
    private static final ProfileStatisticsService PROFILE_STATISTICS_SERVICE = new ProfileStatisticsService();
    private static final ProfileMatchupsService PROFILE_MATCHUPS_SERVICE = new ProfileMatchupsService();
    private static final ChampionDataRefreshService CHAMPION_DATA_REFRESH_SERVICE = new ChampionDataRefreshService();
    private static ExecutorService workerExecutor;

    private DatabaseTracker() {}

    public static <T> CompletableFuture<T> submit(String key, Supplier<T> supplier) {
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
                    TASK_QUEUE.offer(new DatabaseTask<>(key, supplier, future));
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
        return submit(key, () -> refreshProfileStatistics(request));
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
        return submit(key, () -> refreshProfileMatchups(request));
    }

    public static CompletableFuture<Void> startChampionData(
        Filter filter,
        boolean statsMissing,
        boolean buildMissing
    ) {
        if (filter == null || filter.champion() == 0) return CompletableFuture.completedFuture(null);
        if (!statsMissing && !buildMissing) return CompletableFuture.completedFuture(null);

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        CompletableFuture<Map<Integer, ChampionStatistics>> statistics = statsMissing
            ? startChampionStats(requestFilter)
            : CompletableFuture.completedFuture(Map.of());
        CompletableFuture<Boolean> build = buildMissing
            ? startChampionBuild(requestFilter)
            : CompletableFuture.completedFuture(true);
        return CompletableFuture.allOf(statistics, build);
    }

    public static CompletableFuture<Void> enqueueChampionDataRefresh() {
        String patch = new Filter().patch();
        String key = "champion-data-refresh:" + patch;
        return submit(key, () -> {
            CHAMPION_STATS_COMPLETED.clear();
            CHAMPION_DATA_REFRESH_SERVICE.refresh();
            return null;
        });
    }

    public static void shutdown() {
        ExecutorService executor;
        synchronized (LIFECYCLE_LOCK) {
            executor = workerExecutor;
            workerExecutor = null;
            if (executor == null) return;

            executor.shutdownNow();
            DatabaseTask<?> task;
            while ((task = TASK_QUEUE.poll()) != null) {
                task.cancel();
                TASKS.remove(task.key(), task.future());
            }
        }

        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    // ============================================================================

    private static CompletableFuture<Map<Integer, ChampionStatistics>> startChampionStats(Filter filter) {
        String key = filter.genericKey();
        if (ChampionStatsService.hasStored(filter) || CHAMPION_STATS_COMPLETED.contains(key))
            return CompletableFuture.completedFuture(Map.of());

        return submit("champion-stats:" + key, () -> refreshChampionStats(filter, key));
    }

    private static CompletableFuture<Boolean> startChampionBuild(Filter filter) {
        if (BuildService.hasStored(filter)) return CompletableFuture.completedFuture(true);
        return submit("champion-build:" + filter.toKey(), () -> refreshChampionBuild(filter));
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

    private static Map<Integer, ChampionStatistics> refreshChampionStats(Filter filter, String key) {
        try {
            Map<Integer, ChampionStatistics> stats = CHAMPION_DATA_REFRESH_SERVICE.refreshStats(filter);
            if (stats == null) throw new IllegalStateException("refresh returned null");
            CHAMPION_STATS_COMPLETED.add(key);
            if (stats.isEmpty()) BotLogger.warning("Champion stats refresh completed with no data for filter=" + key);
            return stats;
        } catch (Exception exception) {
            BotLogger.error("Champion stats refresh failed for filter=" + key
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
        if (workerExecutor != null && !workerExecutor.isShutdown()) return;

        workerExecutor = Executors.newFixedThreadPool(
            WORKER_COUNT,
            Thread.ofVirtual().name("lol-db-worker-", 0).factory()
        );
        for (int i = 0; i < WORKER_COUNT; i++) workerExecutor.submit(DatabaseTracker::runWorker);
    }

    private static void runWorker() {
        while (!Thread.currentThread().isInterrupted()) {
            DatabaseTask<?> task;
            try {
                task = TASK_QUEUE.take();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            task.execute();
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

    private record DatabaseTask<T>(String key, Supplier<T> supplier, CompletableFuture<T> future) {

        private void execute() {
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
        }

        private void cancel() {
            future.completeExceptionally(new CancellationException("Database task cancelled during shutdown"));
        }
    }
}
