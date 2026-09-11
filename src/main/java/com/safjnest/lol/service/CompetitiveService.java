package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public final class CompetitiveService {

    private CompetitiveService() {}

    public static boolean updateFromRanks(String puuid, LeagueShard shard, Map<GameQueueType, Rank> ranks) {
        return update(puuid, shard, ranks, MongoDB.findProfileStatistics(puuid, Filter.canonical()));
    }

    public static boolean updateFromStatistics(String puuid, LeagueShard shard, ProfileStatistics statistics) {
        return update(puuid, shard, MongoDB.findRanks(puuid, shard), statistics);
    }

    public static MongoDB.CompetitiveRebuild rebuild() {
        long now = System.currentTimeMillis();
        long[] counts = new long[3];
        Filter filter = Filter.canonical();
        MongoDB.forEachCompetitiveSummonerBatch(summoners -> {
            List<String> puuids = new ArrayList<>(summoners.size());
            for (Summoner summoner : summoners) puuids.add(summoner.puuid());
            Map<String, ProfileStatistics> statisticsByPuuid = MongoDB.findProfileStatistics(puuids, filter);
            Map<String, Map<GameQueueType, CompetitiveEntry>> existing = MongoDB.findCompetitive(puuids);
            for (Summoner summoner : summoners) {
                counts[0]++;
                ProfileStatistics statistics = statisticsByPuuid.get(summoner.puuid());
                Map<GameQueueType, CompetitiveEntry> current = existing.getOrDefault(summoner.puuid(), Map.of());
                for (GameQueueType queue : GameQueueTypeUtils.leaderboardQueues()) {
                    CompetitiveEntry previous = current.get(queue);
                    CompetitiveEntry next = entry(summoner.puuid(), summoner.region(), summoner.ranks(), statistics, queue, now);
                    if (previous == null && next == null) continue;
                    if (!write(previous, next, false)) continue;
                    if (next == null) counts[2]++;
                    else counts[1]++;
                }
            }
        });
        return new MongoDB.CompetitiveRebuild(counts[0], counts[1], counts[2]);
    }

    public static StatisticsBuild buildMissingStatistics() {
        long[] counts = new long[3];
        Filter filter = Filter.canonical();
        MongoDB.forEachCompetitiveSummonerBatch(summoners -> {
            List<String> puuids = new ArrayList<>(summoners.size());
            for (Summoner summoner : summoners) puuids.add(summoner.puuid());
            Map<String, ProfileStatistics> statisticsByPuuid = MongoDB.findProfileStatistics(puuids, filter);
            for (Summoner summoner : summoners) {
                if (!hasRank(summoner.ranks())) continue;
                counts[0]++;
                if (statisticsByPuuid.containsKey(summoner.puuid())) continue;
                counts[1]++;
                try {
                    if (ComputeScheduler.startStaleProfileStatistics(summoner, filter).join()) counts[2]++;
                } catch (RuntimeException ignored) {
                }
            }
        });
        return new StatisticsBuild(counts[0], counts[1], counts[2]);
    }

    public record StatisticsBuild(long ranked, long scheduled, long completed) {}

    // ============================================================================

    private static boolean update(
        String puuid,
        LeagueShard shard,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics
    ) {
        if (puuid == null || puuid.isBlank() || shard == null) return false;
        long now = System.currentTimeMillis();
        for (GameQueueType queue : GameQueueTypeUtils.leaderboardQueues()) {
            CompetitiveEntry previous = MongoDB.findCompetitive(puuid, queue);
            CompetitiveEntry next = entry(puuid, shard, ranks, statistics, queue, now);
            if (!write(previous, next, true)) return false;
        }
        return true;
    }

    static CompetitiveEntry entry(
        String puuid,
        LeagueShard shard,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics,
        GameQueueType queue,
        long now
    ) {
        GameQueueType canonicalQueue = GameQueueTypeUtils.canonicalQueue(queue);
        Rank rank = ranks == null ? null : ranks.get(canonicalQueue);
        LaneType primary = primary(statistics, CanonicalQueue.from(canonicalQueue));
        Integer otpChampionId = otpChampion(statistics, CanonicalQueue.from(canonicalQueue));
        if (rank == null || rank.tier() == null) return null;
        long mmr = TierDivisionUtils.getMmr(rank.tier(), rank.lp());
        if (mmr < 0) return null;
        return new CompetitiveEntry(puuid, shard, canonicalQueue, mmr, primary, otpChampionId, now);
    }

    private static boolean hasRank(Map<GameQueueType, Rank> ranks) {
        for (GameQueueType queue : GameQueueTypeUtils.leaderboardQueues()) {
            Rank rank = ranks == null ? null : ranks.get(queue);
            if (rank != null && rank.tier() != null && TierDivisionUtils.getMmr(rank.tier(), rank.lp()) >= 0) return true;
        }
        return false;
    }

    private static LaneType primary(ProfileStatistics statistics, CanonicalQueue queue) {
        if (statistics == null || statistics.champions == null) return null;
        Map<LaneType, ProfileLeafStats> lanes = new LinkedHashMap<>();
        for (Map<CanonicalQueue, Map<String, ProfileLeafStats>> queues : statistics.champions.values()) {
            if (queues == null) continue;
            Map<String, ProfileLeafStats> values = queues.get(queue);
            if (values == null) continue;
            for (Map.Entry<String, ProfileLeafStats> entry : values.entrySet()) {
                LaneType lane;
                try { lane = LaneType.valueOf(entry.getKey()); }
                catch (RuntimeException ignored) { continue; }
                if (!LaneTypeUtils.playables().contains(lane) || entry.getValue() == null) continue;
                lanes.computeIfAbsent(lane, ignored -> new ProfileLeafStats()).merge(entry.getValue());
            }
        }

        LaneType result = null;
        long games = 0;
        for (LaneType lane : LaneTypeUtils.playables()) {
            ProfileLeafStats statisticsForLane = lanes.get(lane);
            long current = statisticsForLane == null ? 0 : statisticsForLane.games;
            if (current > games) {
                result = lane;
                games = current;
            }
        }
        return games == 0 ? null : result;
    }

    private static Integer otpChampion(ProfileStatistics statistics, CanonicalQueue queue) {
        if (statistics == null || statistics.champions == null) return null;
        for (Map.Entry<Integer, Map<CanonicalQueue, Map<String, ProfileLeafStats>>> champion : statistics.champions.entrySet()) {
            Map<CanonicalQueue, Map<String, ProfileLeafStats>> queues = champion.getValue();
            if (queues == null) continue;
            Map<String, ProfileLeafStats> lanes = queues.get(queue);
            if (lanes == null) continue;
            for (ProfileLeafStats values : lanes.values())
                if (values != null && Boolean.TRUE.equals(values.isOtp)) return champion.getKey();
        }
        return null;
    }

    private static boolean write(CompetitiveEntry previous, CompetitiveEntry next, boolean updateIndex) {
        CompetitiveEntry merged = merge(previous, next);
        boolean saved;
        if (merged == null) {
            if (previous == null) return true;
            saved = MongoDB.deleteCompetitive(previous.puuid(), previous.queue());
        } else saved = MongoDB.upsertCompetitive(merged);
        if (!saved) return false;
        if (updateIndex) LeaderboardService.updateIndex(previous, merged);
        return true;
    }

    static CompetitiveEntry merge(CompetitiveEntry previous, CompetitiveEntry next) {
        if (next == null) return null;
        LaneType primary = next.primary() == null && previous != null ? previous.primary() : next.primary();
        Integer otpChampionId = next.otpChampionId() == null && previous != null ? previous.otpChampionId() : next.otpChampionId();
        return new CompetitiveEntry(next.puuid(), next.region(), next.queue(), next.mmr(), primary, otpChampionId, next.lastUpdate());
    }
}
