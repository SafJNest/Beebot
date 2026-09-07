package com.safjnest.lol.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bson.types.Binary;

import com.safjnest.lol.model.competitive.CompetitiveEntry;
import com.safjnest.lol.model.record.ProfileRecord;
import com.safjnest.lol.model.record.RecordMetric;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public final class RankingService {

    private static final int LAZY_TTL_SECONDS = 6 * 60 * 60;
    private static final int REDIS_BUILD_BATCH_SIZE = 250;
    private static final int TEMPORARY_BUILD_TTL_SECONDS = 10 * 60;
    private static final List<GameQueueType> LEADERBOARD_QUEUES = List.of(
        GameQueueType.RANKED_SOLO_5X5,
        GameQueueType.RANKED_FLEX_SR
    );
    private static final List<TierDivisionType> DESCENDING_RANKS = Arrays.stream(TierDivisionType.values())
        .filter(tier -> tier != TierDivisionType.UNRANKED)
        .sorted(Comparator.comparingInt((TierDivisionType tier) -> TierDivisionUtils.getMmr(tier, 0)).reversed())
        .toList();
    private static final Map<String, CompletableFuture<Boolean>> BUILDS = new ConcurrentHashMap<>();

    private RankingService() {}

    public static Map<GameQueueType, Rank> enrichRanks(
        String puuid,
        LeagueShard region,
        Map<GameQueueType, Rank> ranks
    ) {
        if (puuid == null || puuid.isBlank()) return ranks == null ? Map.of() : Map.copyOf(ranks);
        Map<String, Map<GameQueueType, Rank>> result = enrichRanks(List.of(new RankSubject(puuid, region, ranks)));
        return result.getOrDefault(puuid, ranks == null ? Map.of() : Map.copyOf(ranks));
    }

    public static Map<String, Map<GameQueueType, Rank>> enrichRanks(List<RankSubject> subjects) {
        Map<String, Map<GameQueueType, Rank>> result = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        List<byte[]> members = new ArrayList<>();
        List<RankRequest> requests = new ArrayList<>();
        Map<String, SegmentRequest> segments = new LinkedHashMap<>();
        Map<String, Map<TierDivisionType, Long>> countsByKey = new HashMap<>();

        if (subjects == null) return result;
        for (RankSubject subject : subjects) {
            if (subject == null || subject.puuid() == null || subject.puuid().isBlank() || subject.ranks() == null) continue;
            Map<GameQueueType, Rank> enriched = new LinkedHashMap<>(subject.ranks());
            result.put(subject.puuid(), enriched);
            if (subject.region() == null) continue;
            for (Map.Entry<GameQueueType, Rank> entry : subject.ranks().entrySet()) {
                GameQueueType queue = entry.getKey() == null ? null : GameQueueTypeUtils.canonicalQueue(entry.getKey());
                Rank rank = entry.getValue();
                if (queue == null || rank == null || rank.tier() == null || rank.tier() == TierDivisionType.UNRANKED) continue;
                String globalSegment = leaderboardKey(queue, rank.tier(), null);
                String regionalSegment = leaderboardKey(queue, rank.tier(), subject.region());
                segments.putIfAbsent(globalSegment, new SegmentRequest(queue, rank.tier(), null));
                segments.putIfAbsent(regionalSegment, new SegmentRequest(queue, rank.tier(), subject.region()));
                String globalCountKey = countsKey(queue, null);
                String regionalCountKey = countsKey(queue, subject.region());
                Map<TierDivisionType, Long> globalCounts = countsByKey.computeIfAbsent(globalCountKey, ignored -> counts(queue, null));
                Map<TierDivisionType, Long> regionalCounts = countsByKey.computeIfAbsent(regionalCountKey, ignored -> counts(queue, subject.region()));
                byte[] member = leaderboardMember(subject.puuid(), queue);
                requests.add(new RankRequest(subject.puuid(), queue, rank, globalCounts, regionalCounts));
                keys.add(globalSegment); members.add(member);
                keys.add(regionalSegment); members.add(member);
            }
        }

        ensureLeaderboardSegments(segments);

        List<Long> positions = RedisClient.reverseRanks(keys, members);
        for (int index = 0; index + 1 < positions.size(); index += 2) {
            Long globalPosition = positions.get(index);
            Long regionalPosition = positions.get(index + 1);
            RankRequest request = requests.get(index / 2);
            Long global = absoluteRanking(request.globalCounts(), request.rank().tier(), globalPosition);
            Long regional = absoluteRanking(request.regionalCounts(), request.rank().tier(), regionalPosition);
            Map<GameQueueType, Rank> enriched = result.get(request.puuid());
            if (enriched != null) enriched.put(request.queue(), request.rank().withRankings(global, regional));
        }
        Map<String, Map<GameQueueType, Rank>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, Map<GameQueueType, Rank>> entry : result.entrySet()) immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        return Map.copyOf(immutable);
    }

    public static void enrichRecords(List<ProfileRecord> records) {
        if (records == null || records.isEmpty()) return;
        Map<String, RecordSegmentRequest> segments = new LinkedHashMap<>();
        for (ProfileRecord record : records) {
            if (!classifiable(record)) continue;
            segments.putIfAbsent(recordKey(record.filterKey, record.metric, null),
                new RecordSegmentRequest(record.filterKey, record.metric, null));
            if (record.region != null) segments.putIfAbsent(recordKey(record.filterKey, record.metric, record.region),
                new RecordSegmentRequest(record.filterKey, record.metric, record.region));
        }
        ensureRecordSegments(segments);
        List<ProfileRecord> values = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<byte[]> members = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (ProfileRecord record : records) {
            if (!classifiable(record)) continue;
            byte[] member = recordMember(record);
            values.add(record); keys.add(recordKey(record.filterKey, record.metric, null)); members.add(member); scores.add((double) record.score);
            if (record.region != null) {
                values.add(record); keys.add(recordKey(record.filterKey, record.metric, record.region)); members.add(member); scores.add((double) record.score);
            }
        }
        List<Long> membersFound = RedisClient.reverseRanks(keys, members);
        List<Long> above = RedisClient.countsAbove(keys, scores);
        int offset = 0;
        for (ProfileRecord record : records) {
            if (!classifiable(record)) continue;
            Long globalMember = offset < membersFound.size() ? membersFound.get(offset) : null;
            Long globalAbove = offset < above.size() ? above.get(offset) : null;
            record.globalRanking = competitionRanking(globalMember, globalAbove);
            offset++;
            if (record.region == null) continue;
            Long regionalMember = offset < membersFound.size() ? membersFound.get(offset) : null;
            Long regionalAbove = offset < above.size() ? above.get(offset) : null;
            record.regionRanking = competitionRanking(regionalMember, regionalAbove);
            offset++;
        }
    }

    public static void refreshLeaderboard(CompetitiveEntry previous, CompetitiveEntry next) {
        updateLeaderboard(previous, false);
        updateLeaderboard(next, true);
        if (next != null && permanent(next.tier())) {
            ensureLeaderboardSegment(next.queue(), next.tier(), null);
            ensureLeaderboardSegment(next.queue(), next.tier(), next.region());
        }
    }

    public static void refreshRecords(List<ProfileRecord> previous, List<ProfileRecord> next) {
        if (previous != null) for (ProfileRecord record : previous) updateRecord(record, false);
        if (next != null) for (ProfileRecord record : next) updateRecord(record, true);
    }

    public static void rebuildLeaderboard() {
        for (GameQueueType queue : LEADERBOARD_QUEUES) {
            counts(queue, null, true);
            for (LeagueShard region : MongoDB.findCompetitiveRankingRegions(queue)) counts(queue, region, true);
            for (TierDivisionType tier : DESCENDING_RANKS) if (permanent(tier)) {
                ensureLeaderboardSegment(queue, tier, null, true);
                for (LeagueShard region : MongoDB.findCompetitiveRankingRegions(queue))
                    ensureLeaderboardSegment(queue, tier, region, true);
            }
        }
    }

    public static RankingSnapshot snapshot() {
        long leaderboardSegments = 0;
        long recordSegments = 0;
        long memory = 0;
        List<Segment> segments = new ArrayList<>();
        Map<String, Long> accesses = RedisClient.getHashLongs(RedisKey.CONTEXTUAL_RANKING_ACCESS.of());
        for (String registered : RedisClient.members(RedisKey.CONTEXTUAL_RANKING_SEGMENTS.of())) {
            int separator = registered.indexOf('|');
            if (separator < 1) continue;
            String kind = registered.substring(0, separator);
            String key = registered.substring(separator + 1);
            if (!RedisClient.sortedSetExists(key)) {
                RedisClient.removeMember(RedisKey.CONTEXTUAL_RANKING_SEGMENTS.of(), registered);
                RedisClient.removeHashField(RedisKey.CONTEXTUAL_RANKING_ACCESS.of(), key);
                continue;
            }
            boolean permanent = registered.startsWith("L|") && permanentKey(key);
            long cardinality = RedisClient.sortedSetCardinality(key);
            Long usage = RedisClient.memoryUsage(key);
            long used = usage == null ? 0 : usage;
            if ("L".equals(kind)) leaderboardSegments++;
            if ("R".equals(kind)) recordSegments++;
            memory += used;
            segments.add(new Segment(kind, key, cardinality, used, permanent, RedisClient.ttl(key),
                accesses.getOrDefault(key, 0L), BUILDS.containsKey(key)));
        }
        return new RankingSnapshot(leaderboardSegments, recordSegments, memory, List.copyOf(segments));
    }

    public record Segment(String kind, String key, long cardinality, long memoryBytes, boolean permanent, long ttlSeconds, long lastAccess, boolean rebuilding) {}

    public record RankingSnapshot(long leaderboardSegments, long recordSegments, long memoryBytes, List<Segment> segments) {}

    public record RankSubject(String puuid, LeagueShard region, Map<GameQueueType, Rank> ranks) {}

    // ============================================================================

    private static void updateLeaderboard(CompetitiveEntry entry, boolean add) {
        if (entry == null || entry.tier() == null || entry.tier() == TierDivisionType.UNRANKED) return;
        GameQueueType queue = GameQueueTypeUtils.canonicalQueue(entry.queue());
        updateLeaderboardScope(entry, queue, null, add);
        updateLeaderboardScope(entry, queue, entry.region(), add);
    }

    private static void updateRecord(ProfileRecord record, boolean add) {
        if (!classifiable(record)) return;
        updateRecordScope(record, null, add);
        if (record.region != null) updateRecordScope(record, record.region, add);
    }

    private static void updateLeaderboardScope(CompetitiveEntry entry, GameQueueType queue, LeagueShard region, boolean add) {
        String key = leaderboardKey(queue, entry.tier(), region);
        RedisClient.incrementHashIfPresent(countsKey(queue, region), entry.tier().name(), add ? 1 : -1);
        if (!RedisClient.sortedSetExists(key)) return;
        if (add) RedisClient.addSortedSet(key, List.of(new RedisClient.SortedSetEntry(leaderboardMember(entry.puuid(), queue), entry.mmr())));
        else RedisClient.removeSortedSetMember(key, leaderboardMember(entry.puuid(), queue));
        touch(key, permanent(entry.tier()));
    }

    private static void updateRecordScope(ProfileRecord record, LeagueShard region, boolean add) {
        String key = recordKey(record.filterKey, record.metric, region);
        if (!RedisClient.sortedSetExists(key)) return;
        if (add) RedisClient.addSortedSet(key, List.of(new RedisClient.SortedSetEntry(recordMember(record), record.score)));
        else RedisClient.removeSortedSetMember(key, recordMember(record));
        touch(key, false);
    }

    private static void ensureLeaderboardSegment(GameQueueType queue, TierDivisionType tier, LeagueShard region) {
        ensureLeaderboardSegment(queue, tier, region, false);
    }

    private static void ensureLeaderboardSegment(GameQueueType queue, TierDivisionType tier, LeagueShard region, boolean force) {
        String key = leaderboardKey(queue, tier, region);
        if (!force && RedisClient.sortedSetExists(key)) {
            touch(key, permanent(tier));
            return;
        }
        build(key, permanent(tier), entries -> MongoDB.forEachCompetitiveRankingSegment(queue, tier, region, entry ->
            entries.accept(new RedisClient.SortedSetEntry(leaderboardMember(entry.puuid(), queue), entry.mmr()))));
    }

    private static void ensureLeaderboardSegments(Map<String, SegmentRequest> segments) {
        if (segments == null || segments.isEmpty()) return;
        Set<String> existing = RedisClient.existingSortedSets(new ArrayList<>(segments.keySet()));
        for (Map.Entry<String, SegmentRequest> entry : segments.entrySet()) {
            SegmentRequest request = entry.getValue();
            if (existing.contains(entry.getKey())) {
                touch(entry.getKey(), permanent(request.tier()));
                continue;
            }
            build(entry.getKey(), permanent(request.tier()), values -> MongoDB.forEachCompetitiveRankingSegment(
                request.queue(), request.tier(), request.region(), competitive ->
                values.accept(new RedisClient.SortedSetEntry(leaderboardMember(competitive.puuid(), request.queue()), competitive.mmr()))));
        }
    }

    private static void ensureRecordSegments(Map<String, RecordSegmentRequest> segments) {
        if (segments == null || segments.isEmpty()) return;
        Set<String> existing = RedisClient.existingSortedSets(new ArrayList<>(segments.keySet()));
        for (Map.Entry<String, RecordSegmentRequest> entry : segments.entrySet()) {
            RecordSegmentRequest request = entry.getValue();
            if (existing.contains(entry.getKey())) {
                touch(entry.getKey(), false);
                continue;
            }
            build(entry.getKey(), false, values -> MongoDB.forEachProfileRecordRankingSegment(
                request.filterKey(), request.metric(), request.region(), record ->
                values.accept(new RedisClient.SortedSetEntry(recordMember(record), record.score))));
        }
    }

    private static void build(String key, boolean permanent, SegmentSource source) {
        CompletableFuture<Boolean> created = new CompletableFuture<>();
        CompletableFuture<Boolean> existing = BUILDS.putIfAbsent(key, created);
        if (existing != null) {
            existing.join();
            return;
        }
        String temporaryKey = key + ":building:" + UUID.randomUUID();
        try {
            List<RedisClient.SortedSetEntry> batch = new ArrayList<>(REDIS_BUILD_BATCH_SIZE);
            source.read(entry -> {
                batch.add(entry);
                if (batch.size() < REDIS_BUILD_BATCH_SIZE) return;
                RedisClient.addSortedSet(temporaryKey, batch);
                batch.clear();
            });
            if (!batch.isEmpty()) RedisClient.addSortedSet(temporaryKey, batch);
            RedisClient.expire(temporaryKey, TEMPORARY_BUILD_TTL_SECONDS);
            if (RedisClient.sortedSetExists(temporaryKey)) {
                RedisClient.publishSortedSet(temporaryKey, key, permanent ? 0 : LAZY_TTL_SECONDS);
                RedisClient.addPersistentMember(RedisKey.CONTEXTUAL_RANKING_SEGMENTS.of(), (permanent ? "L|" : key.contains(":records:") ? "R|" : "L|") + key);
                RedisClient.setHashLong(RedisKey.CONTEXTUAL_RANKING_ACCESS.of(), key, System.currentTimeMillis());
            }
            created.complete(RedisClient.sortedSetExists(key));
        } catch (RuntimeException exception) {
            created.completeExceptionally(exception);
        } finally {
            RedisClient.delete(temporaryKey);
            BUILDS.remove(key, created);
        }
    }

    private static Map<TierDivisionType, Long> counts(GameQueueType queue, LeagueShard region) {
        return counts(queue, region, false);
    }

    private static Map<TierDivisionType, Long> counts(GameQueueType queue, LeagueShard region, boolean rebuild) {
        String key = countsKey(queue, region);
        Map<String, Long> stored = rebuild ? Map.of() : RedisClient.getHashLongs(key);
        if (!stored.isEmpty()) return toRanks(stored);
        Map<TierDivisionType, Long> rebuilt = MongoDB.findCompetitiveRankCounts(queue, region);
        Map<String, Long> encoded = new HashMap<>();
        for (Map.Entry<TierDivisionType, Long> entry : rebuilt.entrySet()) encoded.put(entry.getKey().name(), entry.getValue());
        RedisClient.replaceHash(key, encoded);
        return rebuilt;
    }

    private static Map<TierDivisionType, Long> toRanks(Map<String, Long> values) {
        Map<TierDivisionType, Long> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            try { result.put(TierDivisionType.valueOf(entry.getKey()), entry.getValue()); }
            catch (RuntimeException ignored) {}
        }
        return result;
    }

    static Long absoluteRanking(Map<TierDivisionType, Long> counts, TierDivisionType tier, Long withinDivision) {
        if (counts == null || withinDivision == null || !counts.containsKey(tier)) return null;
        long higher = 0;
        for (TierDivisionType value : DESCENDING_RANKS) {
            if (value == tier) break;
            higher += counts.getOrDefault(value, 0L);
        }
        return higher + withinDivision + 1;
    }

    static Long competitionRanking(Long memberPosition, Long above) {
        return memberPosition == null || above == null ? null : above + 1;
    }

    private static byte[] leaderboardMember(String puuid, GameQueueType queue) {
        Binary id = MongoDB.competitiveId(puuid, GameQueueTypeUtils.canonicalQueue(queue).name());
        return id.getData();
    }

    private static byte[] recordMember(ProfileRecord record) {
        return hash128(record.filterKey + ':' + record.metric.name() + ':' + record.puuid);
    }

    private static byte[] hash128(String value) {
        try {
            return Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String leaderboardKey(GameQueueType queue, TierDivisionType tier, LeagueShard region) {
        return RedisKey.CONTEXTUAL_LEADERBOARD_SEGMENT.of(GameQueueTypeUtils.canonicalQueue(queue).name(), scope(region), tier.name());
    }

    private static String countsKey(GameQueueType queue, LeagueShard region) {
        return RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS.of(GameQueueTypeUtils.canonicalQueue(queue).name(), scope(region));
    }

    private static String recordKey(String filterKey, RecordMetric metric, LeagueShard region) {
        return RedisKey.CONTEXTUAL_RECORD_SEGMENT.of(filterKey, metric.name(), scope(region));
    }

    private static String scope(LeagueShard region) {
        return region == null ? "GLOBAL" : region.name();
    }

    static boolean permanent(TierDivisionType tier) {
        return TierDivisionUtils.isHighElo(tier);
    }

    private static boolean permanentKey(String key) {
        return key.contains(":MASTER_I") || key.contains(":GRANDMASTER_I") || key.contains(":CHALLENGER_I");
    }

    private static boolean classifiable(ProfileRecord record) {
        return record != null && record.puuid != null && !record.puuid.isBlank() && record.filterKey != null
            && !record.filterKey.isBlank() && record.metric != null;
    }

    private static void touch(String key, boolean permanent) {
        if (!permanent) RedisClient.expire(key, LAZY_TTL_SECONDS);
        RedisClient.setHashLong(RedisKey.CONTEXTUAL_RANKING_ACCESS.of(), key, System.currentTimeMillis());
    }

    private record RankRequest(
        String puuid,
        GameQueueType queue,
        Rank rank,
        Map<TierDivisionType, Long> globalCounts,
        Map<TierDivisionType, Long> regionalCounts
    ) {}

    private record SegmentRequest(GameQueueType queue, TierDivisionType tier, LeagueShard region) {}

    private record RecordSegmentRequest(String filterKey, RecordMetric metric, LeagueShard region) {}

    @FunctionalInterface
    private interface SegmentSource {
        void read(java.util.function.Consumer<RedisClient.SortedSetEntry> entries);
    }
}
