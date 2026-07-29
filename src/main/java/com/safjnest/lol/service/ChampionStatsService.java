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
import com.safjnest.sql.QueryRecord;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChampionStatsService {

    private static final long AT_15_MS = 15 * 60 * 1000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
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

    private ChampionStatsService() {}

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
        long started = System.nanoTime();
        Map<Integer, ChampionStatistics> computed = compute(filter, false);
        if (computed != null && !computed.isEmpty()) {
            long persistenceStarted = System.nanoTime();
            BotLogger.info("Saving champion stats for " + filter.genericKey());
            MongoDB.upsertChampionStatistics(computed);
            BotLogger.info("Champion stats persisted: filter=" + filter.genericKey()
                + ", champions=" + computed.size() + ", persistenceMs="
                + millis(System.nanoTime() - persistenceStarted) + ", totalMs="
                + millis(System.nanoTime() - started));
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
        if (filters == null || filters.isEmpty()) return new MatrixResult(0, 0, 0);

        Map<String, MatrixAccumulator> accumulators = new LinkedHashMap<>();
        for (Filter filter : filters) {
            if (filter == null || filter.patch() == null || filter.queue() == null) continue;
            accumulators.putIfAbsent(filter.genericKey(), new MatrixAccumulator(filter));
        }
        if (accumulators.isEmpty()) return new MatrixResult(0, 0, 0);

        Filter first = accumulators.values().iterator().next().filter;
        Filter source = new Filter()
            .setChampion(0)
            .setLane(null)
            .setQueue(first.queue())
            .setRank(null)
            .setPatch(first.patch())
            .setRegion(null);

        ChampionStatsProvider.forEachMatch(source, read -> {
            ChampionStatsData.RawMatch rawMatch = read.match();
            List<MatrixAccumulator> targets = new ArrayList<>();
            for (MatrixAccumulator accumulator : accumulators.values())
                if (matchesMatrixFilter(accumulator.filter, rawMatch)) targets.add(accumulator);
            if (targets.isEmpty()) {
                if (rawMatch != null && rawMatch.participants() != null) rawMatch.participants().clear();
                return;
            }

            ChampionStatsData.Game game = parse(rawMatch);
            if (game == null) {
                if (rawMatch != null && rawMatch.participants() != null) rawMatch.participants().clear();
                return;
            }
            for (MatrixAccumulator accumulator : targets) accumulate(accumulator, game);

            if (rawMatch != null && rawMatch.participants() != null) rawMatch.participants().clear();
        });

        int emptyFilters = 0;
        int persistedChampions = 0;
        for (MatrixAccumulator accumulator : accumulators.values()) {
            Map<Integer, Trend> trends = loadTrends(accumulator.filter, accumulator.pickWin);
            Map<Integer, ChampionStatistics> statistics = assemble(accumulator, trends);
            if (statistics.isEmpty()) emptyFilters++;
            else {
                MongoDB.upsertChampionStatistics(accumulator.filter, statistics);
                persistedChampions += statistics.size();
            }
            if (statistics.isEmpty()) MongoDB.upsertChampionStatistics(accumulator.filter, Map.of());
        }
        return new MatrixResult(accumulators.size(), emptyFilters, persistedChampions);
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

    private static void accumulate(MatrixAccumulator accumulator, ChampionStatsData.Game game) {
        Filter filter = accumulator.filter;
        acceptOverview(filter, game, accumulator.totalGames, accumulator.banGames,
            accumulator.pickWin, accumulator.banCount);
        acceptLane(filter, game, accumulator.laneAccum);
        acceptMatchup(filter, game, accumulator.matchupAccum);
        acceptSynergy(filter, game, accumulator.synergyAccum);
        acceptMetrics(filter, game, accumulator.metricAccum);
        acceptPowerCurve(filter, game, accumulator.powerCurveAccum);
    }

    private static Map<Integer, ChampionStatistics> assemble(
            MatrixAccumulator accumulator, Map<Integer, Trend> trends) {
        Map<Integer, List<LaneStat>> laneStats = new LinkedHashMap<>();
        Map<Integer, Map<MatchupKey, Matchup>> matchups = new LinkedHashMap<>();
        Map<Integer, List<LaneSynergy>> synergies = new LinkedHashMap<>();
        Map<Integer, ChampionStatsData.MetricValues> metrics = new LinkedHashMap<>();
        Map<Integer, List<PowerCurvePoint>> powerCurve = new LinkedHashMap<>();

        for (Map.Entry<Integer, int[]> entry : accumulator.pickWin.entrySet()) {
            int champion = entry.getKey();
            int picks = entry.getValue()[0];
            double winrate = rate(entry.getValue()[1], picks);
            laneStats.put(champion, laneOptions(accumulator.laneAccum, champion));
            matchups.put(champion, matchupOptions(accumulator.matchupAccum, champion, winrate,
                accumulator.banCount, accumulator.banGames[0]));
            synergies.put(champion, synergyOptions(accumulator.synergyAccum, champion, picks));
            metrics.put(champion, metricOptions(accumulator.metricAccum, champion));
            powerCurve.put(champion, powerCurveOptions(accumulator.powerCurveAccum, champion));
        }

        return assemble(accumulator.filter, accumulator.totalGames[0], accumulator.banGames[0],
            accumulator.pickWin, accumulator.banCount, laneStats, matchups, synergies,
            metrics, powerCurve, trends);
    }

    private static Map<Integer, ChampionStatistics> compute(Filter filter, boolean save) {
        long started = System.nanoTime();
        int[] totalGames = new int[1];
        int[] banGames = new int[1];
        Map<Integer, int[]> pickWin = new LinkedHashMap<>();
        Map<Integer, int[]> banCount = new HashMap<>();
        Map<Integer, Map<LaneType, int[]>> laneAccum = new HashMap<>();
        Map<Integer, Map<MatchupKey, double[]>> matchupAccum = new HashMap<>();
        Map<Integer, Map<ChampionStatsData.SynergyKey, int[]>> synergyAccum = new HashMap<>();
        Map<Integer, double[]> metricAccum = new HashMap<>();
        Map<Integer, Map<String, int[]>> powerCurveAccum = new HashMap<>();

        int[] processedMatches = new int[1];
        int[] parsedGamesTotal = new int[1];
        long[] rawMaterializeNanos = new long[1];
        long[] matchReadNanos = new long[1];
        long[] eventReadNanos = new long[1];
        long[] parseNanos = new long[1];
        long[] aggregateNanos = new long[1];
        long streamStarted = System.nanoTime();
        System.out.println("stats stream started: filter=" + filter.genericKey());
        ChampionStatsProvider.forEachMatch(filter, read -> {
            processedMatches[0]++;
            rawMaterializeNanos[0] += read.materializeNanos();
            matchReadNanos[0] += read.matchReadNanos();
            eventReadNanos[0] += read.eventReadNanos();
            ChampionStatsData.RawMatch rawMatch = read.match();
            long parseStarted = System.nanoTime();
            ChampionStatsData.Game game = parse(rawMatch);
            parseNanos[0] += System.nanoTime() - parseStarted;
            if (game != null) {
                parsedGamesTotal[0]++;
                long aggregateStarted = System.nanoTime();
                acceptOverview(filter, game, totalGames, banGames, pickWin, banCount);
                acceptLane(filter, game, laneAccum);
                acceptMatchup(filter, game, matchupAccum);
                acceptSynergy(filter, game, synergyAccum);
                acceptMetrics(filter, game, metricAccum);
                acceptPowerCurve(filter, game, powerCurveAccum);
                aggregateNanos[0] += System.nanoTime() - aggregateStarted;
            }
            if (rawMatch != null && rawMatch.participants() != null) rawMatch.participants().clear();
            game = null;
            rawMatch = null;
        });
        long streamNanos = System.nanoTime() - streamStarted;
        long totalMatches = processedMatches[0];

        long trendStarted = System.nanoTime();
        Map<Integer, Trend> trends = loadTrends(filter, pickWin);
        long trendNanos = System.nanoTime() - trendStarted;
        long assemblyStarted = System.nanoTime();
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
            matchups.put(champion, matchupOptions(matchupAccum, champion, winrate, banCount, banGames[0]));
            synergies.put(champion, synergyOptions(synergyAccum, champion, picks));
            metrics.put(champion, metricOptions(metricAccum, champion));
            powerCurve.put(champion, powerCurveOptions(powerCurveAccum, champion));
        }

        Map<Integer, ChampionStatistics> stats = assemble(
            filter, totalGames[0], banGames[0], pickWin, banCount, laneStats,
            matchups, synergies, metrics, powerCurve, trends);
        long assemblyNanos = System.nanoTime() - assemblyStarted;
        long persistenceNanos = 0;
        if (save && !stats.isEmpty()) {
            long persistenceStarted = System.nanoTime();
            save(stats);
            persistenceNanos = System.nanoTime() - persistenceStarted;
        }
        BotLogger.info("Champion stats compute completed: filter=" + filter.genericKey()
            + ", matches=" + totalMatches + ", parsed=" + parsedGamesTotal[0]
            + ", streamMs=" + millis(streamNanos) + ", matchReadMs=" + millis(matchReadNanos[0])
            + ", eventReadMs=" + millis(eventReadNanos[0])
            + ", rawMaterializeMs=" + millis(rawMaterializeNanos[0]) + ", parseMs=" + millis(parseNanos[0])
            + ", aggregateMs=" + millis(aggregateNanos[0]) + ", trendMs=" + millis(trendNanos)
            + ", assemblyMs=" + millis(assemblyNanos) + ", persistenceQueueMs=" + millis(persistenceNanos)
            + ", totalMs=" + millis(System.nanoTime() - started));
        return stats;
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

    private static void acceptOverview(Filter filter, ChampionStatsData.Game game, int[] totalGames,
                                       int[] banGames, Map<Integer, int[]> pickWin,
                                       Map<Integer, int[]> banCount) {
        totalGames[0]++;
        if (game.bans() != null && !game.bans().isEmpty()) banGames[0]++;
        addBans(game.bans(), banCount);
        for (ChampionStatsData.Player player : game.players()) {
            if (!isTargetLane(filter, player)) continue;
            int[] values = pickWin.computeIfAbsent(player.champion(), ignored -> new int[2]);
            values[0]++;
            if (player.win()) values[1]++;
        }
    }

    private static void acceptLane(Filter filter, ChampionStatsData.Game game,
                                   Map<Integer, Map<LaneType, int[]>> values) {
        for (ChampionStatsData.Player player : game.players()) {
            if (!isTargetLane(filter, player)) continue;
            int[] stats = values.computeIfAbsent(player.champion(), ignored -> new HashMap<>())
                .computeIfAbsent(player.lane(), ignored -> new int[2]);
            stats[0]++;
            if (player.win()) stats[1]++;
        }
    }

    private static void acceptMatchup(Filter filter, ChampionStatsData.Game game,
                                      Map<Integer, Map<MatchupKey, double[]>> values) {
        Map<TeamType, List<ChampionStatsData.Player>> byTeam = byTeam(game.players());
        List<List<ChampionStatsData.Player>> sides = new ArrayList<>(byTeam.values());
        if (sides.size() != 2) return;
        accumulateMatchups(filter, sides.get(0), sides.get(1), game.data(), values);
        accumulateMatchups(filter, sides.get(1), sides.get(0), game.data(), values);
    }

    private static void accumulateMatchups(Filter filter, List<ChampionStatsData.Player> team,
                                           List<ChampionStatsData.Player> enemies,
                                           ChampionStatsData.MatchData data,
                                           Map<Integer, Map<MatchupKey, double[]>> values) {
        for (ChampionStatsData.Player player : team) {
            if (!isTargetLane(filter, player) || player.lane() == null) continue;
            for (ChampionStatsData.Player opponent : enemies) {
                if (opponent.lane() != player.lane() || opponent.champion() == player.champion()) continue;
                MatchupKey key = new MatchupKey(opponent.champion(), opponent.lane());
                double[] value = values.computeIfAbsent(player.champion(), ignored -> new HashMap<>())
                    .computeIfAbsent(key, ignored -> new double[MATCHUP_VALUE_SIZE]);
                value[MATCHES]++;
                if (player.win()) value[WINS]++;

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

                ChampionStatsData.EventMetric eventMetric = data.eventMetrics().get(player.puuid());
                if (eventMetric == null || !eventMetric.available()) continue;
                value[METRIC_GAMES]++;
                value[SOLO_KILLS] += eventMetric.soloKills();
                value[KILLS] += eventMetric.kills();
                if (eventMetric.teamKills() > 0) {
                    value[KILL_PARTICIPATION_SUM] += (double) (eventMetric.kills() + eventMetric.assists())
                        / eventMetric.teamKills();
                    value[KILL_PARTICIPATION_GAMES]++;
                }
            }
        }
    }

    private static void acceptSynergy(Filter filter, ChampionStatsData.Game game,
                                      Map<Integer, Map<ChampionStatsData.SynergyKey, int[]>> values) {
        Map<TeamType, List<ChampionStatsData.Player>> byTeam = byTeam(game.players());
        for (ChampionStatsData.Player player : game.players()) {
            if (!isTargetLane(filter, player)) continue;
            for (ChampionStatsData.Player ally : byTeam.getOrDefault(player.team(), List.of())) {
                if (ally == player || !compatible(player.lane(), ally.lane())
                        || player.champion() == ally.champion()) continue;
                int[] stats = values.computeIfAbsent(player.champion(), ignored -> new HashMap<>())
                    .computeIfAbsent(new ChampionStatsData.SynergyKey(ally.champion(), ally.lane()),
                        ignored -> new int[2]);
                stats[0]++;
                if (player.win()) stats[1]++;
            }
        }
    }

    private static void acceptMetrics(Filter filter, ChampionStatsData.Game game,
                                      Map<Integer, double[]> values) {
        for (ChampionStatsData.Player player : game.players()) {
            if (!isTargetLane(filter, player)) continue;
            double[] value = values.computeIfAbsent(player.champion(), ignored -> new double[METRIC_VALUE_SIZE]);
            if (player.kda() != null) {
                int[] kda = parseKda(player.kda());
                value[KDA_KILLS] += kda[0];
                value[KDA_DEATHS] += kda[1];
                value[KDA_ASSISTS] += kda[2];
                value[KDA_GAMES]++;
            }
            double minutes = durationMinutes(player);
            if (player.cs() != null && minutes != 0) {
                value[CS_PER_MINUTE_SUM] += player.cs() / minutes;
                value[CS_PER_MINUTE_GAMES]++;
            }
            if (player.gold() != null && minutes != 0) {
                value[GOLD_PER_MINUTE_SUM] += player.gold() / minutes;
                value[GOLD_PER_MINUTE_GAMES]++;
            }
            ChampionStatsData.EventMetric eventMetric = game.data().eventMetrics().get(player.puuid());
            if (eventMetric != null && eventMetric.available()) value[EVENT_GAMES]++;
        }
    }

    private static void acceptPowerCurve(Filter filter, ChampionStatsData.Game game,
                                         Map<Integer, Map<String, int[]>> values) {
        for (ChampionStatsData.Player player : game.players()) {
            if (!isTargetLane(filter, player)) continue;
            long duration = player.timeEnd() - player.timeStart();
            if (duration <= 0) continue;
            long minutes = duration / 60000;
            String bucket = minutes <= 15 ? POWER_BUCKETS.get(0)
                : minutes <= 25 ? POWER_BUCKETS.get(1)
                : minutes <= 35 ? POWER_BUCKETS.get(2)
                : minutes <= 45 ? POWER_BUCKETS.get(3)
                : POWER_BUCKETS.get(4);
            int[] stats = values.computeIfAbsent(player.champion(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(bucket, ignored -> new int[2]);
            stats[0]++;
            if (player.win()) stats[1]++;
        }
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

    private static Map<Integer, Trend> loadTrends(Filter filter, Map<Integer, int[]> current) {
        Filter previousFilter = previousFilter(filter);
        if (previousFilter == null) return Map.of();

        Map<Integer, int[]> stored = storedPreviousPickWin(previousFilter, current);
        if (stored != null) return trendOptions(filter, previousFilter, current, stored);

        Map<Integer, int[]> previous = new HashMap<>();
        String lastMatchId = null;
        long previousTotal = ChampionStatsProvider.loadMatchCount(previousFilter);
        long previousBatches = ChampionStatsProvider.batchCount(previousTotal);
        long previousGames = 0;
        int batchNumber = 0;
        while (true) {
            List<String> matchIds = ChampionStatsProvider.loadMatchIds(previousFilter, lastMatchId);
            if (matchIds.isEmpty()) break;
            batchNumber++;
            int batchSize = matchIds.size();
            System.out.println("stats previous batch " + batchNumber + "/" + previousBatches
                + " started: " + matchIds.size() + " games");
            List<QueryRecord> result = ChampionStatsProvider.loadTrendParticipants(matchIds);
            mergePrevious(previous, previousPickWin(result, filter));
            previousGames += batchSize;
            lastMatchId = matchIds.get(matchIds.size() - 1);
            int resultSize = result.size();
            result.clear();
            matchIds.clear();
            System.out.println("stats previous batch " + batchNumber + "/" + previousBatches
                + " completed: processed=" + batchSize
                + ", rows=" + resultSize + ", raw released");
        }
        System.out.println("stats previous matches elaborated: games=" + previousGames);
        return previousGames == 0 ? Map.of() : trendOptions(filter, previousFilter, current, previous);
    }

    private static Map<Integer, int[]> storedPreviousPickWin(Filter previousFilter, Map<Integer, int[]> current) {
        Map<Integer, ChampionStatistics> stored = MongoDB.findChampionStatistics(previousFilter);
        if (stored.isEmpty()) return null;

        Map<Integer, int[]> result = new HashMap<>();
        for (Map.Entry<Integer, ChampionStatistics> entry : stored.entrySet()) {
            ChampionStatistics.Overview overview = entry.getValue() == null ? null : entry.getValue().overview();
            if (overview != null) result.put(entry.getKey(), new int[]{overview.picks(), overview.wins()});
        }
        for (Integer champion : current.keySet()) if (!result.containsKey(champion)) return null;
        return result;
    }

    private static Filter previousFilter(Filter filter) {
        if (filter == null || filter.patch() == null) return null;
        try {
            String currentPatch = PatchUtils.getPatch();
            String previousPatch = PatchUtils.getPreviousPatch();
            if (!filter.patch().equals(currentPatch) || previousPatch == null || previousPatch.isBlank()) return null;
            return new Filter().setPatch(previousPatch).setQueue(filter.queue()).setRank(filter.rank())
                .setRegion(filter.region()).setLane(filter.lane());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Map<Integer, int[]> previousPickWin(List<QueryRecord> result, Filter filter) {
        Map<Integer, int[]> values = new HashMap<>();
        if (result == null) return values;
        for (QueryRecord record : result) {
            LaneType lane = record.getAsLaneType("lane");
            if (filter.lane() != null && lane != filter.lane()) continue;
            int champion = record.getAsInt("champion");
            int[] stats = values.computeIfAbsent(champion, ignored -> new int[2]);
            stats[0]++;
            if (record.getAsBoolean("win")) stats[1]++;
        }
        return values;
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

    private static void mergePrevious(Map<Integer, int[]> target, Map<Integer, int[]> values) {
        for (Map.Entry<Integer, int[]> entry : values.entrySet()) {
            int[] total = target.computeIfAbsent(entry.getKey(), ignored -> new int[2]);
            total[0] += entry.getValue()[0];
            total[1] += entry.getValue()[1];
        }
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

    private static boolean isTargetLane(Filter filter, ChampionStatsData.Player player) {
        return filter.lane() == null || player.lane() == filter.lane();
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

    private static void addBans(Map<String, Object> bans, Map<Integer, int[]> banCount) {
        if (bans == null) return;
        for (Object value : bans.values()) {
            if (!(value instanceof List<?> champions)) continue;
            for (Object item : champions) {
                if (!(item instanceof Number number)) continue;
                int champion = number.intValue();
                if (champion != 0) banCount.computeIfAbsent(champion, ignored -> new int[1])[0]++;
            }
        }
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

    private static long millis(long nanos) {
        return nanos / NANOS_PER_MILLI;
    }

    public record MatrixResult(int filters, int emptyFilters, int persistedChampions) {}

    private static final class MatrixAccumulator {
        private final Filter filter;
        private final int[] totalGames = new int[1];
        private final int[] banGames = new int[1];
        private final Map<Integer, int[]> pickWin = new LinkedHashMap<>();
        private final Map<Integer, int[]> banCount = new HashMap<>();
        private final Map<Integer, Map<LaneType, int[]>> laneAccum = new HashMap<>();
        private final Map<Integer, Map<MatchupKey, double[]>> matchupAccum = new HashMap<>();
        private final Map<Integer, Map<ChampionStatsData.SynergyKey, int[]>> synergyAccum = new HashMap<>();
        private final Map<Integer, double[]> metricAccum = new HashMap<>();
        private final Map<Integer, Map<String, int[]>> powerCurveAccum = new HashMap<>();

        private MatrixAccumulator(Filter filter) {
            this.filter = filter;
        }
    }

    private static final class EventCounter {
        int kills;
        int soloKills;
        int assists;
        int deaths;
    }
}
