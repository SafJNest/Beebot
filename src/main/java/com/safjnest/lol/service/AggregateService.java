package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bson.Document;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.job.Job;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.DatabaseWorkerType;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.LeagueConstants;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.status.LeagueMetricsStore;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public final class AggregateService {

    private static final List<String> TYPES = List.of("leaderboard-counts", "rank-distribution", "top-regions",
        "division-counts", "record-counts", "metrics");
    private static final TypeReference<Map<String, Long>> COUNTS_TYPE = new TypeReference<>() {};
    private static final int LOCK_COUNT = 64;
    private static final Object[] LOCKS = new Object[LOCK_COUNT];
    private static final Set<String> UNPUBLISHED = ConcurrentHashMap.newKeySet();

    static {
        for (int index = 0; index < LOCKS.length; index++) LOCKS[index] = new Object();
    }

    private AggregateService() {}

    public static List<String> types() {
        return TYPES;
    }

    public static <T> T get(RedisKey key, Class<T> type, Supplier<T> source, Object... arguments) {
        return read(new Scope(key, strings(arguments)), json -> JsonCodec.fromJson(json, type), source);
    }

    public static <T> T get(RedisKey key, TypeReference<T> type, Supplier<T> source, Object... arguments) {
        return read(new Scope(key, strings(arguments)), json -> JsonCodec.fromJson(json, type), source);
    }

    public static Map<String, Long> findHash(RedisKey key, Object... arguments) {
        return get(key, COUNTS_TYPE, null, arguments);
    }

    public static void put(RedisKey key, Object value, Object... arguments) {
        Scope scope = new Scope(key, strings(arguments));
        synchronized (lock(scope.id())) {
            store(scope, value);
        }
    }

    public static void incrementHash(RedisKey key, String field, long delta, Object... arguments) {
        if (delta == 0) return;
        Scope scope = new Scope(key, strings(arguments));
        synchronized (lock(scope.id())) {
            UNPUBLISHED.add(scope.id());
            Document updated = MongoDB.incrementAggregateHash(scope.id(), field, delta);
            if (updated == null) {
                if (RedisClient.delete(scope.id())) UNPUBLISHED.remove(scope.id());
                return;
            }
            publish(scope, updated.get("value"));
        }
    }

    public static void invalidate(Collection<String> ids) {
        for (String id : ids) synchronized (lock(id)) {
            UNPUBLISHED.add(id);
            MongoDB.deleteAggregate(id);
            if (RedisClient.delete(id)) UNPUBLISHED.remove(id);
        }
    }

    public static void enqueueRebuild(String type) {
        List<String> selected = "all".equals(type) ? TYPES : List.of(type);
        if (!TYPES.containsAll(selected)) throw new IllegalArgumentException("Unsupported aggregate type: " + type);
        for (String item : selected) {
            QueueHandler.background(ComputeScheduler.class, DatabaseWorkerType.MONGO,
                "aggregate-rebuild:" + item, "regenerate aggregates " + item, job -> {
                    rebuild(item, job);
                    return null;
                });
        }
    }

    public record Scope(RedisKey key, List<String> arguments) {
        public Scope {
            Objects.requireNonNull(key);
            arguments = List.copyOf(arguments);
        }

        public String id() {
            return key.of(arguments.toArray());
        }
    }

    // ============================================================================

    private static <T> T read(Scope scope, Function<String, T> decode, Supplier<T> source) {
        synchronized (lock(scope.id())) {
            if (!UNPUBLISHED.contains(scope.id())) {
                String cached;
                if (scope.key() == RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS) {
                    Map<String, Long> values = RedisClient.getHashLongs(scope.id());
                    cached = values.isEmpty() ? null : JsonCodec.toJson(values);
                } else cached = RedisClient.get(scope.id());
                T value = decode.apply(cached);
                if (value != null) return value;
            }
            Document stored = MongoDB.findAggregate(scope.id());
            if (stored != null) {
                T value = decode.apply(JsonCodec.toJson(stored.get("value")));
                if (value != null) {
                    publish(scope, value);
                    return value;
                }
            }
            if (source == null) return null;
            T value = Objects.requireNonNull(source.get(), "Aggregate source returned null");
            store(scope, value);
            return value;
        }
    }

    private static void store(Scope scope, Object value) {
        Objects.requireNonNull(value, "Aggregate value is required");
        UNPUBLISHED.add(scope.id());
        MongoDB.storeAggregate(scope.id(), scope.key().name(), scope.arguments(), value);
        publish(scope, value);
    }

    private static void publish(Scope scope, Object value) {
        UNPUBLISHED.add(scope.id());
        if (scope.key() == RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS) {
            Map<String, Long> counts = JsonCodec.fromJson(JsonCodec.toJson(value), COUNTS_TYPE);
            try {
                RedisClient.replaceHash(scope.id(), counts);
                UNPUBLISHED.remove(scope.id());
            } catch (RuntimeException ignored) {
                // The durable snapshot remains readable while Redis is unavailable.
            }
        } else if (RedisClient.setCached(scope.key(), JsonCodec.toJson(value), scope.arguments().toArray())) {
            UNPUBLISHED.remove(scope.id());
        }
    }

    private static void rebuild(String type, Job<?> job) {
        job.phase("DISCOVER " + type);
        Map<String, Scope> scopes = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();
        for (RedisKey key : keys(type)) names.add(key.name());
        for (Document stored : MongoDB.findAggregateScopes(names)) {
            Scope scope = new Scope(RedisKey.valueOf(stored.getString("type")), stored.getList("arguments", String.class));
            if (!scope.id().equals(stored.getString("_id"))) throw new IllegalStateException("Invalid aggregate identity: " + stored.getString("_id"));
            scopes.put(scope.id(), scope);
        }
        seed(type, scopes);
        Map<List<String>, Long> records = "record-counts".equals(type) ? ProfileRecordService.calculateCounts() : Map.of();
        for (List<String> arguments : records.keySet()) add(scopes, RedisKey.RECORD_COUNT, arguments.toArray());
        Map<String, Map<String, Map<String, Long>>> divisions = new LinkedHashMap<>();
        if ("division-counts".equals(type)) {
            for (Scope scope : List.copyOf(scopes.values())) {
                String queue = scope.arguments().get(0);
                if (divisions.containsKey(queue)) continue;
                Map<String, Map<String, Long>> counts = LeaderboardService.calculateDivisionCounts(queue);
                divisions.put(queue, counts);
                for (String region : counts.keySet()) add(scopes, RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS, queue, region);
            }
        }
        job.trackItems(scopes.keySet());
        int completed = 0;
        int missing = 0;
        int failed = 0;
        for (Scope scope : scopes.values()) {
            job.currentItem(scope.id());
            try {
                Object value = switch (scope.key()) {
                    case RECORD_COUNT -> records.getOrDefault(scope.arguments(), 0L);
                    case CONTEXTUAL_LEADERBOARD_COUNTS -> divisionCounts(divisions, scope);
                    case STATUS_GAMES_ANALYZED, STATUS_TOTAL_SUMMONERS, STATUS_TOTAL_MASTERIES,
                         STATUS_TRACKED_SUMMONERS, STATUS_RANKS_BY_QUEUE -> LeagueMetricsStore.calculate(scope.key());
                    default -> LeaderboardService.calculateAggregate(scope.key(), scope.arguments());
                };
                if (value == null) {
                    job.missing(scope.id());
                    missing++;
                } else {
                    put(scope.key(), value, scope.arguments().toArray());
                    job.done(scope.id());
                    completed++;
                }
            } catch (RuntimeException exception) {
                job.failed(scope.id());
                failed++;
                com.safjnest.utils.log.BotLogger.error("Aggregate rebuild failed: " + scope.id() + " " + exception.getMessage());
            }
            job.phase("REBUILD total=" + scopes.size() + " completed=" + completed + " missing=" + missing + " failed=" + failed);
        }
        job.phase((failed == 0 ? "DONE" : "FAILED") + " total=" + scopes.size()
            + " completed=" + completed + " missing=" + missing + " failed=" + failed);
        if ("metrics".equals(type)) LeagueMetricsStore.refresh();
        if (failed > 0) throw new IllegalStateException("Aggregate rebuild failed for " + failed + " scopes");
    }

    private static void seed(String type, Map<String, Scope> scopes) {
        if ("metrics".equals(type)) {
            for (RedisKey key : keys(type)) add(scopes, key);
            return;
        }
        if ("record-counts".equals(type)) return;
        List<String> regions = new ArrayList<>(List.of(LeagueConstants.GLOBAL_REGION));
        for (LeagueShard shard : LeagueShardUtils.getActives()) regions.add(shard.name());
        for (var queue : GameQueueTypeUtils.leaderboardQueues()) {
            if ("top-regions".equals(type)) {
                for (TierType tier : TierType.values()) add(scopes, RedisKey.LEADERBOARD_TOP_REGIONS, queue.name(), tier.name());
                continue;
            }
            for (String region : regions) {
                switch (type) {
                    case "leaderboard-counts" -> {
                        add(scopes, RedisKey.LEADERBOARD_COUNT, queue.name(), region, LeagueConstants.ALL, LeagueConstants.ALL, LeagueConstants.ALL);
                        for (TierType tier : TierType.values()) if (tier != TierType.UNRANKED)
                            add(scopes, RedisKey.LEADERBOARD_COUNT, queue.name(), region, tier.name(), LeagueConstants.ALL, LeagueConstants.ALL);
                    }
                    case "rank-distribution" -> add(scopes, RedisKey.LEADERBOARD_RANK_DISTRIBUTION, queue.name(), region);
                    case "division-counts" -> add(scopes, RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS, queue.name(), region);
                    default -> throw new IllegalArgumentException("Unsupported aggregate type: " + type);
                }
            }
        }
    }

    private static Map<String, Long> divisionCounts(Map<String, Map<String, Map<String, Long>>> values, Scope scope) {
        Map<String, Long> result = new LinkedHashMap<>(values.get(scope.arguments().get(0))
            .getOrDefault(scope.arguments().get(1), Map.of()));
        for (TierDivisionType tier : TierDivisionUtils.descendingRankedDivisions()) result.putIfAbsent(tier.name(), 0L);
        return result;
    }

    private static List<RedisKey> keys(String type) {
        return switch (type) {
            case "leaderboard-counts" -> List.of(RedisKey.LEADERBOARD_COUNT);
            case "rank-distribution" -> List.of(RedisKey.LEADERBOARD_RANK_DISTRIBUTION);
            case "top-regions" -> List.of(RedisKey.LEADERBOARD_TOP_REGIONS);
            case "division-counts" -> List.of(RedisKey.CONTEXTUAL_LEADERBOARD_COUNTS);
            case "record-counts" -> List.of(RedisKey.RECORD_COUNT);
            case "metrics" -> List.of(RedisKey.STATUS_GAMES_ANALYZED, RedisKey.STATUS_TOTAL_SUMMONERS,
                RedisKey.STATUS_TOTAL_MASTERIES, RedisKey.STATUS_TRACKED_SUMMONERS, RedisKey.STATUS_RANKS_BY_QUEUE);
            default -> throw new IllegalArgumentException("Unsupported aggregate type: " + type);
        };
    }

    private static void add(Map<String, Scope> scopes, RedisKey key, Object... arguments) {
        Scope scope = new Scope(key, strings(arguments));
        scopes.putIfAbsent(scope.id(), scope);
    }

    private static List<String> strings(Object[] arguments) {
        List<String> values = new ArrayList<>(arguments.length);
        for (Object value : arguments) values.add(Objects.requireNonNull(value).toString());
        return List.copyOf(values);
    }

    private static Object lock(String id) {
        return LOCKS[Math.floorMod(id.hashCode(), LOCKS.length)];
    }
}
