package com.safjnest.lol.service;

import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.record.ProfileRecord;
import com.safjnest.lol.model.record.ProfileRecordPage;
import com.safjnest.lol.model.record.RecordMetric;
import com.safjnest.lol.model.record.RecordPage;
import com.safjnest.lol.model.record.RecordsOverview;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.DatabaseWorkerType;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class ProfileRecordService {

    private static final int GLOBAL_OVERVIEW_PER_METRIC = 5;
    private static final Map<String, CompletableFuture<Boolean>> INDEX_BUILDS = new ConcurrentHashMap<>();

    public ApiResult<ProfileRecordPage> get(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return ApiResult.notFound();
        List<ProfileRecord> records = MongoDB.findProfileRecords(puuid, filter);
        if (!records.isEmpty()) {
            resolveRankings(records);
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
            records.addAll(MongoDB.findGlobalProfileRecords(filter, metric, region, GLOBAL_OVERVIEW_PER_METRIC, 0));
        }
        resolveRankings(records);
        enrich(records);
        long lastUpdate = lastUpdate(records);
        return RecordsOverview.of(records, ResponseMetadata.ready(lastUpdate, filter));
    }

    public RecordPage getGlobalPage(Filter filter, RecordMetric metric, LeagueShard region, int limit, int offset) {
        List<ProfileRecord> records = MongoDB.findGlobalProfileRecords(filter, metric, region, limit, offset);
        resolveRankings(records);
        enrich(records);
        long total = MongoDB.countGlobalProfileRecords(filter, metric, region);
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
        if (saved) updateIndex(previous, records);
        return saved;
    }

    public static void resolveRankings(List<ProfileRecord> records) {
        if (records == null || records.isEmpty()) return;
        Set<RecordSegment> segments = new LinkedHashSet<>();
        List<String> keys = new ArrayList<>();
        List<byte[]> members = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (ProfileRecord record : records) {
            if (!classifiable(record)) continue;
            String globalKey = RedisKey.CONTEXTUAL_RECORD_SEGMENT.of(record.filterKey, record.metric.name(), LeagueShardUtils.leaderboardScope(null));
            segments.add(new RecordSegment(record.filterKey, record.metric, null));
            keys.add(globalKey); members.add(member(record)); scores.add((double) record.score);
            if (record.region == null) continue;
            String regionalKey = RedisKey.CONTEXTUAL_RECORD_SEGMENT.of(record.filterKey, record.metric.name(), LeagueShardUtils.leaderboardScope(record.region));
            segments.add(new RecordSegment(record.filterKey, record.metric, record.region));
            keys.add(regionalKey); members.add(member(record)); scores.add((double) record.score);
        }
        ensureIndexesAsync(segments);
        List<Long> positions = RedisClient.reverseRanks(keys, members);
        List<Long> above = RedisClient.countsAbove(keys, scores);
        int offset = 0;
        for (ProfileRecord record : records) {
            if (!classifiable(record)) continue;
            record.globalRanking = ranking(value(positions, offset), value(above, offset));
            offset++;
            if (record.region == null) continue;
            record.regionRanking = ranking(value(positions, offset), value(above, offset));
            offset++;
        }
    }

    public static IndexStatus indexStatus() {
        List<SegmentStatus> segments = new ArrayList<>();
        long memory = 0;
        Map<String, Long> accesses = RedisClient.getHashLongs(RedisKey.CONTEXTUAL_RECORD_ACCESS.of());
        for (String key : RedisClient.members(RedisKey.CONTEXTUAL_RECORD_SEGMENTS.of())) {
            if (!RedisClient.sortedSetExists(key)) continue;
            Long usage = RedisClient.memoryUsage(key);
            long used = usage == null ? 0 : usage;
            memory += used;
            segments.add(new SegmentStatus(key, RedisClient.sortedSetCardinality(key), used, RedisClient.ttl(key),
                accesses.getOrDefault(key, 0L), INDEX_BUILDS.containsKey(key)));
        }
        return new IndexStatus(segments.size(), memory, List.copyOf(segments));
    }

    public record SegmentStatus(String key, long cardinality, long memoryBytes, long ttlSeconds, long lastAccess, boolean rebuilding) {}
    public record IndexStatus(long segments, long memoryBytes, List<SegmentStatus> values) {}

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

    private static void updateIndex(List<ProfileRecord> previous, List<ProfileRecord> next) {
        if (previous != null) for (ProfileRecord record : previous) applyIndexChange(record, -1);
        if (next != null) for (ProfileRecord record : next) applyIndexChange(record, 1);
    }

    private static void applyIndexChange(ProfileRecord record, int delta) {
        if (!classifiable(record)) return;
        List<String> scopes = new ArrayList<>(2);
        scopes.add(LeagueShardUtils.leaderboardScope(null));
        if (record.region != null) scopes.add(LeagueShardUtils.leaderboardScope(record.region));
        for (String scope : scopes) {
            String key = RedisKey.CONTEXTUAL_RECORD_SEGMENT.of(record.filterKey, record.metric.name(), scope);
            if (!RedisClient.sortedSetExists(key)) continue;
            if (delta > 0) RedisClient.addSortedSet(key, List.of(new RedisClient.SortedSetEntry(member(record), record.score)));
            else RedisClient.removeSortedSetMember(key, member(record));
            RedisClient.expire(key, RedisKey.CONTEXTUAL_RECORD_SEGMENT.ttlSeconds());
            RedisClient.setHashLong(RedisKey.CONTEXTUAL_RECORD_ACCESS.of(), key, System.currentTimeMillis());
        }
    }

    private static void ensureIndexesAsync(Set<RecordSegment> segments) {
        if (segments.isEmpty()) return;
        Map<RecordSegment, String> keys = new LinkedHashMap<>();
        for (RecordSegment segment : segments) keys.put(segment, RedisKey.CONTEXTUAL_RECORD_SEGMENT.of(
            segment.filterKey(), segment.metric().name(), LeagueShardUtils.leaderboardScope(segment.region())));
        Set<String> existing = RedisClient.existingSortedSets(new ArrayList<>(keys.values()));
        for (Map.Entry<RecordSegment, String> entry : keys.entrySet()) {
            RecordSegment segment = entry.getKey();
            String key = entry.getValue();
            if (existing.contains(key)) {
                RedisClient.expire(key, RedisKey.CONTEXTUAL_RECORD_SEGMENT.ttlSeconds());
                RedisClient.setHashLong(RedisKey.CONTEXTUAL_RECORD_ACCESS.of(), key, System.currentTimeMillis());
                continue;
            }
            QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.PROFILE, "record-index:" + key, "record index " + key, job -> {
                rebuildIndex(segment, key);
                return null;
            });
        }
    }

    private static void rebuildIndex(RecordSegment segment, String key) {
        CompletableFuture<Boolean> created = new CompletableFuture<>();
        CompletableFuture<Boolean> existing = INDEX_BUILDS.putIfAbsent(key, created);
        if (existing != null) {
            existing.join();
            return;
        }
        try {
            boolean built = RedisClient.buildSortedSet(
                RedisKey.CONTEXTUAL_RANKING_BUILD.of(java.util.UUID.randomUUID()), key,
                RedisKey.CONTEXTUAL_RECORD_SEGMENT.ttlSeconds(), RedisKey.CONTEXTUAL_RANKING_BUILD.ttlSeconds(),
                entries -> MongoDB.forEachProfileRecordRankingSegment(segment.filterKey(), segment.metric(), segment.region(), record ->
                    entries.accept(new RedisClient.SortedSetEntry(member(record), record.score))));
            if (built) {
                RedisClient.addPersistentMember(RedisKey.CONTEXTUAL_RECORD_SEGMENTS.of(), key);
                RedisClient.setHashLong(RedisKey.CONTEXTUAL_RECORD_ACCESS.of(), key, System.currentTimeMillis());
            }
            created.complete(built);
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
            throw exception;
        } finally {
            INDEX_BUILDS.remove(key, created);
        }
    }

    static Long ranking(Long memberPosition, Long above) {
        return memberPosition == null || above == null ? null : above + 1;
    }

    private static Long value(List<Long> values, int index) {
        return index < values.size() ? values.get(index) : null;
    }

    private static boolean classifiable(ProfileRecord record) {
        return record != null && record.puuid != null && !record.puuid.isBlank() && record.filterKey != null
            && !record.filterKey.isBlank() && record.metric != null;
    }

    private static byte[] member(ProfileRecord record) {
        try {
            return java.util.Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(
                (record.filterKey + ':' + record.metric.name() + ':' + record.puuid).getBytes(StandardCharsets.UTF_8)), 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record RecordSegment(String filterKey, RecordMetric metric, LeagueShard region) {}
}
