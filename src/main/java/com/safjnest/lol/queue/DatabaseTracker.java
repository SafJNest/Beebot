package com.safjnest.lol.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.status.QueueWorkerStatus;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.ChampionService;
import com.safjnest.lol.service.ProfileService;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class DatabaseTracker extends AbstractQueueScheduler<DatabaseWorkerType> {

    private static final DatabaseTracker INSTANCE = new DatabaseTracker();
    private static final ConcurrentMap<String, ChampionMatrixRequest> CHAMPION_MATRICES = new ConcurrentHashMap<>();
    private static final ProfileService PROFILE_SERVICE = new ProfileService();
    private static final ChampionService CHAMPION_SERVICE = new ChampionService();
    private static final CompletableFuture<Void> VOID = CompletableFuture.completedFuture(null);
    private static final CompletableFuture<Boolean> NOT_SCHEDULED = CompletableFuture.completedFuture(false);

    private DatabaseTracker() {
        super("Database task cancelled during shutdown");
        registerRoutes(List.of(DatabaseWorkerType.PROFILE, DatabaseWorkerType.CHAMPION));
    }

    public static <T> CompletableFuture<T> schedule(QueueRequest<DatabaseWorkerType, T> request) {
        return INSTANCE.enqueue(request);
    }

    public static CompletableFuture<Boolean> startProfileStatistics(
        Summoner summoner,
        SeasonUtils.SeasonRange season
    ) {
        if (season == null) return NOT_SCHEDULED;
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
        return startProfileStatistics(summoner, filter, rebuild, QueuePriority.NORMAL);
    }

    public static CompletableFuture<Boolean> startStaleProfileStatistics(Summoner summoner, Filter filter) {
        return startProfileStatistics(summoner, filter, false, QueuePriority.BACKGROUND);
    }

    public static CompletableFuture<Boolean> startProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileMatchups(puuid, shard, filter, QueuePriority.NORMAL);
    }

    public static CompletableFuture<Boolean> startStaleProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileMatchups(puuid, shard, filter, QueuePriority.BACKGROUND);
    }

    public static CompletableFuture<Boolean> startProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileActivity(puuid, shard, filter, QueuePriority.NORMAL);
    }

    public static CompletableFuture<Boolean> startStaleProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileActivity(puuid, shard, filter, QueuePriority.BACKGROUND);
    }

    public static CompletableFuture<Boolean> startProfileRefresh(
        Summoner summoner,
        LeagueShard shard
    ) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || shard == null) {
            return NOT_SCHEDULED;
        }
        String puuid = summoner.puuid();
        String key = profileRefreshKey(puuid);
        String name = "profile refresh puuid=" + puuid;
        return schedule(new QueueRequest<>(
            key,
            name,
            DatabaseWorkerType.PROFILE,
            QueuePriority.IMMEDIATE,
            () -> refreshProfileAggregates(puuid, shard)
        ));
    }

    public static CompletableFuture<Void> startChampionData(
        Filter filter,
        boolean statsMissing,
        boolean buildMissing
    ) {
        if (filter == null || filter.champion() == 0) return VOID;
        if (!statsMissing && !buildMissing) return VOID;

        Filter snapshot = Filter.fromStateKey(filter.toStateKey());
        if (statsMissing) return startChampionStatistics(snapshot, buildMissing);
        return startChampionBuild(snapshot).thenApply(ignored -> null);
    }

    private static CompletableFuture<Void> startChampionStatistics(Filter filter, boolean includeBuild) {
        List<String> patches = PatchUtils.getRecentPatches(3);
        Filter buildFilter = includeBuild ? filter : null;
        if (!patches.contains(filter.patch())) {
            return enqueueChampionStatsMatrix(filter.patch(), filter.queue(), buildFilter).thenApply(ignored -> null);
        }

        List<CompletableFuture<ChampionService.MatrixRefreshResult>> refreshes = new ArrayList<>();
        for (String patch : patches) {
            Filter patchBuild = patch.equals(filter.patch()) ? buildFilter : null;
            refreshes.add(enqueueChampionStatsMatrix(patch, filter.queue(), patchBuild));
        }
        return CompletableFuture.allOf(refreshes.toArray(CompletableFuture[]::new));
    }

    public static CompletableFuture<Void> enqueueChampionDataRefresh() {
        String patch = new Filter().patch();
        String key = "champion-data-refresh:" + patch;
        return schedule(new QueueRequest<>(key, "champion data refresh patch=" + patch, DatabaseWorkerType.CHAMPION,
            QueuePriority.NORMAL, () -> {
                CHAMPION_SERVICE.refresh();
                return null;
            }));
    }

    public static CompletableFuture<ChampionService.MatrixRefreshResult> enqueueChampionStatsMatrix(
        String patch,
        GameQueueType queue
    ) {
        return enqueueChampionStatsMatrix(patch, queue, null);
    }

    public static CompletableFuture<Void> enqueueRecentChampionStatsMatrices(GameQueueType queue) {
        if (queue == null) return VOID;
        List<String> patches = PatchUtils.getRecentPatches(3);
        if (patches.isEmpty()) return VOID;
        List<CompletableFuture<ChampionService.MatrixRefreshResult>> futures = new ArrayList<>();
        for (String patch : patches) futures.add(enqueueChampionStatsMatrix(patch, queue));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public static void shutdown() {
        INSTANCE.shutdownScheduler();
    }

    public static List<QueueWorkerStatus> workerStatuses() {
        return INSTANCE.schedulerWorkerStatuses();
    }

    public static int profileQueueSize() {
        return INSTANCE.incompleteCount(DatabaseWorkerType.PROFILE);
    }

    // ============================================================================

    @Override
    protected String channelName(DatabaseWorkerType route) {
        return route == DatabaseWorkerType.PROFILE ? "profile" : "champion";
    }

    @Override
    protected String workerThreadName(DatabaseWorkerType route) {
        return route == DatabaseWorkerType.PROFILE ? "lol-db-profile-worker-" : "lol-db-champion-worker-";
    }

    @Override
    protected <T> DatabaseWorkerType queueFor(QueueRequest<DatabaseWorkerType, T> request) {
        if (request.route() != DatabaseWorkerType.PROFILE) return DatabaseWorkerType.CHAMPION;
        return load(DatabaseWorkerType.CHAMPION) < load(DatabaseWorkerType.PROFILE)
            ? DatabaseWorkerType.CHAMPION
            : DatabaseWorkerType.PROFILE;
    }

    @Override
    protected boolean promoteOnReuse(DatabaseWorkerType route) {
        return route == DatabaseWorkerType.PROFILE;
    }

    @Override
    protected void onFailed(QueueTask<DatabaseWorkerType, ?> task, Throwable failure) {
        try {
            BotLogger.error("Database task failed for key=" + task.key()
                + " message=" + failure.getMessage());
        } catch (Throwable ignored) {
        }
    }

    private static CompletableFuture<Boolean> startProfileStatistics(
        Summoner summoner,
        Filter filter,
        boolean rebuild,
        QueuePriority priority
    ) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || filter == null)
            return NOT_SCHEDULED;

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        String puuid = summoner.puuid();
        LeagueShard shard = summoner.region();
        String key = "profile-statistics:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile statistics " + (rebuild ? "rebuild " : "") + "puuid=" + puuid;
        return schedule(new QueueRequest<>(
            key,
            name,
            DatabaseWorkerType.PROFILE,
            priority,
            () -> refreshProfileStatistics(puuid, shard, requestFilter, rebuild)
        ));
    }

    private static CompletableFuture<Boolean> startProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter,
        QueuePriority priority
    ) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null)
            return NOT_SCHEDULED;

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        String key = "profile-matchups:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile matchups puuid=" + puuid;
        return schedule(new QueueRequest<>(
            key,
            name,
            DatabaseWorkerType.PROFILE,
            priority,
            () -> refreshProfileMatchups(puuid, shard, requestFilter)
        ));
    }

    private static CompletableFuture<Boolean> startProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter,
        QueuePriority priority
    ) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null)
            return NOT_SCHEDULED;
        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        String key = "profile-activity:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile activity puuid=" + puuid;
        return schedule(new QueueRequest<>(
            key,
            name,
            DatabaseWorkerType.PROFILE,
            priority,
            () -> refreshProfileActivity(puuid, shard, requestFilter)
        ));
    }

    private static CompletableFuture<ChampionService.MatrixRefreshResult> enqueueChampionStatsMatrix(
        String patch,
        GameQueueType queue,
        Filter buildFilter
    ) {
        if (patch == null || patch.isBlank() || queue == null)
            return CompletableFuture.completedFuture(new ChampionService.MatrixRefreshResult(0, 0, 0, 0, 0));
        String key = championStatsMatrixKey(patch, queue);
        String name = "champion stats matrix patch=" + patch + " queue=" + queue.name();
        synchronized (INSTANCE.lifecycleLock()) {
            ChampionMatrixRequest existing = CHAMPION_MATRICES.get(key);
            if (existing != null) {
                if (!existing.running()) existing.addBuild(buildFilter);
                else if (buildFilter != null) startChampionBuild(buildFilter);
                return existing.future();
            }
            ChampionMatrixRequest request = new ChampionMatrixRequest();
            request.addBuild(buildFilter);
            CHAMPION_MATRICES.put(key, request);
            CompletableFuture<ChampionService.MatrixRefreshResult> future = schedule(new QueueRequest<>(
                key,
                name,
                DatabaseWorkerType.CHAMPION,
                QueuePriority.NORMAL,
                () -> {
                    request.start();
                    try {
                        return CHAMPION_SERVICE.refreshStatisticsMatrix(patch, queue, request.buildFilters());
                    } finally {
                        CHAMPION_MATRICES.remove(key, request);
                    }
                }
            ));
            request.setFuture(future);
            return future;
        }
    }

    public static String championStatsMatrixKey(String patch, GameQueueType queue) {
        return "champion-stats-matrix:" + patch + ":" + queue.name();
    }

    public static String profileRefreshKey(String puuid) {
        return "profile-refresh:" + puuid;
    }

    private static CompletableFuture<Boolean> startChampionBuild(Filter filter) {
        String key = "champion-build:" + filter.toKey();
        String name = "champion build champion=" + filter.champion()
            + " patch=" + filter.patch()
            + " queue=" + filter.queue()
            + " rank=" + filter.rank()
            + " region=" + filter.region()
            + " lane=" + filter.lane();
        return schedule(new QueueRequest<>(
            key,
            name,
            DatabaseWorkerType.CHAMPION,
            QueuePriority.NORMAL,
            () -> refreshChampionBuild(filter)
        ));
    }

    private static boolean refreshProfileStatistics(
        String puuid,
        LeagueShard shard,
        Filter filter,
        boolean rebuild
    ) {
        try {
            if (!PROFILE_SERVICE.refreshStatistics(puuid, shard, filter, rebuild)) {
                BotLogger.error("Profile statistics refresh failed for summoner=" + puuid);
                return false;
            }

            ProfileService.invalidate(puuid, shard);
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile statistics refresh failed for summoner=" + puuid
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshProfileMatchups(String puuid, LeagueShard shard, Filter filter) {
        try {
            if (!PROFILE_SERVICE.refreshMatchups(puuid, shard, filter)) {
                BotLogger.error("Profile matchups refresh failed for puuid=" + puuid);
                return false;
            }
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile matchups refresh failed for puuid=" + puuid
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshProfileActivity(String puuid, LeagueShard shard, Filter filter) {
        try {
            if (!PROFILE_SERVICE.refreshActivity(shard, puuid, filter)) {
                BotLogger.error("Profile activity refresh failed for puuid=" + puuid);
                return false;
            }
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile activity refresh failed for puuid=" + puuid
                + " message=" + exception.getMessage());
            throw new IllegalStateException(exception);
        }
    }

    private static boolean refreshProfileAggregates(String puuid, LeagueShard shard) {
        try {
            if (!PROFILE_SERVICE.refreshCanonicalAggregates(shard, puuid)) {
                BotLogger.error("Profile aggregate refresh failed for puuid=" + puuid);
                return false;
            }
            ProfileService.invalidate(puuid, shard);
            return true;
        } catch (Exception exception) {
            BotLogger.error("Profile aggregate refresh failed for puuid=" + puuid
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

}
