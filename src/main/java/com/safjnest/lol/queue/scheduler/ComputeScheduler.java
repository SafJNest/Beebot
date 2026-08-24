package com.safjnest.lol.queue.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.job.JobPriority;
import com.safjnest.lol.service.ChampionService;
import com.safjnest.lol.service.ProfileService;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.SeasonUtils;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public final class ComputeScheduler extends AbstractScheduler<DatabaseWorkerType> {

    private static final ComputeScheduler INSTANCE = new ComputeScheduler();
    private static final ConcurrentMap<String, ChampionMatrixRequest> CHAMPION_MATRICES = new ConcurrentHashMap<>();
    private static final ProfileService PROFILE_SERVICE = new ProfileService();
    private static final ChampionService CHAMPION_SERVICE = new ChampionService();
    private static final CompletableFuture<Void> VOID = CompletableFuture.completedFuture(null);
    private static final CompletableFuture<Boolean> NOT_SCHEDULED = CompletableFuture.completedFuture(false);

    private ComputeScheduler() {
        super("compute", "Compute request cancelled during shutdown");
        registerRoutes(List.of(DatabaseWorkerType.PROFILE, DatabaseWorkerType.CHAMPION));
    }

    public static AbstractScheduler<DatabaseWorkerType> scheduler() {
        return INSTANCE;
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
        return startProfileStatistics(summoner, filter, rebuild, JobPriority.NORMAL);
    }

    public static CompletableFuture<Boolean> startStaleProfileStatistics(Summoner summoner, Filter filter) {
        return startProfileStatistics(summoner, filter, false, JobPriority.BACKGROUND);
    }

    public static CompletableFuture<Boolean> startProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileMatchups(puuid, shard, filter, JobPriority.NORMAL);
    }

    public static CompletableFuture<Boolean> startStaleProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileMatchups(puuid, shard, filter, JobPriority.BACKGROUND);
    }

    public static CompletableFuture<Boolean> startProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileActivity(puuid, shard, filter, JobPriority.NORMAL);
    }

    public static CompletableFuture<Boolean> startStaleProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter
    ) {
        return startProfileActivity(puuid, shard, filter, JobPriority.BACKGROUND);
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
        return submit(DatabaseWorkerType.PROFILE, JobPriority.IMMEDIATE, key, name,
            ignored -> refreshProfileAggregates(puuid, shard));
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
        return submit(DatabaseWorkerType.CHAMPION, JobPriority.NORMAL, key,
            "champion data refresh patch=" + patch, ignored -> {
                CHAMPION_SERVICE.refresh();
                return null;
            });
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
        INSTANCE.shutdownDispatcher();
    }

    public static com.safjnest.lol.model.status.SchedulerStatus status() {
        return INSTANCE.snapshot();
    }

    // ============================================================================

    @Override
    protected String routeName(DatabaseWorkerType route) {
        return route == DatabaseWorkerType.PROFILE ? "profile" : "champion";
    }

    @Override
    protected String workerThreadName(DatabaseWorkerType route) {
        return route == DatabaseWorkerType.PROFILE ? "lol-db-profile-worker-" : "lol-db-champion-worker-";
    }

    @Override
    protected DatabaseWorkerType routeForJob(Object route) {
        if (route instanceof DatabaseWorkerType workerType) return workerType;
        throw new IllegalArgumentException("Compute jobs require a DatabaseWorkerType");
    }

    @Override
    protected DatabaseWorkerType queueFor(DatabaseWorkerType route) {
        if (route != DatabaseWorkerType.PROFILE) return DatabaseWorkerType.CHAMPION;
        return profileQueue(load(DatabaseWorkerType.PROFILE), load(DatabaseWorkerType.CHAMPION), championReserved());
    }

    @Override
    protected void onBodyFailed(com.safjnest.lol.queue.job.Job<?> job, Throwable failure) {
        try {
            BotLogger.error("Database task failed for key=" + job.key()
                + " message=" + failure.getMessage());
        } catch (Throwable ignored) {
        }
    }

    private static CompletableFuture<Boolean> startProfileStatistics(
        Summoner summoner,
        Filter filter,
        boolean rebuild,
        JobPriority priority
    ) {
        if (summoner == null || summoner.puuid() == null || summoner.puuid().isBlank() || filter == null)
            return NOT_SCHEDULED;

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        String puuid = summoner.puuid();
        LeagueShard shard = summoner.region();
        String key = "profile-statistics:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile statistics " + (rebuild ? "rebuild " : "") + "puuid=" + puuid;
        return submit(DatabaseWorkerType.PROFILE, priority, key, name,
            ignored -> refreshProfileStatistics(puuid, shard, requestFilter, rebuild));
    }

    private static CompletableFuture<Boolean> startProfileMatchups(
        String puuid,
        LeagueShard shard,
        Filter filter,
        JobPriority priority
    ) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null)
            return NOT_SCHEDULED;

        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        String key = "profile-matchups:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile matchups puuid=" + puuid;
        return submit(DatabaseWorkerType.PROFILE, priority, key, name,
            ignored -> refreshProfileMatchups(puuid, shard, requestFilter));
    }

    private static CompletableFuture<Boolean> startProfileActivity(
        String puuid,
        LeagueShard shard,
        Filter filter,
        JobPriority priority
    ) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null)
            return NOT_SCHEDULED;
        Filter requestFilter = Filter.fromStateKey(filter.toStateKey());
        String key = "profile-activity:" + puuid + ":" + requestFilter.toSummonerKey();
        String name = "profile activity puuid=" + puuid;
        return submit(DatabaseWorkerType.PROFILE, priority, key, name,
            ignored -> refreshProfileActivity(puuid, shard, requestFilter));
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
            CompletableFuture<ChampionService.MatrixRefreshResult> future = submit(
                DatabaseWorkerType.CHAMPION, JobPriority.NORMAL, key, name, ignored -> {
                    request.start();
                    try {
                        return CHAMPION_SERVICE.refreshStatisticsMatrix(patch, queue, request.buildFilters());
                    } finally {
                        CHAMPION_MATRICES.remove(key, request);
                    }
                });
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

    static DatabaseWorkerType profileQueue(int profileLoad, int championLoad, boolean championReserved) {
        if (championReserved) return DatabaseWorkerType.PROFILE;
        return championLoad < profileLoad ? DatabaseWorkerType.CHAMPION : DatabaseWorkerType.PROFILE;
    }

    static boolean isHeavyChampionTaskKey(String key) {
        return key != null && (key.startsWith("champion-stats-matrix:")
            || key.startsWith("champion-build:")
            || key.startsWith("champion-data-refresh:"));
    }

    private static CompletableFuture<Boolean> startChampionBuild(Filter filter) {
        String key = "champion-build:" + filter.toKey();
        String name = "champion build champion=" + filter.champion()
            + " patch=" + filter.patch()
            + " queue=" + filter.queue()
            + " rank=" + filter.rank()
            + " region=" + filter.region()
            + " lane=" + filter.lane();
        return submit(DatabaseWorkerType.CHAMPION, JobPriority.NORMAL, key, name,
            ignored -> refreshChampionBuild(filter));
    }

    private boolean championReserved() {
        return hasIncompleteTask(DatabaseWorkerType.CHAMPION,
            task -> isHeavyChampionTaskKey(task.key()));
    }

    private static <T> CompletableFuture<T> submit(DatabaseWorkerType route, JobPriority priority,
        String key, String name, Function<Job<T>, T> work) {
        return switch (priority) {
            case IMMEDIATE -> QueueHandler.immediate(ComputeScheduler.class, route, key, name, work);
            case NORMAL -> QueueHandler.normal(ComputeScheduler.class, route, key, name, work);
            case BACKGROUND -> QueueHandler.background(ComputeScheduler.class, route, key, name, work);
        };
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
