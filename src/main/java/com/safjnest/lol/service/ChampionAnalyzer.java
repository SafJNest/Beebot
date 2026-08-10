package com.safjnest.lol.service;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.champion.ChampionStatsData;
import com.safjnest.lol.champion.ChampionStatsProvider;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.LaneSynergy;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.ChampionStatistics.MatchupKey;
import com.safjnest.lol.model.ChampionStatistics.Overview;
import com.safjnest.lol.model.ChampionStatistics.PowerCurvePoint;
import com.safjnest.lol.model.ChampionStatistics.Trend;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

import org.json.JSONArray;
import org.json.JSONObject;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChampionAnalyzer {

    private static final long AT_15_MS = 15 * 60 * 1000L;
    private static final List<String> POWER_BUCKETS = List.of("0-15", "15-25", "25-35", "35-45", "45+");

    private static final int MATCHES = 0;
    private static final int WINS = 1;
    private static final int METRIC_GAMES = 2;
    private static final int GOLD_DIFF_SUM = 3;
    private static final int GOLD_DIFF_GAMES = 4;
    private static final int CS_DIFF_SUM = 5;
    private static final int CS_DIFF_GAMES = 6;
    private static final int SOLO_KILLS = 7;
    private static final int KILLS = 8;
    private static final int KILL_PARTICIPATION_SUM = 9;
    private static final int KILL_PARTICIPATION_GAMES = 10;
    private static final int MATCHUP_VALUE_SIZE = 11;

    private static final int KDA_KILLS = 0;
    private static final int KDA_DEATHS = 1;
    private static final int KDA_ASSISTS = 2;
    private static final int KDA_GAMES = 3;
    private static final int EVENT_GAMES = 4;
    private static final int CS_PER_MINUTE_SUM = 5;
    private static final int CS_PER_MINUTE_GAMES = 6;
    private static final int GOLD_PER_MINUTE_SUM = 7;
    private static final int GOLD_PER_MINUTE_GAMES = 8;
    private static final int METRIC_VALUE_SIZE = 9;

    private ChampionAnalyzer() {}

    public static Map<Integer, ChampionStatistics> getAll(Filter filter) {
        Map<Integer, ChampionStatistics> cached;
        try {
            cached = MongoDB.findChampionStatistics(filter);
        } catch (RuntimeException exception) {
            BotLogger.warning("Invalid persisted champion stats for " + filter.genericKey()
                + ": " + exception.getMessage());
            cached = null;
        }
        return cached != null && (!cached.isEmpty() || MongoDB.hasChampionStatisticsReady(filter))
            ? cached : compute(filter, true);
    }

    public static ChampionStatistics get(Filter filter) {
        return get(filter, true);
    }

    public static boolean hasStored(Filter filter) {
        return filter != null && filter.champion() != 0 && get(filter, false) != null;
    }

    public static Map<Integer, ChampionStatistics> recomputeAll(Filter filter) {
        Map<Integer, ChampionStatistics> computed = compute(filter, false);
        if (computed != null && !computed.isEmpty()) {
            MongoDB.upsertChampionStatistics(computed);
        }
        return computed;
    }

    // ============================================================================

    static ChampionStatistics empty(Filter filter) {
        return new ChampionStatistics(
            filter,
            new ChampionStatistics.Overview(0, 0, 0, 0, 0, 0, null, null, null, null, null),
            List.of(),
            Map.of(),
            List.of(),
            List.of(),
            null
        );
    }

    static ChampionStatistics get(Filter filter, boolean allowCompute) {
        if (filter == null) return null;

        String key = RedisKey.CHAMPION_STATS.of(filter.genericKey(), filter.champion());
        ChampionStatistics stats;
        try {
            stats = RedisClient.get(key, ChampionStatistics.class);
        } catch (RuntimeException exception) {
            RedisClient.delete(key);
            stats = null;
        }
        if (stats != null) return stats;

        try {
            stats = MongoDB.findChampionStatistics(filter, filter.champion());
        } catch (RuntimeException exception) {
            BotLogger.warning("Invalid persisted champion stats for " + filter.toKey()
                + ": " + exception.getMessage());
            return null;
        }
        if (stats != null) {
            RedisClient.set(RedisKey.CHAMPION_STATS, stats, filter.genericKey(), filter.champion());
            return stats;
        }
        if (MongoDB.hasChampionStatisticsReady(filter)) {
            stats = empty(filter);
            RedisClient.set(RedisKey.CHAMPION_STATS, stats, filter.genericKey(), filter.champion());
            return stats;
        }
        if (!allowCompute) return null;

        Map<Integer, ChampionStatistics> computed = compute(filter, true);
        stats = computed == null ? null : computed.get(filter.champion());
        if (stats != null) RedisClient.set(RedisKey.CHAMPION_STATS, stats, filter.genericKey(), filter.champion());
        return stats;
    }

    static MatrixResult recomputeMatrix(List<Filter> filters) {
        return recomputeMatrix(filters, List.of());
    }

    static MatrixResult recomputeMatrix(List<Filter> filters, List<Filter> buildFilters) {
        if (filters == null || filters.isEmpty()) return new MatrixResult(0, 0, 0);

        Map<String, Filter> matrixFilters = new LinkedHashMap<>();
        for (int regionScope = 0; regionScope < 2; regionScope++) for (Filter filter : filters) {
            if (filter == null || filter.patch() == null || filter.queue() == null
                    || (regionScope == 0) != (filter.region() == null)) continue;
            matrixFilters.putIfAbsent(filter.genericKey(), filter);
        }
        if (matrixFilters.isEmpty()) return new MatrixResult(0, 0, 0);

        Map<String, ChampionBuildEngine.BuildAccumulator> builds = new LinkedHashMap<>();
        if (buildFilters != null) for (Filter filter : buildFilters) {
            if (filter == null || filter.champion() == 0 || filter.patch() == null || filter.queue() == null) continue;
            builds.putIfAbsent(filter.toKey(), ChampionBuildEngine.newAccumulator(filter));
        }

        Filter first = matrixFilters.values().iterator().next();
        Filter source = new Filter()
            .setChampion(0)
            .setLane(null)
            .setQueue(first.queue())
            .setRank(null)
            .setPatch(first.patch())
            .setRegion(null);

        MatrixMetrics metrics = new MatrixMetrics();
        RawMatrix raw = new RawMatrix();
        try {
        ChampionStatsProvider.forEachMatchWithBuild(source, (read, document) -> {
            ChampionStatsData.RawMatch rawMatch = read.match();
            try {
                for (ChampionBuildEngine.BuildAccumulator accumulator : builds.values())
                    for (var record : MongoDB.championBuildRecords(document, accumulator.filter()))
                        ChampionBuildEngine.accept(accumulator, record);
                long parseStarted = System.nanoTime();
                ChampionStatsData.Game game = parse(rawMatch);
                metrics.parseNanos += System.nanoTime() - parseStarted;
                metrics.baseScanNanos += read.matchReadNanos();
                if (game == null) return;
                long aggregationStarted = System.nanoTime();
                raw.addBase(game, rawMatch.metadata());
                metrics.baseAggregationNanos += System.nanoTime() - aggregationStarted;
            } finally {
                release(rawMatch);
            }
        }, read -> {
            ChampionStatsData.RawMatch rawMatch = read.match();
            try {
                long parseStarted = System.nanoTime();
                ChampionStatsData.Game game = parse(rawMatch);
                metrics.parseNanos += System.nanoTime() - parseStarted;
                metrics.eventScanNanos += read.eventReadNanos();
                if (game == null) return;
                long aggregationStarted = System.nanoTime();
                raw.addEvents(game, rawMatch.metadata());
                metrics.eventAggregationNanos += System.nanoTime() - aggregationStarted;
            } finally {
                release(rawMatch);
            }
        });

        long rollupStarted = System.nanoTime();
        Map<String, RawProjection> projections = new LinkedHashMap<>();
        for (Map.Entry<String, Filter> entry : matrixFilters.entrySet())
            projections.put(entry.getKey(), raw.project(entry.getValue()));
        metrics.rollupNanos = System.nanoTime() - rollupStarted;
        long trendStarted = System.nanoTime();
        Map<String, Map<Integer, Trend>> trends = loadMatrixTrends(matrixFilters, projections);
        metrics.trendNanos = System.nanoTime() - trendStarted;

        int emptyFilters = 0;
        int persistedChampions = 0;
        for (Map.Entry<String, Filter> entry : matrixFilters.entrySet()) {
            Filter filter = entry.getValue();
            RawProjection projection = projections.get(entry.getKey());
            long assembleStarted = System.nanoTime();
            Map<Integer, ChampionStatistics> statistics = assemble(projection,
                trends.getOrDefault(entry.getKey(), Map.of()));
            metrics.assembleNanos += System.nanoTime() - assembleStarted;
            if (statistics.isEmpty()) emptyFilters++;
            long writeStarted = System.nanoTime();
            if (!statistics.isEmpty()) {
                MongoDB.upsertChampionStatistics(filter, statistics);
                persistedChampions += statistics.size();
            }
            if (statistics.isEmpty()) MongoDB.upsertChampionStatistics(filter, Map.of());
            metrics.writeNanos += System.nanoTime() - writeStarted;
            statistics.clear();
            release(projection);
            trends.remove(entry.getKey());
        }
        for (ChampionBuildEngine.BuildAccumulator accumulator : builds.values()) {
            List<com.safjnest.lol.model.Build> result = ChampionBuildEngine.finish(accumulator);
            if (result.isEmpty()) result = ChampionBuildEngine.emptyResult(accumulator.filter());
            MongoDB.upsertChampionBuilds(result);
        }
        metrics.rawBuckets = raw.bucketCount();
        metrics.rawValues = raw.valueCount();
        metrics.peakBucketValues = raw.peakBucketValues();
        projections.clear();
        BotLogger.info(metrics.message(matrixFilters.size()));
        return new MatrixResult(matrixFilters.size(), emptyFilters, persistedChampions);
        } finally {
            raw.clear();
        }
    }

    static boolean matchesMatrixFilter(Filter filter, ChampionStatsData.RawMatch rawMatch) {
        if (filter == null || rawMatch == null || rawMatch.metadata() == null) return false;
        ChampionStatsData.MatchMeta metadata = rawMatch.metadata();
        if (filter.region() != null && filter.region() != metadata.region()) return false;
        if (filter.rank() == null) return true;
        if (metadata.rank() == null) return false;
        return filter.rankBehavior() == Filter.RankBehavior.EXACT
            ? metadata.rank() == filter.rank()
            : metadata.rank().ordinal() <= filter.rank().ordinal();
    }

    private static Map<Integer, ChampionStatistics> compute(Filter filter, boolean save) {
        RawMatrix raw = new RawMatrix();
        try {
        ChampionStatsProvider.forEachMatch(filter, read -> {
            ChampionStatsData.RawMatch rawMatch = read.match();
            try {
                ChampionStatsData.Game game = parse(rawMatch);
                if (game == null) return;
                raw.addBase(game, rawMatch.metadata());
            } finally {
                release(rawMatch);
            }
        }, read -> {
            ChampionStatsData.RawMatch rawMatch = read.match();
            try {
                ChampionStatsData.Game game = parse(rawMatch);
                if (game == null) return;
                raw.addEvents(game, rawMatch.metadata());
            } finally {
                release(rawMatch);
            }
        });
        RawProjection projection = raw.project(filter);
        String filterKey = filter.genericKey();
        Map<Integer, Trend> trends = loadMatrixTrends(Map.of(filterKey, filter),
            Map.of(filterKey, projection)).getOrDefault(filterKey, Map.of());
        Map<Integer, ChampionStatistics> stats = assemble(projection, trends);
        if (save && !stats.isEmpty()) {
            save(stats);
        }
        return stats;
        } finally {
            raw.clear();
        }
    }

    private static ChampionStatsData.Game parse(ChampionStatsData.RawMatch rawMatch) {
        if (rawMatch == null || rawMatch.metadata() == null || rawMatch.participants() == null
                || rawMatch.participants().isEmpty()) return null;

        ChampionStatsData.MatchMeta metadata = rawMatch.metadata();
        List<ChampionStatsData.Player> players = new ArrayList<>();
        for (ChampionStatsData.RawParticipant participant : rawMatch.participants()) {
            players.add(new ChampionStatsData.Player(participant.champion(), participant.lane(), participant.win(),
                participant.team(), participant.matchId(), metadata.timeStart(), metadata.timeEnd(), participant.kda(),
                participant.cs(), participant.gold(), participant.puuid()));
        }
        return new ChampionStatsData.Game(rawMatch.matchId(), metadata.bans(), metadata.timeStart(), metadata.timeEnd(),
            List.copyOf(players), parseMatchData(players, metadata.events()));
    }

    private static ChampionStatsData.MatchData parseMatchData(
            List<ChampionStatsData.Player> players,
            Object rawEvents) {
        JSONObject events = eventJson(rawEvents);
        if (events == null) return new ChampionStatsData.MatchData(Map.of(), Map.of(), false);

        JSONObject participantRefs = events.optJSONObject("participants");
        Map<String, ChampionStatsData.Player> byPuuid = new HashMap<>();
        for (ChampionStatsData.Player player : players)
            if (player.puuid() != null) byPuuid.put(player.puuid(), player);

        Map<String, EventCounter> counters = new HashMap<>();
        Map<TeamType, Integer> teamKills = new HashMap<>();
        JSONArray kills = events.optJSONArray("champion_kills");
        boolean available = kills != null;
        if (kills != null) {
            for (int i = 0; i < kills.length(); i++) {
                JSONObject kill = kills.optJSONObject(i);
                if (kill == null) continue;
                ChampionStatsData.Player killer = resolve(kill.opt("killer"), participantRefs, byPuuid);
                if (killer != null) {
                    EventCounter counter = counters.computeIfAbsent(killer.puuid(), ignored -> new EventCounter());
                    counter.kills++;
                    JSONArray assists = kill.optJSONArray("assists");
                    if (assists == null || assists.length() == 0) counter.soloKills++;
                    if (killer.team() != null) teamKills.merge(killer.team(), 1, Integer::sum);
                }
                ChampionStatsData.Player victim = resolve(kill.opt("victim"), participantRefs, byPuuid);
                if (victim != null) counters.computeIfAbsent(victim.puuid(), ignored -> new EventCounter()).deaths++;
                JSONArray assists = kill.optJSONArray("assists");
                if (assists != null) for (int j = 0; j < assists.length(); j++) {
                    ChampionStatsData.Player assister = resolve(assists.opt(j), participantRefs, byPuuid);
                    if (assister != null) counters.computeIfAbsent(assister.puuid(), ignored -> new EventCounter()).assists++;
                }
            }
        }

        Map<String, ChampionStatsData.EventMetric> metrics = new HashMap<>();
        for (ChampionStatsData.Player player : players) {
            if (player.puuid() == null || player.puuid().isBlank()) continue;
            EventCounter counter = counters.getOrDefault(player.puuid(), new EventCounter());
            int teamKillsForPlayer = player.team() == null ? 0 : teamKills.getOrDefault(player.team(), 0);
            metrics.put(player.puuid(), new ChampionStatsData.EventMetric(counter.kills, counter.soloKills,
                counter.assists, teamKillsForPlayer, counter.deaths, available));
        }

        Map<String, ChampionStatsData.Snapshot> snapshots = new HashMap<>();
        JSONArray snapshotArray = events.optJSONArray("snapshots");
        if (snapshotArray != null && participantRefs != null) {
            JSONObject nearestSnapshot = null;
            long nearestTimestamp = -1;
            long nearestDistance = Long.MAX_VALUE;
            for (int i = 0; i < snapshotArray.length(); i++) {
                JSONObject snapshot = snapshotArray.optJSONObject(i);
                if (snapshot == null) continue;
                long timestamp = snapshot.optLong("timestamp", -1);
                if (timestamp < 0) continue;
                long distance = Math.abs(timestamp - AT_15_MS);
                if (distance < nearestDistance
                        || distance == nearestDistance && (nearestTimestamp < 0 || timestamp < nearestTimestamp)) {
                    nearestSnapshot = snapshot;
                    nearestTimestamp = timestamp;
                    nearestDistance = distance;
                }
            }
            if (nearestSnapshot != null) {
                JSONObject participants = nearestSnapshot.optJSONObject("participants");
                if (participants != null) for (String participantId : participants.keySet()) {
                    String puuid = participantRefs.optString(participantId, null);
                    JSONObject values = participants.optJSONObject(participantId);
                    if (puuid != null && values != null)
                        snapshots.put(puuid, new ChampionStatsData.Snapshot(nullableInt(values, "cs"),
                            nullableInt(values, "total_gold")));
                }
            }
        }
        return new ChampionStatsData.MatchData(metrics, snapshots, available);
    }

    private static JSONObject eventJson(Object rawEvents) {
        if (rawEvents instanceof String json && !json.isBlank()) return new JSONObject(json);
        if (rawEvents instanceof Map<?, ?> map && !map.isEmpty()) return new JSONObject(map);
        return null;
    }

    private static List<LaneStat> laneOptions(Map<Integer, Map<LaneType, int[]>> values, int champion) {
        List<LaneStat> result = new ArrayList<>();
        for (Map.Entry<LaneType, int[]> entry : values.getOrDefault(champion, Map.of()).entrySet())
            result.add(new LaneStat(entry.getKey(), entry.getValue()[0], rate(entry.getValue()[1], entry.getValue()[0])));
        result.sort(Comparator.comparingInt(LaneStat::games).reversed());
        return result;
    }

    private static Map<MatchupKey, Matchup> matchupOptions(
            Map<Integer, Map<MatchupKey, double[]>> values, int champion, double championWinrate,
            Map<Integer, int[]> banCount, int banGames) {
        Map<MatchupKey, Matchup> result = new LinkedHashMap<>();
        for (Map.Entry<MatchupKey, double[]> entry : values.getOrDefault(champion, Map.of()).entrySet()) {
            MatchupKey key = entry.getKey();
            double[] value = entry.getValue();
            int matches = (int) value[MATCHES];
            int wins = (int) value[WINS];
            int opponentBans = banCount.getOrDefault(key.champion(), new int[1])[0];
            double matchupWinrate = rate(wins, matches);
            result.put(key, new Matchup(key.champion(), key.lane(), matches, wins, matchupWinrate,
                matchupWinrate - championWinrate,
                value[GOLD_DIFF_GAMES] > 0 ? (int) Math.round(value[GOLD_DIFF_SUM] / value[GOLD_DIFF_GAMES]) : null,
                value[CS_DIFF_GAMES] > 0 ? value[CS_DIFF_SUM] / value[CS_DIFF_GAMES] : null,
                soloKillRate(value), killParticipation(value),
                banGames > 0 ? (double) opponentBans / banGames : null,
                (int) value[METRIC_GAMES]));
        }
        return result;
    }

    private static List<LaneSynergy> synergyOptions(
            Map<Integer, Map<ChampionStatsData.SynergyKey, int[]>> values, int champion, int picks) {
        List<LaneSynergy> result = new ArrayList<>();
        for (Map.Entry<ChampionStatsData.SynergyKey, int[]> entry
                : values.getOrDefault(champion, Map.of()).entrySet()) {
            int matches = entry.getValue()[0];
            int wins = entry.getValue()[1];
            result.add(new LaneSynergy(entry.getKey().champion(), entry.getKey().lane(), matches, wins,
                rate(wins, matches), rate(matches, picks)));
        }
        result.sort(Comparator.comparingInt(LaneSynergy::allyChampion)
            .thenComparing(synergy -> String.valueOf(synergy.allyLane())));
        return result;
    }

    private static ChampionStatsData.MetricValues metricOptions(Map<Integer, double[]> values, int champion) {
        double[] value = values.get(champion);
        if (value == null) return new ChampionStatsData.MetricValues(null, null, null);
        Double kda = value[KDA_GAMES] == 0 && value[EVENT_GAMES] == 0 ? null
            : value[KDA_DEATHS] > 0 ? (value[KDA_KILLS] + value[KDA_ASSISTS]) / value[KDA_DEATHS]
            : value[KDA_KILLS] + value[KDA_ASSISTS];
        Double csPerMinute = value[CS_PER_MINUTE_GAMES] > 0
            ? value[CS_PER_MINUTE_SUM] / value[CS_PER_MINUTE_GAMES] : null;
        Double goldPerMinute = value[GOLD_PER_MINUTE_GAMES] > 0
            ? value[GOLD_PER_MINUTE_SUM] / value[GOLD_PER_MINUTE_GAMES] : null;
        return new ChampionStatsData.MetricValues(kda, csPerMinute, goldPerMinute);
    }

    private static List<PowerCurvePoint> powerCurveOptions(
            Map<Integer, Map<String, int[]>> values, int champion) {
        List<PowerCurvePoint> result = new ArrayList<>();
        Map<String, int[]> championValues = values.getOrDefault(champion, Map.of());
        for (String bucket : POWER_BUCKETS) {
            int[] stats = championValues.get(bucket);
            if (stats != null) result.add(new PowerCurvePoint(bucket, stats[0], stats[1], rate(stats[1], stats[0])));
        }
        return result;
    }

    private static Map<Integer, ChampionStatistics> assemble(
            Filter filter, int totalGames, int banGames, Map<Integer, int[]> pickWin,
            Map<Integer, int[]> banCount, Map<Integer, List<LaneStat>> laneStats,
            Map<Integer, Map<MatchupKey, Matchup>> matchups,
            Map<Integer, List<LaneSynergy>> synergies,
            Map<Integer, ChampionStatsData.MetricValues> metrics,
            Map<Integer, List<PowerCurvePoint>> powerCurve,
            Map<Integer, Trend> trends) {
        Map<Integer, ChampionStatistics> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, int[]> entry : pickWin.entrySet()) {
            int champion = entry.getKey();
            int picks = entry.getValue()[0];
            int wins = entry.getValue()[1];
            int bans = banCount.getOrDefault(champion, new int[1])[0];
            double winrate = rate(wins, picks);
            ChampionStatsData.MetricValues metric = metrics.getOrDefault(champion,
                new ChampionStatsData.MetricValues(null, null, null));
            Filter championFilter = new Filter().setChampion(champion).setLane(filter.lane()).setPatch(filter.patch())
                .setQueue(filter.queue()).setRank(filter.rank()).setRegion(filter.region());
            result.put(champion, new ChampionStatistics(
                championFilter,
                new Overview(totalGames, picks, bans, wins, winrate, rate(picks, totalGames),
                    banGames > 0 ? (double) bans / banGames : null,
                    metric.kda(), metric.csPerMinute(), metric.goldPerMinute(), null),
                laneStats.getOrDefault(champion, List.of()),
                matchups.getOrDefault(champion, Map.of()),
                synergies.getOrDefault(champion, List.of()),
                powerCurve.getOrDefault(champion, List.of()),
                trends.get(champion)
            ));
        }
        return result;
    }

    private static void release(ChampionStatsData.RawMatch rawMatch) {
        if (rawMatch == null) return;
        if (rawMatch.participants() != null) rawMatch.participants().clear();
        if (rawMatch.metadata() != null && rawMatch.metadata().bans() != null
                && !rawMatch.metadata().bans().isEmpty()) rawMatch.metadata().bans().clear();
    }

    private static void release(RawProjection projection) {
        projection.pickWin().clear();
        projection.banCount().clear();
        projection.laneStats().clear();
        projection.matchups().clear();
        projection.synergies().clear();
        projection.metrics().clear();
        projection.powerCurve().clear();
    }

    private static Map<String, Map<Integer, Trend>> loadMatrixTrends(
            Map<String, Filter> filters, Map<String, RawProjection> current) {
        Map<String, Map<Integer, Trend>> result = new LinkedHashMap<>();
        if (filters.isEmpty()) return result;

        Map<String, Filter> previousFilters = new LinkedHashMap<>();
        for (Map.Entry<String, Filter> entry : filters.entrySet()) {
            Filter previous = previousFilter(entry.getValue());
            if (previous == null) return result;
            previousFilters.put(entry.getKey(), previous);
        }
        Map<String, Map<Integer, ChampionStatistics>> stored = MongoDB.findChampionStatistics(
            new ArrayList<>(previousFilters.values()));
        boolean complete = true;
        for (Map.Entry<String, Filter> entry : previousFilters.entrySet()) {
            RawProjection projection = current.get(entry.getKey());
            Map<Integer, int[]> values = storedPickWin(stored.get(entry.getValue().genericKey()));
            if (projection == null || values == null || !values.keySet().containsAll(projection.pickWin().keySet())) {
                complete = false;
                break;
            }
            result.put(entry.getKey(), trendOptions(filters.get(entry.getKey()), entry.getValue(),
                projection.pickWin(), values));
        }
        if (complete) return result;
        result.clear();

        Filter source = previousFilter(new Filter()
            .setPatch(filters.values().iterator().next().patch())
            .setQueue(filters.values().iterator().next().queue())
            .setRank(null)
            .setRegion(null)
            .setLane(null));
        if (source == null) return result;

        RawMatrix previous = new RawMatrix();
        try {
            ChampionStatsProvider.forEachBaseMatch(source, read -> {
                ChampionStatsData.RawMatch rawMatch = read.match();
                try {
                    ChampionStatsData.Game game = parse(rawMatch);
                    if (game != null) previous.addBase(game, rawMatch.metadata());
                } finally {
                    release(rawMatch);
                }
            });
            for (Map.Entry<String, Filter> entry : filters.entrySet()) {
                Filter previousFilter = previousFilters.get(entry.getKey());
                RawProjection projection = current.get(entry.getKey());
                if (previousFilter == null || projection == null) {
                    result.put(entry.getKey(), Map.of());
                    continue;
                }
                Map<Integer, int[]> values = previous.project(previousFilter).pickWin();
                result.put(entry.getKey(), trendOptions(entry.getValue(), previousFilter,
                    projection.pickWin(), values));
            }
            return result;
        } finally {
            previous.clear();
        }
    }

    private static Map<Integer, int[]> storedPickWin(Map<Integer, ChampionStatistics> statistics) {
        if (statistics == null) return null;
        Map<Integer, int[]> result = new HashMap<>();
        for (Map.Entry<Integer, ChampionStatistics> entry : statistics.entrySet()) {
            ChampionStatistics.Overview overview = entry.getValue() == null ? null : entry.getValue().overview();
            if (overview != null) result.put(entry.getKey(), new int[]{overview.picks(), overview.wins()});
        }
        return result;
    }

    private static Filter previousFilter(Filter filter) {
        if (filter == null || filter.patch() == null) return null;
        try {
            List<String> patches = PatchUtils.getPatches();
            int currentIndex = patches.indexOf(filter.patch());
            if (currentIndex < 0 || currentIndex + 1 >= patches.size()) return null;
            String previousPatch = patches.get(currentIndex + 1);
            if (previousPatch == null || previousPatch.isBlank()) return null;
            return new Filter().setPatch(previousPatch).setQueue(filter.queue()).setRank(filter.rank())
                .setRegion(filter.region()).setLane(filter.lane());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Map<Integer, Trend> trendOptions(Filter currentFilter, Filter previousFilter,
                                                     Map<Integer, int[]> current,
                                                     Map<Integer, int[]> previous) {
        if (currentFilter == null || previousFilter == null || previousFilter.patch() == null
            || previous == null || previous.isEmpty()) return Map.of();
        Map<Integer, Trend> result = new HashMap<>();
        for (Integer champion : current.keySet()) {
            int[] values = previous.get(champion);
            if (values == null || values[0] == 0) continue;
            double previousWinrate = rate(values[1], values[0]);
            double currentWinrate = rate(current.get(champion)[1], current.get(champion)[0]);
            result.put(champion, new Trend(previousFilter.patch(), values[0], previousWinrate,
                currentWinrate - previousWinrate));
        }
        return result;
    }

    private static void save(Map<Integer, ChampionStatistics> stats) {
        ChronoTask saveTask = () -> MongoDB.upsertChampionStatistics(stats);
        saveTask.queue();
    }

    private static Map<TeamType, List<ChampionStatsData.Player>> byTeam(
            List<ChampionStatsData.Player> players) {
        Map<TeamType, List<ChampionStatsData.Player>> result = new HashMap<>();
        for (ChampionStatsData.Player player : players)
            result.computeIfAbsent(player.team(), ignored -> new ArrayList<>()).add(player);
        return result;
    }

    private static boolean compatible(LaneType primary, LaneType ally) {
        return (primary == LaneType.BOT && ally == LaneType.UTILITY)
            || (primary == LaneType.UTILITY && ally == LaneType.BOT);
    }

    private static Double soloKillRate(double[] value) {
        if (value[KILLS] > 0) return value[SOLO_KILLS] / value[KILLS];
        if (value[METRIC_GAMES] > 0) return 0d;
        return null;
    }

    private static Double killParticipation(double[] value) {
        return value[KILL_PARTICIPATION_GAMES] > 0
            ? value[KILL_PARTICIPATION_SUM] / value[KILL_PARTICIPATION_GAMES] : null;
    }

    private static double durationMinutes(ChampionStatsData.Player player) {
        long duration = player.timeEnd() - player.timeStart();
        return duration > 0 ? duration / 60000d : 0;
    }

    private static int[] parseKda(String raw) {
        String[] values = raw.split("/");
        if (values.length != 3) return new int[3];
        return new int[]{integer(values[0]), integer(values[1]), integer(values[2])};
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (Exception ignored) { return 0; }
    }

    private static ChampionStatsData.Player resolve(Object value, JSONObject refs,
                                                    Map<String, ChampionStatsData.Player> byPuuid) {
        if (value == null || value == JSONObject.NULL) return null;
        String key = String.valueOf(value);
        String puuid = refs == null ? key : refs.optString(key, key);
        return byPuuid.get(puuid);
    }

    private static Integer nullableInt(JSONObject object, String key) {
        return object.has(key) && !object.isNull(key) ? object.optInt(key) : null;
    }

    private static double rate(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0;
    }

    private static Map<Integer, ChampionStatistics> assemble(RawProjection projection, Map<Integer, Trend> trends) {
        return assemble(projection.filter(), projection.totalGames(), projection.banGames(), projection.pickWin(),
            projection.banCount(), projection.laneStats(), projection.matchups(), projection.synergies(),
            projection.metrics(), projection.powerCurve(), trends);
    }

    record RawProjection(
        Filter filter,
        int totalGames,
        int banGames,
        Map<Integer, int[]> pickWin,
        Map<Integer, int[]> banCount,
        Map<Integer, List<LaneStat>> laneStats,
        Map<Integer, Map<MatchupKey, Matchup>> matchups,
        Map<Integer, List<LaneSynergy>> synergies,
        Map<Integer, ChampionStatsData.MetricValues> metrics,
        Map<Integer, List<PowerCurvePoint>> powerCurve
    ) {}

    private static final class MatrixMetrics {
        private long baseScanNanos;
        private long eventScanNanos;
        private long parseNanos;
        private long baseAggregationNanos;
        private long eventAggregationNanos;
        private long rollupNanos;
        private long trendNanos;
        private long assembleNanos;
        private long writeNanos;
        private int rawBuckets;
        private int rawValues;
        private int peakBucketValues;

        private String message(int filters) {
            return "[ChampionStats] filters=" + filters
                + " scanBaseMs=" + milliseconds(baseScanNanos)
                + " scanEventsMs=" + milliseconds(eventScanNanos)
                + " parseMs=" + milliseconds(parseNanos)
                + " aggregateBaseMs=" + milliseconds(baseAggregationNanos)
                + " aggregateEventsMs=" + milliseconds(eventAggregationNanos)
                + " rollupMs=" + milliseconds(rollupNanos)
                + " trendMs=" + milliseconds(trendNanos)
                + " assembleMs=" + milliseconds(assembleNanos)
                + " writeMs=" + milliseconds(writeNanos)
                + " rawBuckets=" + rawBuckets
                + " rawValues=" + rawValues
                + " peakBucketValues=" + peakBucketValues;
        }

        private static long milliseconds(long nanos) {
            return nanos / 1_000_000L;
        }
    }

    static final class RawMatrix {

        private static final int LANE_BITS = 4;
        private static final int POWER_BITS = 3;
        private static final int CHAMPION_BITS = 24;
        private static final int CHAMPION_MASK = (1 << CHAMPION_BITS) - 1;

        private final Int2ObjectOpenHashMap<RawBucket> buckets = new Int2ObjectOpenHashMap<>();
        private final Int2IntOpenHashMap championIndexes = new Int2IntOpenHashMap();
        private final IntArrayList championIds = new IntArrayList();
        private int sequence;
        private int peakBucketValues;

        RawMatrix() {
            championIndexes.defaultReturnValue(0);
            championIds.add(0);
        }

        void addBase(ChampionStatsData.Game game, ChampionStatsData.MatchMeta metadata) {
            RawBucket bucket = bucket(metadata);
            bucket.totalGames++;
            if (game.bans() != null && !game.bans().isEmpty()) bucket.banGames++;
            addBans(bucket, game.bans());

            for (ChampionStatsData.Player player : game.players()) {
                long key = playerKey(index(player.champion()), laneCode(player.lane()));
                bucket.markPlayer(key, sequence++);
                int[] pickWin = ints(bucket.pickWin, key, 2);
                pickWin[0]++;
                if (player.win()) pickWin[1]++;
                addMetrics(bucket, key, player, false, game.data());
                addPowerCurve(bucket, key, player);
            }

            Map<TeamType, List<ChampionStatsData.Player>> teams = byTeam(game.players());
            List<List<ChampionStatsData.Player>> sides = new ArrayList<>(teams.values());
            if (sides.size() == 2) {
                addMatchups(bucket, sides.get(0), sides.get(1), game.data(), false);
                addMatchups(bucket, sides.get(1), sides.get(0), game.data(), false);
            }
            addSynergies(bucket, game.players(), teams);
            recordBucketPeak(bucket);
        }

        void addEvents(ChampionStatsData.Game game, ChampionStatsData.MatchMeta metadata) {
            RawBucket bucket = bucket(metadata);
            for (ChampionStatsData.Player player : game.players()) {
                long key = playerKey(index(player.champion()), laneCode(player.lane()));
                addMetrics(bucket, key, player, true, game.data());
            }
            Map<TeamType, List<ChampionStatsData.Player>> teams = byTeam(game.players());
            List<List<ChampionStatsData.Player>> sides = new ArrayList<>(teams.values());
            if (sides.size() == 2) {
                addMatchups(bucket, sides.get(0), sides.get(1), game.data(), true);
                addMatchups(bucket, sides.get(1), sides.get(0), game.data(), true);
            }
            recordBucketPeak(bucket);
        }

        RawProjection project(Filter filter) {
            RawBucket rollup = new RawBucket(null, null);
            for (RawBucket bucket : buckets.values()) if (matches(filter, bucket)) rollup.merge(bucket);

            Map<Integer, int[]> pickWin = new LinkedHashMap<>();
            Map<Integer, Map<LaneType, int[]>> laneAccum = new HashMap<>();
            Map<Integer, Map<MatchupKey, double[]>> matchupAccum = new LinkedHashMap<>();
            Map<Integer, Map<ChampionStatsData.SynergyKey, int[]>> synergyAccum = new HashMap<>();
            Map<Integer, double[]> metricAccum = new HashMap<>();
            Map<Integer, Map<String, int[]>> powerCurveAccum = new HashMap<>();

            for (long key : orderedKeys(rollup, filter.lane())) {
                int champion = champion(key);
                int[] values = rollup.pickWin.get(key);
                merge(pickWin, champion, values, 2);
                merge(laneAccum.computeIfAbsent(champion, ignored -> new HashMap<>()), lane(key), values, 2);
            }
            for (var entry : rollup.metrics.long2ObjectEntrySet()) {
                long key = entry.getLongKey();
                if (!matchesLane(key, filter.lane())) continue;
                merge(metricAccum, champion(key), entry.getValue(), METRIC_VALUE_SIZE);
            }
            for (var entry : rollup.powerCurve.long2ObjectEntrySet()) {
                long playerKey = entry.getLongKey() >>> POWER_BITS;
                if (!matchesLane(playerKey, filter.lane())) continue;
                String duration = POWER_BUCKETS.get((int) (entry.getLongKey() & ((1 << POWER_BITS) - 1)));
                merge(powerCurveAccum.computeIfAbsent(champion(playerKey), ignored -> new LinkedHashMap<>()),
                    duration, entry.getValue(), 2);
            }
            for (long key : orderedMatchupKeys(rollup, filter.lane())) {
                if (!matchesLane(key, filter.lane())) continue;
                int champion = matchupChampion(key);
                MatchupKey matchup = new MatchupKey(matchupOpponent(key), lane(key));
                merge(matchupAccum.computeIfAbsent(champion, ignored -> new LinkedHashMap<>()), matchup,
                    rollup.matchups.get(key), MATCHUP_VALUE_SIZE);
            }
            for (var entry : rollup.synergies.long2ObjectEntrySet()) {
                long key = entry.getLongKey();
                if (!matchesSynergyLane(key, filter.lane())) continue;
                int champion = synergyChampion(key);
                ChampionStatsData.SynergyKey synergy = new ChampionStatsData.SynergyKey(
                    synergyAlly(key), synergyAllyLane(key));
                merge(synergyAccum.computeIfAbsent(champion, ignored -> new HashMap<>()), synergy,
                    entry.getValue(), 2);
            }

            Map<Integer, List<LaneStat>> laneStats = new LinkedHashMap<>();
            Map<Integer, Map<MatchupKey, Matchup>> matchups = new LinkedHashMap<>();
            Map<Integer, List<LaneSynergy>> synergies = new LinkedHashMap<>();
            Map<Integer, ChampionStatsData.MetricValues> metrics = new LinkedHashMap<>();
            Map<Integer, List<PowerCurvePoint>> powerCurve = new LinkedHashMap<>();
            for (Map.Entry<Integer, int[]> entry : pickWin.entrySet()) {
                int champion = entry.getKey();
                int picks = entry.getValue()[0];
                double winrate = rate(entry.getValue()[1], picks);
                laneStats.put(champion, laneOptions(laneAccum, champion));
                matchups.put(champion, matchupOptions(matchupAccum, champion, winrate, rollup.banCount, rollup.banGames));
                synergies.put(champion, synergyOptions(synergyAccum, champion, picks));
                metrics.put(champion, metricOptions(metricAccum, champion));
                powerCurve.put(champion, powerCurveOptions(powerCurveAccum, champion));
            }
            return new RawProjection(filter, rollup.totalGames, rollup.banGames, pickWin,
                toJavaMap(rollup.banCount), laneStats, matchups, synergies, metrics, powerCurve);
        }

        private void addMatchups(RawBucket bucket, List<ChampionStatsData.Player> team,
                                 List<ChampionStatsData.Player> enemies, ChampionStatsData.MatchData data,
                                 boolean events) {
            for (ChampionStatsData.Player player : team) {
                if (player.lane() == null) continue;
                int playerIndex = index(player.champion());
                int playerLane = laneCode(player.lane());
                for (ChampionStatsData.Player opponent : enemies) {
                    if (opponent.lane() != player.lane() || opponent.champion() == player.champion()) continue;
                    long key = matchupKey(playerIndex, index(opponent.champion()), playerLane);
                    double[] value = doubles(bucket.matchups, key, MATCHUP_VALUE_SIZE);
                    if (!events) {
                        bucket.markMatchup(key, sequence++);
                        value[MATCHES]++;
                        if (player.win()) value[WINS]++;
                        continue;
                    }
                    ChampionStatsData.Snapshot playerSnapshot = data.snapshots().get(player.puuid());
                    ChampionStatsData.Snapshot opponentSnapshot = data.snapshots().get(opponent.puuid());
                    if (playerSnapshot != null && opponentSnapshot != null) {
                        if (playerSnapshot.gold() != null && opponentSnapshot.gold() != null) {
                            value[GOLD_DIFF_SUM] += playerSnapshot.gold() - opponentSnapshot.gold();
                            value[GOLD_DIFF_GAMES]++;
                        }
                        if (playerSnapshot.cs() != null && opponentSnapshot.cs() != null) {
                            value[CS_DIFF_SUM] += playerSnapshot.cs() - opponentSnapshot.cs();
                            value[CS_DIFF_GAMES]++;
                        }
                    }
                    ChampionStatsData.EventMetric metric = data.eventMetrics().get(player.puuid());
                    if (metric == null || !metric.available()) continue;
                    value[METRIC_GAMES]++;
                    value[SOLO_KILLS] += metric.soloKills();
                    value[KILLS] += metric.kills();
                    if (metric.teamKills() > 0) {
                        value[KILL_PARTICIPATION_SUM] += (double) (metric.kills() + metric.assists()) / metric.teamKills();
                        value[KILL_PARTICIPATION_GAMES]++;
                    }
                }
            }
        }

        private void addSynergies(RawBucket bucket, List<ChampionStatsData.Player> players,
                                  Map<TeamType, List<ChampionStatsData.Player>> teams) {
            for (ChampionStatsData.Player player : players) {
                int playerIndex = index(player.champion());
                int playerLane = laneCode(player.lane());
                for (ChampionStatsData.Player ally : teams.getOrDefault(player.team(), List.of())) {
                    if (ally == player || !compatible(player.lane(), ally.lane()) || player.champion() == ally.champion()) continue;
                    long key = synergyKey(playerIndex, playerLane, index(ally.champion()), laneCode(ally.lane()));
                    int[] value = ints(bucket.synergies, key, 2);
                    value[0]++;
                    if (player.win()) value[1]++;
                }
            }
        }

        private void addMetrics(RawBucket bucket, long key, ChampionStatsData.Player player,
                                boolean events, ChampionStatsData.MatchData data) {
            double[] value = doubles(bucket.metrics, key, METRIC_VALUE_SIZE);
            if (!events) {
                if (player.kda() != null) {
                    int[] kda = parseKda(player.kda());
                    value[KDA_KILLS] += kda[0]; value[KDA_DEATHS] += kda[1]; value[KDA_ASSISTS] += kda[2]; value[KDA_GAMES]++;
                }
                double minutes = durationMinutes(player);
                if (player.cs() != null && minutes != 0) { value[CS_PER_MINUTE_SUM] += player.cs() / minutes; value[CS_PER_MINUTE_GAMES]++; }
                if (player.gold() != null && minutes != 0) { value[GOLD_PER_MINUTE_SUM] += player.gold() / minutes; value[GOLD_PER_MINUTE_GAMES]++; }
                return;
            }
            ChampionStatsData.EventMetric metric = data.eventMetrics().get(player.puuid());
            if (metric != null && metric.available()) value[EVENT_GAMES]++;
        }

        private void addPowerCurve(RawBucket bucket, long key, ChampionStatsData.Player player) {
            long duration = player.timeEnd() - player.timeStart();
            if (duration <= 0) return;
            long minutes = duration / 60000;
            int power = minutes <= 15 ? 0 : minutes <= 25 ? 1 : minutes <= 35 ? 2 : minutes <= 45 ? 3 : 4;
            int[] value = ints(bucket.powerCurve, (key << POWER_BITS) | power, 2);
            value[0]++;
            if (player.win()) value[1]++;
        }

        private void addBans(RawBucket bucket, Map<String, Object> bans) {
            if (bans == null) return;
            for (Object value : bans.values()) {
                if (!(value instanceof List<?> champions)) continue;
                for (Object item : champions) if (item instanceof Number number && number.intValue() != 0)
                    bucket.banCount.addTo(number.intValue(), 1);
            }
        }

        private RawBucket bucket(ChampionStatsData.MatchMeta metadata) {
            int key = regionCode(metadata.region()) << 8 | rankCode(metadata.rank());
            RawBucket result = buckets.get(key);
            if (result == null) {
                result = new RawBucket(metadata.region(), metadata.rank());
                buckets.put(key, result);
            }
            return result;
        }

        private int bucketCount() {
            return buckets.size();
        }

        private int valueCount() {
            int result = 0;
            for (RawBucket bucket : buckets.values()) result += bucket.valueCount();
            return result;
        }

        private int peakBucketValues() {
            return peakBucketValues;
        }

        private void recordBucketPeak(RawBucket bucket) {
            peakBucketValues = Math.max(peakBucketValues, bucket.valueCount());
        }

        private boolean matches(Filter filter, RawBucket bucket) {
            if (filter.region() != null && filter.region() != bucket.region) return false;
            if (filter.rank() == null) return true;
            if (bucket.rank == null) return false;
            return filter.rankBehavior() == Filter.RankBehavior.EXACT
                ? bucket.rank == filter.rank()
                : bucket.rank.ordinal() <= filter.rank().ordinal();
        }

        private int index(int champion) {
            int index = championIndexes.get(champion);
            if (index != 0) return index;
            index = championIds.size();
            if (index > CHAMPION_MASK) throw new IllegalStateException("Champion index exceeds long key capacity");
            championIndexes.put(champion, index);
            championIds.add(champion);
            return index;
        }

        private List<Long> orderedKeys(RawBucket bucket, LaneType lane) {
            List<Long> keys = new ArrayList<>();
            for (long value : bucket.pickWin.keySet()) {
                if (matchesLane(value, lane)) keys.add(value);
            }
            keys.sort(Comparator.comparingInt(value -> bucket.playerOrder.get(value.longValue())));
            return keys;
        }

        private List<Long> orderedMatchupKeys(RawBucket bucket, LaneType lane) {
            List<Long> keys = new ArrayList<>();
            for (long key : bucket.matchups.keySet()) if (matchesLane(key, lane)) keys.add(key);
            keys.sort(Comparator.comparingInt(value -> bucket.matchupOrder.get(value.longValue())));
            return keys;
        }

        private boolean matchesLane(long key, LaneType lane) {
            return lane == null || laneCode(lane) == (int) (key & ((1 << LANE_BITS) - 1));
        }

        private boolean matchesSynergyLane(long key, LaneType lane) {
            return lane == null || laneCode(lane) == (int) ((key >>> LANE_BITS) & ((1 << LANE_BITS) - 1));
        }

        private int champion(long playerKey) { return championIds.getInt((int) (playerKey >>> LANE_BITS)); }
        private LaneType lane(long playerKey) { return lane((int) (playerKey & ((1 << LANE_BITS) - 1))); }
        private int matchupChampion(long key) { return championIds.getInt((int) (key >>> 28)); }
        private int matchupOpponent(long key) { return championIds.getInt((int) ((key >>> LANE_BITS) & CHAMPION_MASK)); }
        private int synergyChampion(long key) { return championIds.getInt((int) (key >>> 32)); }
        private int synergyAlly(long key) { return championIds.getInt((int) ((key >>> 8) & CHAMPION_MASK)); }
        private LaneType synergyAllyLane(long key) { return lane((int) (key & ((1 << LANE_BITS) - 1))); }

        private static long playerKey(int champion, int lane) { return ((long) champion << LANE_BITS) | lane; }
        private static long matchupKey(int champion, int opponent, int lane) { return ((long) champion << 28) | ((long) opponent << LANE_BITS) | lane; }
        private static long synergyKey(int champion, int lane, int ally, int allyLane) {
            return ((long) champion << 32) | ((long) ally << 8) | ((long) lane << LANE_BITS) | allyLane;
        }

        private static int[] ints(Long2ObjectOpenHashMap<int[]> values, long key, int size) {
            int[] result = values.get(key);
            if (result == null) { result = new int[size]; values.put(key, result); }
            return result;
        }

        private static double[] doubles(Long2ObjectOpenHashMap<double[]> values, long key, int size) {
            double[] result = values.get(key);
            if (result == null) { result = new double[size]; values.put(key, result); }
            return result;
        }

        private static <K> void merge(Map<K, int[]> values, K key, int[] source, int size) {
            int[] target = values.computeIfAbsent(key, ignored -> new int[size]);
            for (int index = 0; index < size; index++) target[index] += source[index];
        }

        private static <K> void merge(Map<K, double[]> values, K key, double[] source, int size) {
            double[] target = values.computeIfAbsent(key, ignored -> new double[size]);
            for (int index = 0; index < size; index++) target[index] += source[index];
        }

        private static Map<Integer, int[]> toJavaMap(Int2IntOpenHashMap values) {
            Map<Integer, int[]> result = new HashMap<>();
            for (var entry : values.int2IntEntrySet()) result.put(entry.getIntKey(), new int[]{entry.getIntValue()});
            return result;
        }

        private static int regionCode(no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard region) {
            if (region == null) return 0;
            return switch (region.name()) {
                case "EUW1" -> 1; case "NA1" -> 2; case "KR" -> 3; case "EUN1" -> 4; case "JP1" -> 5;
                case "BR1" -> 6; case "LA1" -> 7; case "LA2" -> 8; case "TR1" -> 9; case "RU" -> 10;
                case "OC1" -> 11; case "VN2" -> 12; case "SG2" -> 13; case "TW2" -> 14; case "ME1" -> 15;
                default -> 16;
            };
        }

        private static int rankCode(no.stelar7.api.r4j.basic.constants.types.lol.TierType rank) {
            if (rank == null) return 0;
            return switch (rank.name()) {
                case "CHALLENGER" -> 1; case "GRANDMASTER" -> 2; case "MASTER" -> 3; case "DIAMOND" -> 4;
                case "EMERALD" -> 5; case "PLATINUM" -> 6; case "GOLD" -> 7; case "SILVER" -> 8;
                case "BRONZE" -> 9; case "IRON" -> 10; default -> 11;
            };
        }

        private static int laneCode(LaneType lane) {
            if (lane == null) return 0;
            return switch (lane.name()) {
                case "TOP" -> 1; case "JUNGLE" -> 2; case "MID" -> 3; case "BOT" -> 4; case "UTILITY" -> 5;
                case "NONE" -> 6; default -> 7;
            };
        }

        private static LaneType lane(int code) {
            return switch (code) {
                case 1 -> LaneType.TOP; case 2 -> LaneType.JUNGLE; case 3 -> LaneType.MID;
                case 4 -> LaneType.BOT; case 5 -> LaneType.UTILITY; case 6 -> LaneType.NONE;
                default -> null;
            };
        }

        private void clear() {
            for (RawBucket bucket : buckets.values()) bucket.clear();
            buckets.clear(); championIndexes.clear(); championIds.clear();
        }
    }

    private static final class RawBucket {
        private final no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard region;
        private final no.stelar7.api.r4j.basic.constants.types.lol.TierType rank;
        private int totalGames;
        private int banGames;
        private final Int2IntOpenHashMap banCount = new Int2IntOpenHashMap();
        private final Long2ObjectOpenHashMap<int[]> pickWin = new Long2ObjectOpenHashMap<>();
        private final Long2IntOpenHashMap playerOrder = new Long2IntOpenHashMap<>();
        private final Long2IntOpenHashMap matchupOrder = new Long2IntOpenHashMap<>();
        private final Long2ObjectOpenHashMap<double[]> metrics = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<int[]> powerCurve = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<double[]> matchups = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectOpenHashMap<int[]> synergies = new Long2ObjectOpenHashMap<>();

        private RawBucket(no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard region,
                          no.stelar7.api.r4j.basic.constants.types.lol.TierType rank) {
            this.region = region;
            this.rank = rank;
            playerOrder.defaultReturnValue(Integer.MAX_VALUE);
            matchupOrder.defaultReturnValue(Integer.MAX_VALUE);
        }

        private void merge(RawBucket source) {
            totalGames += source.totalGames;
            banGames += source.banGames;
            for (var entry : source.banCount.int2IntEntrySet()) banCount.addTo(entry.getIntKey(), entry.getIntValue());
            for (var entry : source.playerOrder.long2IntEntrySet()) {
                int order = playerOrder.get(entry.getLongKey());
                if (entry.getIntValue() < order) playerOrder.put(entry.getLongKey(), entry.getIntValue());
            }
            for (var entry : source.matchupOrder.long2IntEntrySet()) {
                int order = matchupOrder.get(entry.getLongKey());
                if (entry.getIntValue() < order) matchupOrder.put(entry.getLongKey(), entry.getIntValue());
            }
            mergeInts(pickWin, source.pickWin); mergeDoubles(metrics, source.metrics); mergeInts(powerCurve, source.powerCurve);
            mergeDoubles(matchups, source.matchups); mergeInts(synergies, source.synergies);
        }

        private void markPlayer(long key, int order) {
            if (!playerOrder.containsKey(key)) playerOrder.put(key, order);
        }

        private void markMatchup(long key, int order) {
            if (!matchupOrder.containsKey(key)) matchupOrder.put(key, order);
        }

        private static void mergeInts(Long2ObjectOpenHashMap<int[]> target, Long2ObjectOpenHashMap<int[]> source) {
            for (var entry : source.long2ObjectEntrySet()) {
                int[] value = target.get(entry.getLongKey());
                if (value == null) target.put(entry.getLongKey(), entry.getValue().clone());
                else for (int index = 0; index < value.length; index++) value[index] += entry.getValue()[index];
            }
        }

        private static void mergeDoubles(Long2ObjectOpenHashMap<double[]> target, Long2ObjectOpenHashMap<double[]> source) {
            for (var entry : source.long2ObjectEntrySet()) {
                double[] value = target.get(entry.getLongKey());
                if (value == null) target.put(entry.getLongKey(), entry.getValue().clone());
                else for (int index = 0; index < value.length; index++) value[index] += entry.getValue()[index];
            }
        }

        private void clear() {
            banCount.clear(); pickWin.clear(); playerOrder.clear(); matchupOrder.clear(); metrics.clear(); powerCurve.clear(); matchups.clear(); synergies.clear();
        }

        private int valueCount() {
            return banCount.size() + pickWin.size() + metrics.size() + powerCurve.size()
                + matchups.size() + synergies.size();
        }
    }

    public record MatrixResult(int filters, int emptyFilters, int persistedChampions) {}

    private static final class EventCounter {
        int kills;
        int soloKills;
        int assists;
        int deaths;
    }
}
