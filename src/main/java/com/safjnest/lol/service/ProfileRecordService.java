package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.record.ProfileRecord;
import com.safjnest.lol.model.record.ProfileRecordPage;
import com.safjnest.lol.model.record.RecordMetric;
import com.safjnest.lol.model.summoner.Mastery;
import com.safjnest.lol.model.record.RecordPage;
import com.safjnest.lol.model.record.RecordsOverview;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class ProfileRecordService {

    public ApiResult<ProfileRecordPage> get(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return ApiResult.notFound();
        List<ProfileRecord> records = MongoDB.findProfileRecords(puuid, filter);
        if (!records.isEmpty()) {
            long lastUpdate = lastUpdate(records);
            ProfileRecordPage page = ProfileRecordPage.of(records, lastUpdate, ResponseMetadata.ready(lastUpdate, filter));
            return ApiResult.ready(page, page.metadata());
        }
        ComputeScheduler.startProfileRecords(puuid, shard, filter, false);
        return ApiResult.pending(ResponseMetadata.pending(null, filter));
    }

    public RecordsOverview getGlobalOverview(Filter filter, LeagueShard region) {
        List<ProfileRecord> records = new ArrayList<>();
        for (RecordMetric metric : RecordMetric.values()) {
            records.addAll(MongoDB.findGlobalProfileRecords(filter, metric, region, 5, 0));
        }
        enrich(records);
        long lastUpdate = lastUpdate(records);
        return RecordsOverview.of(records, ResponseMetadata.ready(lastUpdate, filter));
    }

    public RecordPage getGlobalPage(Filter filter, RecordMetric metric, LeagueShard region, int limit, int offset) {
        return getGlobalPage(filter, metric, region, null, limit, offset);
    }

    public RecordPage getGlobalPage(
        Filter filter,
        RecordMetric metric,
        LeagueShard region,
        Integer championId,
        int limit,
        int offset
    ) {
        List<ProfileRecord> records = MongoDB.findGlobalProfileRecords(filter, metric, region, championId, limit, offset);
        enrich(records);
        Object[] countScope = { filter.toSummonerKey(), metric.name(), LeagueShardUtils.leaderboardScope(region), championId == null ? "all" : championId };
        Long total = RedisClient.getLong(RedisKey.RECORD_COUNT.of(countScope));
        if (total == null) {
            total = MongoDB.countGlobalProfileRecords(filter, metric, region, championId);
            RedisClient.setCached(RedisKey.RECORD_COUNT, total.toString(), countScope);
        }
        long lastUpdate = lastUpdate(records);
        ResponseMetadata.Pagination pagination = new ResponseMetadata.Pagination(
            null, null, limit, offset, total, null, offset + records.size() < total);
        return new RecordPage(metric, records, limit, offset, total, offset + records.size() < total,
            new ResponseMetadata(pagination, lastUpdate == 0 ? null : lastUpdate, false, filter));
    }

    public boolean generate(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return false;
        List<ProfileRecord> previous = MongoDB.findProfileRecords(puuid, filter);
        ProfileRecordAnalyzer.Accumulator accumulator = ProfileRecordAnalyzer.accumulator(puuid, filter);
        MongoDB.forEachProfileRecordMatch(puuid, shard, filter, accumulator::accept);
        List<ProfileRecord> records = accumulator.finish();
        boolean saved = MongoDB.upsertProfileRecords(puuid, filter, records);
        if (saved) invalidateCounts(previous, records);
        return saved;
    }

    public boolean updateMasteries(String puuid, LeagueShard shard, List<Mastery> masteries) {
        if (puuid == null || puuid.isBlank() || shard == null) return false;
        Filter filter = Filter.canonical();
        List<ProfileRecord> previous = MongoDB.findProfileRecords(puuid, filter, RecordMetric.HIGHEST_MASTERY);
        List<ProfileRecord> records = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (masteries != null) for (Mastery mastery : masteries) {
            if (mastery == null || mastery.championId() <= 0 || mastery.points() < 0) continue;
            records.add(ProfileRecord.mastery(puuid, filter.toSummonerKey(), mastery, shard, now));
        }
        boolean saved = MongoDB.upsertProfileMasteryRecords(puuid, filter, records);
        if (saved) invalidateCounts(previous, records);
        return saved;
    }

    public int regenerateMasteries(LeagueShard shard) {
        if (shard == null) return 0;
        int[] refreshed = new int[1];
        MongoDB.forEachMasterySummonerBatch(shard, summoners -> {
            for (com.safjnest.lol.model.summoner.Summoner summoner : summoners)
                if (updateMasteries(summoner.puuid(), shard, summoner.masteries())) refreshed[0]++;
        });
        return refreshed[0];
    }

    // ============================================================================

    private static void enrich(List<ProfileRecord> records) {
        if (records == null || records.isEmpty()) return;
        Set<String> puuids = new HashSet<>();
        for (ProfileRecord record : records) {
            if (record == null) continue;
            if (record.puuid != null && !record.puuid.isBlank()) puuids.add(record.puuid);
        }
        if (puuids.isEmpty()) return;
        try {
            Map<String, Summoner> summoners = MongoDB.findSummonersByPuuids(new ArrayList<>(puuids));
            if (summoners == null || summoners.isEmpty()) return;
            for (ProfileRecord record : records) {
                if (record == null || record.puuid == null) continue;
                Summoner summoner = summoners.get(record.puuid);
                if (summoner == null) continue;
                record.riotId = summoner.riotId();
                record.icon = summoner.icon();
            }
        } catch (RuntimeException ignored) {}
    }

    private static long lastUpdate(List<ProfileRecord> records) {
        long result = 0;
        if (records == null) return result;
        for (ProfileRecord record : records) if (record != null) result = Math.max(result, record.lastUpdate);
        return result;
    }

    private static void invalidateCounts(List<ProfileRecord> previous, List<ProfileRecord> next) {
        Map<String, Integer> changes = new HashMap<>();
        for (int index = 0; index < 2; index++) {
            List<ProfileRecord> records = index == 0 ? previous : next;
            if (records == null) continue;
            for (ProfileRecord record : records) {
                if (record == null || record.metric == null) continue;
                List<String> regions = record.region == null ? List.of(LeagueShardUtils.leaderboardScope(null))
                    : List.of(LeagueShardUtils.leaderboardScope(null), record.region.name());
                List<String> champions = record.metric.championScoped()
                    ? List.of("all", Integer.toString(record.championId)) : List.of("all");
                for (String region : regions) for (String champion : champions)
                    changes.merge(RedisKey.RECORD_COUNT.of(record.filterKey, record.metric.name(), region, champion),
                        index == 0 ? -1 : 1, Integer::sum);
            }
        }
        List<String> keys = new ArrayList<>();
        for (var entry : changes.entrySet()) if (entry.getValue() != 0) keys.add(entry.getKey());
        CacheInvalidationService.clearRedis(keys);
    }
}
