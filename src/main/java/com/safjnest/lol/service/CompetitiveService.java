package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.model.statistics.Stats;
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

    private static final List<GameQueueType> QUEUES = List.of(
        GameQueueType.RANKED_SOLO_5X5,
        GameQueueType.RANKED_FLEX_SR
    );

    private CompetitiveService() {}

    public static void refreshFromRanks(String puuid, LeagueShard shard, Map<GameQueueType, Rank> ranks) {
        ProfileStatistics statistics = MongoDB.findProfileStatistics(puuid, Filter.canonical());
        refresh(puuid, shard, ranks, statistics);
    }

    public static void refreshFromStatistics(String puuid, LeagueShard shard, ProfileStatistics statistics) {
        refresh(puuid, shard, MongoDB.findRanks(puuid, shard), statistics);
    }

    public static MongoDB.CompetitiveRebuild rebuild() {
        long removed = MongoDB.clearCompetitive();
        long now = System.currentTimeMillis();
        long[] counts = new long[2];
        Filter filter = Filter.canonical();
        MongoDB.forEachCompetitiveSummonerBatch(summoners -> {
            List<String> puuids = new ArrayList<>(summoners.size());
            for (Summoner summoner : summoners) puuids.add(summoner.puuid());
            Map<String, ProfileStatistics> statisticsByPuuid = MongoDB.findProfileStatistics(puuids, filter);
            for (Summoner summoner : summoners) {
                counts[0]++;
                ProfileStatistics statistics = statisticsByPuuid.get(summoner.puuid());
                for (GameQueueType queue : QUEUES) {
                    CompetitiveEntry entry = entry(summoner.puuid(), summoner.region(), summoner.ranks(), statistics, queue, now);
                    if (entry != null) {
                        MongoDB.upsertCompetitive(entry);
                        counts[1]++;
                    }
                }
            }
        });
        return new MongoDB.CompetitiveRebuild(counts[0], counts[1], removed);
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

    private static void refresh(
        String puuid,
        LeagueShard shard,
        Map<GameQueueType, Rank> ranks,
        ProfileStatistics statistics
    ) {
        if (puuid == null || puuid.isBlank() || shard == null) return;
        long now = System.currentTimeMillis();
        for (GameQueueType queue : QUEUES) {
            CompetitiveEntry entry = entry(puuid, shard, ranks, statistics, queue, now);
            if (entry == null) MongoDB.deleteCompetitive(puuid, queue);
            else MongoDB.upsertCompetitive(entry);
        }
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
        for (GameQueueType queue : QUEUES) {
            Rank rank = ranks == null ? null : ranks.get(queue);
            if (rank != null && rank.tier() != null && TierDivisionUtils.getMmr(rank.tier(), rank.lp()) >= 0) return true;
        }
        return false;
    }

    private static LaneType primary(ProfileStatistics statistics, CanonicalQueue queue) {
        if (statistics == null || statistics.champions == null) return null;
        Map<LaneType, Stats<Void>> lanes = new LinkedHashMap<>();
        for (Map<CanonicalQueue, Map<String, Stats<Void>>> queues : statistics.champions.values()) {
            if (queues == null) continue;
            Map<String, Stats<Void>> values = queues.get(queue);
            if (values == null) continue;
            for (Map.Entry<String, Stats<Void>> entry : values.entrySet()) {
                LaneType lane;
                try { lane = LaneType.valueOf(entry.getKey()); }
                catch (RuntimeException ignored) { continue; }
                if (!LaneTypeUtils.playables().contains(lane) || entry.getValue() == null) continue;
                lanes.computeIfAbsent(lane, ignored -> new Stats<>()).merge(entry.getValue());
            }
        }

        LaneType result = null;
        long games = 0;
        for (LaneType lane : LaneTypeUtils.playables()) {
            Stats<Void> statisticsForLane = lanes.get(lane);
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
        for (Map.Entry<Integer, Map<CanonicalQueue, Map<String, Stats<Void>>>> champion : statistics.champions.entrySet()) {
            Map<CanonicalQueue, Map<String, Stats<Void>>> queues = champion.getValue();
            if (queues == null) continue;
            Map<String, Stats<Void>> lanes = queues.get(queue);
            if (lanes == null) continue;
            for (Stats<Void> values : lanes.values())
                if (values != null && Boolean.TRUE.equals(values.isOtp)) return champion.getKey();
        }
        return null;
    }
}
