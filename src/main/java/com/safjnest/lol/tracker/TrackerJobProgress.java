package com.safjnest.lol.tracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.model.status.JobProgress;
import com.safjnest.lol.model.status.TrackerMetrics;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public final class TrackerJobProgress {

    private static final AtomicInteger TRACKING_CURRENT = new AtomicInteger();
    private static final AtomicInteger TRACKING_TOTAL = new AtomicInteger();
    private static final AtomicReference<String> TRACKING_STATE = new AtomicReference<>("idle");
    private static final List<TrackingSummonerProgress> TRACKING_SUMMONERS = new ArrayList<>();
    private static final Map<String, Integer> TRACKING_SUMMONERS_INDEX = new HashMap<>();
    private static final Object TRACKING_SUMMONERS_LOCK = new Object();

    private static final AtomicInteger HIGH_ELO_CURRENT = new AtomicInteger();
    private static final AtomicInteger HIGH_ELO_TOTAL = new AtomicInteger();
    private static final AtomicReference<String> HIGH_ELO_STATE = new AtomicReference<>("idle");
    private static final AtomicReference<String> HIGH_ELO_TIER = new AtomicReference<>();
    private static final AtomicReference<String> HIGH_ELO_SHARD = new AtomicReference<>();
    private static final AtomicReference<String> HIGH_ELO_QUEUE = new AtomicReference<>();
    private static final Map<String, HighEloStepProgress> HIGH_ELO_STEPS = new LinkedHashMap<>();
    private static final Object HIGH_ELO_STEPS_LOCK = new Object();

    private static final AtomicInteger GAME_ANALYSIS_CURRENT = new AtomicInteger();
    private static final AtomicInteger GAME_ANALYSIS_TOTAL = new AtomicInteger();
    private static final AtomicReference<String> GAME_ANALYSIS_STATE = new AtomicReference<>("idle");

    private static final AtomicReference<String> SAMPLE_GAMES_STATE = new AtomicReference<>("idle");
    private static final AtomicReference<String> SAMPLE_GAMES_QUEUE = new AtomicReference<>();
    private static final AtomicInteger SAMPLE_ACTIVE_SHARDS = new AtomicInteger();
    private static final ConcurrentHashMap<String, SampleRegionProgress> SAMPLE_REGIONS = new ConcurrentHashMap<>();

    private TrackerJobProgress() {}

    public static void beginTracking(List<Summoner> accounts) {
        TRACKING_STATE.set("running");
        int total;
        synchronized (TRACKING_SUMMONERS_LOCK) {
            TRACKING_SUMMONERS.clear();
            TRACKING_SUMMONERS_INDEX.clear();
            if (accounts != null) {
                for (Summoner account : accounts) {
                    if (account == null || account.puuid() == null) continue;
                    String shard = account.region() == null ? null : account.region().name();
                    int index = TRACKING_SUMMONERS.size();
                    TRACKING_SUMMONERS.add(new TrackingSummonerProgress(account.puuid(), account.riotId(), shard, false));
                    TRACKING_SUMMONERS_INDEX.put(account.puuid(), index);
                }
            }
            total = TRACKING_SUMMONERS.size();
        }
        TRACKING_TOTAL.set(total);
        TRACKING_CURRENT.set(0);
    }

    public static void completeTrackingSummoner(String puuid) {
        synchronized (TRACKING_SUMMONERS_LOCK) {
            Integer index = TRACKING_SUMMONERS_INDEX.get(puuid);
            if (index != null && index >= 0 && index < TRACKING_SUMMONERS.size()) {
                TrackingSummonerProgress current = TRACKING_SUMMONERS.get(index);
                TRACKING_SUMMONERS.set(index, current.withDone(true));
            }
        }
        TRACKING_CURRENT.incrementAndGet();
    }

    public static void endTracking() {
        TRACKING_STATE.set("idle");
        synchronized (TRACKING_SUMMONERS_LOCK) {
            TRACKING_SUMMONERS.clear();
            TRACKING_SUMMONERS_INDEX.clear();
        }
    }

    public static void beginHighElo(
        List<TierDivisionType> tiers,
        List<LeagueShard> shards,
        List<GameQueueType> queues
    ) {
        HIGH_ELO_STATE.set("running");
        synchronized (HIGH_ELO_STEPS_LOCK) {
            HIGH_ELO_STEPS.clear();
            if (tiers != null && shards != null && queues != null) {
                for (TierDivisionType tier : tiers) {
                    for (LeagueShard shard : shards) {
                        for (GameQueueType queue : queues) {
                            if (tier == null || shard == null || queue == null) continue;
                            HighEloStepProgress step = new HighEloStepProgress(tier.name(), shard.name(), queue.name(), false);
                            HIGH_ELO_STEPS.put(step.key(), step);
                        }
                    }
                }
            }
        }
        HIGH_ELO_TOTAL.set(HIGH_ELO_STEPS.size());
        HIGH_ELO_CURRENT.set(0);
        HIGH_ELO_TIER.set(null);
        HIGH_ELO_SHARD.set(null);
        HIGH_ELO_QUEUE.set(null);
    }

    public static void startHighEloStep(String tier, LeagueShard shard, GameQueueType queue) {
        HIGH_ELO_TIER.set(tier);
        HIGH_ELO_SHARD.set(shard == null ? null : shard.name());
        HIGH_ELO_QUEUE.set(queue == null ? null : queue.name());
    }

    public static void completeHighEloStep(String tier, LeagueShard shard, GameQueueType queue) {
        if (tier != null && shard != null && queue != null) {
            String key = HighEloStepProgress.key(tier, shard.name(), queue.name());
            synchronized (HIGH_ELO_STEPS_LOCK) {
                HIGH_ELO_STEPS.computeIfPresent(key, (ignored, value) -> value.withDone(true));
            }
        }
        HIGH_ELO_CURRENT.incrementAndGet();
    }

    public static void endHighElo() {
        HIGH_ELO_STATE.set("idle");
        HIGH_ELO_TIER.set(null);
        HIGH_ELO_SHARD.set(null);
        HIGH_ELO_QUEUE.set(null);
        HIGH_ELO_CURRENT.set(0);
        HIGH_ELO_TOTAL.set(0);
        synchronized (HIGH_ELO_STEPS_LOCK) {
            HIGH_ELO_STEPS.clear();
        }
    }

    public static void beginGameAnalysis(int total) {
        GAME_ANALYSIS_STATE.set("running");
        GAME_ANALYSIS_TOTAL.set(Math.max(0, total));
        GAME_ANALYSIS_CURRENT.set(0);
    }

    public static void advanceGameAnalysis() {
        GAME_ANALYSIS_CURRENT.incrementAndGet();
    }

    public static void endGameAnalysis() {
        GAME_ANALYSIS_STATE.set("idle");
    }

    public static void beginSampleGames(GameQueueType queue, List<LeagueShard> shards) {
        SAMPLE_GAMES_STATE.set("running");
        SAMPLE_GAMES_QUEUE.set(queue == null ? null : queue.name());
        SAMPLE_REGIONS.clear();
        SAMPLE_ACTIVE_SHARDS.set(shards == null ? 0 : shards.size());
        if (shards == null) return;
        for (LeagueShard shard : shards) {
            if (shard == null) continue;
            SAMPLE_REGIONS.put(shard.name(), new SampleRegionProgress(shard.name(), "pending", 0, 0));
        }
    }

    public static void sampleRegionLoading(LeagueShard shard) {
        if (shard == null) return;
        SAMPLE_REGIONS.compute(shard.name(), (key, value) -> {
            SampleRegionProgress region = value == null ? new SampleRegionProgress(key, "loading", 0, 0) : value;
            return region.withState("loading");
        });
    }

    public static void sampleRegionTotal(LeagueShard shard, int total) {
        if (shard == null) return;
        SAMPLE_REGIONS.compute(shard.name(), (key, value) -> {
            SampleRegionProgress region = value == null ? new SampleRegionProgress(key, "analyzing", total, 0) : value;
            return region.withTotal(total);
        });
    }

    public static void sampleRegionAnalyzed(LeagueShard shard, int analyzed) {
        if (shard == null) return;
        SAMPLE_REGIONS.compute(shard.name(), (key, value) -> {
            if (value == null) return new SampleRegionProgress(key, "analyzing", analyzed, analyzed);
            return value.withAnalyzed(analyzed);
        });
    }

    public static void sampleRegionDone(LeagueShard shard) {
        if (shard == null) return;
        SAMPLE_REGIONS.computeIfPresent(shard.name(), (key, value) -> value.withState("done"));
        if (SAMPLE_ACTIVE_SHARDS.decrementAndGet() <= 0) endSampleGames();
    }

    public static void endSampleGames() {
        SAMPLE_GAMES_STATE.set("idle");
        SAMPLE_GAMES_QUEUE.set(null);
        SAMPLE_REGIONS.clear();
        SAMPLE_ACTIVE_SHARDS.set(0);
    }

    public static TrackerMetrics.TrackingJob trackingSnapshot(
        boolean scheduled,
        boolean schedulerRunning,
        long nextRunAt,
        long totalSummoners
    ) {
        String state = jobState(scheduled, schedulerRunning, TRACKING_STATE.get());
        JobProgress progress = progress(TRACKING_STATE.get(), TRACKING_CURRENT.get(), TRACKING_TOTAL.get());
        List<TrackerMetrics.TrackingSummoner> summoners = trackingSummonersSnapshot();
        return new TrackerMetrics.TrackingJob(
            state,
            nextRunAt > 0 ? nextRunAt : null,
            totalSummoners > 0 ? totalSummoners : null,
            progress,
            summoners
        );
    }

    public static TrackerMetrics.HighEloJob highEloSnapshot(boolean scheduled, boolean schedulerRunning, long nextRunAt) {
        String state = jobState(scheduled, schedulerRunning, HIGH_ELO_STATE.get());
        JobProgress progress = progress(HIGH_ELO_STATE.get(), HIGH_ELO_CURRENT.get(), HIGH_ELO_TOTAL.get());
        List<TrackerMetrics.HighEloStep> steps = highEloStepsSnapshot();
        return new TrackerMetrics.HighEloJob(
            state,
            nextRunAt > 0 ? nextRunAt : null,
            HIGH_ELO_TIER.get(),
            HIGH_ELO_SHARD.get(),
            HIGH_ELO_QUEUE.get(),
            progress,
            steps
        );
    }

    public static TrackerMetrics.GameAnalysisJob gameAnalysisSnapshot(boolean scheduled, boolean schedulerRunning, long nextRunAt) {
        String state = jobState(scheduled, schedulerRunning, GAME_ANALYSIS_STATE.get());
        JobProgress progress = progress(GAME_ANALYSIS_STATE.get(), GAME_ANALYSIS_CURRENT.get(), GAME_ANALYSIS_TOTAL.get());
        return new TrackerMetrics.GameAnalysisJob(
            state,
            nextRunAt > 0 ? nextRunAt : null,
            progress
        );
    }

    public static TrackerMetrics.SampleGamesJob sampleGamesSnapshot(Long nextRunAt) {
        String state = SAMPLE_GAMES_STATE.get();
        List<TrackerMetrics.SampleGamesRegion> regions = new ArrayList<>();
        for (SampleRegionProgress region : SAMPLE_REGIONS.values()) regions.add(region.toRegion());
        regions.sort((left, right) -> left.shard().compareTo(right.shard()));
        return new TrackerMetrics.SampleGamesJob(
            state,
            nextRunAt,
            SAMPLE_GAMES_QUEUE.get(),
            List.copyOf(regions)
        );
    }

    // ============================================================================

    private static String jobState(boolean scheduled, boolean schedulerRunning, String progressState) {
        if (!scheduled) return "stopped";
        if (schedulerRunning || "running".equals(progressState)) return "running";
        return "idle";
    }

    private static JobProgress progress(String state, int current, int total) {
        if (!"running".equals(state) || total <= 0) return null;
        return new JobProgress(Math.min(current, total), total);
    }

    private static List<TrackerMetrics.TrackingSummoner> trackingSummonersSnapshot() {
        if (!"running".equals(TRACKING_STATE.get())) return null;
        synchronized (TRACKING_SUMMONERS_LOCK) {
            if (TRACKING_SUMMONERS.isEmpty()) return null;
            List<TrackerMetrics.TrackingSummoner> summoners = new ArrayList<>(TRACKING_SUMMONERS.size());
            for (TrackingSummonerProgress summoner : TRACKING_SUMMONERS) summoners.add(summoner.toSummoner());
            return List.copyOf(summoners);
        }
    }

    private static List<TrackerMetrics.HighEloStep> highEloStepsSnapshot() {
        if (!"running".equals(HIGH_ELO_STATE.get())) return null;
        synchronized (HIGH_ELO_STEPS_LOCK) {
            if (HIGH_ELO_STEPS.isEmpty()) return null;
            List<TrackerMetrics.HighEloStep> steps = new ArrayList<>(HIGH_ELO_STEPS.size());
            for (HighEloStepProgress step : HIGH_ELO_STEPS.values()) steps.add(step.toStep());
            return List.copyOf(steps);
        }
    }

    private static final class HighEloStepProgress {
        private final String tier;
        private final String shard;
        private final String queue;
        private final boolean done;

        private HighEloStepProgress(String tier, String shard, String queue, boolean done) {
            this.tier = tier;
            this.shard = shard;
            this.queue = queue;
            this.done = done;
        }

        private static String key(String tier, String shard, String queue) {
            return tier + ":" + shard + ":" + queue;
        }

        private String key() {
            return key(tier, shard, queue);
        }

        private HighEloStepProgress withDone(boolean nextDone) {
            return new HighEloStepProgress(tier, shard, queue, nextDone);
        }

        private TrackerMetrics.HighEloStep toStep() {
            return new TrackerMetrics.HighEloStep(tier, shard, queue, done);
        }
    }

    private static final class TrackingSummonerProgress {
        private final String puuid;
        private final String riotId;
        private final String shard;
        private final boolean done;

        private TrackingSummonerProgress(String puuid, String riotId, String shard, boolean done) {
            this.puuid = puuid;
            this.riotId = riotId;
            this.shard = shard;
            this.done = done;
        }

        private TrackingSummonerProgress withDone(boolean nextDone) {
            return new TrackingSummonerProgress(puuid, riotId, shard, nextDone);
        }

        private TrackerMetrics.TrackingSummoner toSummoner() {
            return new TrackerMetrics.TrackingSummoner(puuid, riotId, shard, done);
        }
    }

    private static final class SampleRegionProgress {
        private final String shard;
        private final String state;
        private final int total;
        private final int analyzed;

        private SampleRegionProgress(String shard, String state, int total, int analyzed) {
            this.shard = shard;
            this.state = state;
            this.total = total;
            this.analyzed = analyzed;
        }

        private SampleRegionProgress withState(String nextState) {
            return new SampleRegionProgress(shard, nextState, total, analyzed);
        }

        private SampleRegionProgress withTotal(int nextTotal) {
            return new SampleRegionProgress(shard, "analyzing", Math.max(0, nextTotal), analyzed);
        }

        private SampleRegionProgress withAnalyzed(int nextAnalyzed) {
            int bounded = Math.max(0, nextAnalyzed);
            return new SampleRegionProgress(shard, "analyzing", Math.max(total, bounded), bounded);
        }

        private TrackerMetrics.SampleGamesRegion toRegion() {
            long remaining = Math.max(0, total - analyzed);
            return new TrackerMetrics.SampleGamesRegion(shard, state, total, analyzed, remaining);
        }
    }
}
