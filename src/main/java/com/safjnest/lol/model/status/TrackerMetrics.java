package com.safjnest.lol.model.status;

import java.util.List;

public record TrackerMetrics(
    String scheduler,
    TrackingJob tracking,
    HighEloJob highElo,
    GameAnalysisJob gameAnalysis,
    SampleGamesJob sampleGames,
    GamesSummary games
) {

    public record TrackingJob(
        String state,
        Long nextRunAt,
        Long totalSummoners,
        JobProgress progress,
        List<TrackingSummoner> summoners
    ) {
    }

    public record TrackingSummoner(
        String puuid,
        String riotId,
        String shard,
        boolean done
    ) {
    }

    public record HighEloJob(
        String state,
        Long nextRunAt,
        String tier,
        String shard,
        String queue,
        JobProgress progress,
        List<HighEloStep> steps
    ) {
    }

    public record HighEloStep(
        String tier,
        String shard,
        String queue,
        boolean done
    ) {
    }

    public record GameAnalysisJob(
        String state,
        Long nextRunAt,
        JobProgress progress
    ) {
    }

    public record SampleGamesJob(
        String state,
        Long nextRunAt,
        String queue,
        List<SampleGamesRegion> regions
    ) {
    }

    public record SampleGamesRegion(
        String shard,
        String state,
        long total,
        long analyzed,
        long remaining
    ) {
    }

    public record GamesSummary(
        long pendingGames,
        int matchLookups,
        Long nextMatchLookupAt
    ) {
    }
}
