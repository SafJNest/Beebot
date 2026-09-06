package com.safjnest.lol.service;

import com.safjnest.lol.champion.ChampionStatsData;
import com.safjnest.lol.champion.ChampionStatsProvider;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.LaneSynergy;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.ChampionStatistics.Overview;
import com.safjnest.lol.model.ChampionStatistics.PowerCurvePoint;
import com.safjnest.lol.model.ChampionStatistics.Trend;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.statistics.ChampionStatsDocument;
import com.safjnest.lol.model.statistics.shared.ChampionLeafStats;
import com.safjnest.lol.model.statistics.shared.ChampionNode;
import com.safjnest.lol.model.statistics.shared.ChampionStatsScope;
import com.safjnest.lol.model.statistics.shared.MatchupStats;
import com.safjnest.lol.model.statistics.shared.TrendStats;
import com.safjnest.lol.model.statistics.shared.WinLossStats;
import com.safjnest.lol.utils.LaneTypeUtils;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.MatchMemoryUtils;
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
        if (filter == null) return Map.of();
        ChampionStatsDocument document = MongoDB.findChampionStatsDocument(ChampionStatsScope.from(filter));
        if (document == null || !document.ready) return Map.of();
        Map<Integer, ChampionStatistics> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, ChampionNode> entry : document.champions.entrySet()) {
            ChampionLeafStats leaf = leaf(entry.getValue(), filter.lane());
            if (leaf != null && leaf.games > 0) {
                Filter championFilter = copyFilter(filter, entry.getKey());
                result.put(entry.getKey(), toChampionStatistics(championFilter, document, entry.getValue(), leaf));
            }
        }
        return result;
    }

    public static ChampionStatistics get(Filter filter) {
        return get(filter, true);
    }

    public static boolean hasStored(Filter filter) {
        if (filter == null) return false;
        ChampionStatsDocument document = MongoDB.findChampionStatsDocument(ChampionStatsScope.from(filter));
        return document != null && document.ready;
    }

    public static Map<Integer, ChampionStatistics> recomputeAll(Filter filter) {
        if (filter == null || filter.patch() == null || filter.queue() == null) return Map.of();
        recomputeScope(ChampionStatsScope.from(filter));
        return getAll(filter);
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

        String key = RedisKey.CHAMPION_STATS.of(filter.champion(), filter.genericKey());
        ChampionStatistics stats;
        try {
            stats = RedisClient.get(key, ChampionStatistics.class);
        } catch (RuntimeException exception) {
            RedisClient.delete(key);
            stats = null;
        }
        if (stats != null) return stats;

        // New doc shape: 1 doc per scope, lanes inside
        try {
            ChampionStatsScope scope = ChampionStatsScope.from(filter);
            com.safjnest.lol.model.statistics.ChampionStatsDocument doc = MongoDB.findChampionStatsDocument(scope);
            if (doc != null && doc.ready) {
                com.safjnest.lol.model.statistics.shared.ChampionNode node = doc.champions.get(filter.champion());
                if (node != null) {
                ChampionLeafStats leaf = leaf(node, filter.lane());
                    if (leaf != null && leaf.games > 0) {
                        stats = toChampionStatistics(filter, doc, node, leaf);
                        RedisClient.set(RedisKey.CHAMPION_STATS, stats, filter.champion(), filter.genericKey());
                        return stats;
                    }
                }
                if (doc.champions.containsKey(filter.champion())) {
                    // champion exists but no games for this lane -> empty
                    stats = empty(filter);
                    RedisClient.set(RedisKey.CHAMPION_STATS, stats, filter.champion(), filter.genericKey());
                    return stats;
                }
            }
        } catch (RuntimeException ignored) {}

        if (!allowCompute || filter.patch() == null || filter.queue() == null) return null;
        recomputeScope(ChampionStatsScope.from(filter));
        return get(filter, false);
    }

    static ChampionStatistics toChampionStatistics(Filter filter, com.safjnest.lol.model.statistics.ChampionStatsDocument doc, com.safjnest.lol.model.statistics.shared.ChampionNode node, ChampionLeafStats leaf) {
        // Derive overview from leaf + doc totals
        int picks = (int) leaf.games;
        int wins = (int) leaf.wins;
        int bans = (int) node.bans;
        int totalGames = (int) doc.games;
        int banGames = (int) doc.banGames;
        double winrate = leaf.winrate();
        double pickrate = totalGames == 0 ? 0 : (double) picks / totalGames;
        Double banrate = banGames == 0 ? null : (double) bans / banGames;
        // Convert matchups: leaf.matchups is opp -> MatchupStats, key is opponent champion id
        Map<Integer, ChampionStatistics.Matchup> matchups = new java.util.LinkedHashMap<>();
        for (Map.Entry<Integer, com.safjnest.lol.model.statistics.shared.MatchupStats> e : leaf.matchups.entrySet()) {
            com.safjnest.lol.model.statistics.shared.MatchupStats m = e.getValue();
            double mWinrate = m.winrate();
            ChampionNode opponent = doc.champions.get(e.getKey());
            Double opponentBanRate = opponent == null || banGames == 0 ? null : (double) opponent.bans / banGames;
            matchups.put(e.getKey(), new ChampionStatistics.Matchup((int)m.games, (int)m.wins, mWinrate, mWinrate - winrate, m.goldDiffAt15() == null ? null : m.goldDiffAt15().intValue(), m.csDiffAt15(), m.soloKillRate(), m.killParticipation(), opponentBanRate, (int)m.metricGames));
        }
        // laneStats: single entry for requested lane or all lanes if lane==null
        java.util.List<ChampionStatistics.LaneStat> laneStats = new java.util.ArrayList<>();
        if (filter.lane() != null) laneStats.add(new ChampionStatistics.LaneStat(filter.lane(), picks, winrate));
        else for (Map.Entry<String, ChampionLeafStats> e : node.lanes.entrySet()) {
            try { LaneType l = LaneType.valueOf(e.getKey()); laneStats.add(new ChampionStatistics.LaneStat(l, (int)e.getValue().games, e.getValue().winrate())); } catch (Exception ignored) {}
        }
        List<LaneSynergy> synergies = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, WinLossStats>> lane : leaf.synergies.entrySet()) {
            LaneType allyLane;
            try { allyLane = LaneType.valueOf(lane.getKey()); } catch (IllegalArgumentException ignored) { continue; }
            for (Map.Entry<Integer, WinLossStats> ally : lane.getValue().entrySet()) {
                WinLossStats value = ally.getValue();
                if (value != null && value.games > 0)
                    synergies.add(new LaneSynergy(ally.getKey(), allyLane, (int)value.games, (int)value.wins,
                        value.winrate(), (double)value.games / picks));
            }
        }
        List<PowerCurvePoint> powerCurve = new ArrayList<>();
        for (String bucket : POWER_BUCKETS) {
            WinLossStats value = leaf.powerCurve.get(bucket);
            if (value != null && value.games > 0)
                powerCurve.add(new PowerCurvePoint(bucket, (int)value.games, (int)value.wins, value.winrate()));
        }
        Trend trend = leaf.trend == null || leaf.trend.games == 0 ? null
            : new Trend(doc.previousPatch, (int)leaf.trend.games, leaf.trend.winrate(), winrate - leaf.trend.winrate());
        return new ChampionStatistics(filter, new Overview(totalGames, picks, bans, wins, winrate, pickrate, banrate, leaf.kda(), leaf.csPerMinute(), leaf.goldPerMinute(), null), laneStats, matchups, synergies, powerCurve, trend);
    }

    static MatrixResult recomputeMatrix(List<Filter> filters) {
        return recomputeMatrixCoalesced(filters, List.of());
    }

    static MatrixResult recomputeMatrix(List<Filter> filters, List<Filter> buildFilters) {
        return recomputeMatrixCoalesced(filters, buildFilters);
    }

    // New coalesce: 1 doc per scope (queue|rank|patch|region), lanes dentro
    static MatrixResult recomputeMatrixCoalesced(List<Filter> filters, List<Filter> buildFilters) {
        if (filters == null || filters.isEmpty()) return new MatrixResult(0, 0, 0);
        // Deduplicate to scopes (without lane)
        Map<String, ChampionStatsScope> scopes = new LinkedHashMap<>();
        for (Filter f : filters) if (f != null && f.patch() != null && f.queue() != null) {
            ChampionStatsScope s = ChampionStatsScope.from(f);
            // scope key without lane: queue|rank|patch|region
            String key = s.toKey();
            scopes.putIfAbsent(key, s);
        }
        if (scopes.isEmpty()) return new MatrixResult(0, 0, 0);
        // Keep RawMatrix logic intact, then coalesce per scope
        Map<String, ChampionBuildEngine.BuildAccumulator> builds = new java.util.LinkedHashMap<>();
        if (buildFilters != null) for (Filter f : buildFilters) if (f != null && f.champion() != 0 && f.patch() != null && f.queue() != null) builds.putIfAbsent(f.toKey(), ChampionBuildEngine.newAccumulator(f));
        Filter first = filters.get(0);
        Filter source = new Filter().setChampion(0).setLane(null).setQueue(first.queue()).setRank(null)
            .setRankBehavior(first.rankBehavior()).setPatch(first.patch()).setRegion(null);
        RawMatrix raw = new RawMatrix();
        int persistedChampions = 0;
        try {
            ChampionStatsProvider.forEachMatchWithBuild(source, (read, document) -> {
                ChampionStatsData.RawMatch rm = read.match();
                try {
                    for (ChampionBuildEngine.BuildAccumulator acc : builds.values()) for (var rec : com.safjnest.nosql.MongoDB.championBuildRecords(document, acc.filter())) ChampionBuildEngine.accept(acc, rec);
                    ChampionStatsData.Game g = parse(rm); if (g != null) raw.addBase(g, rm.metadata());
                } finally { MatchMemoryUtils.release(rm); }
            }, read -> {
                ChampionStatsData.RawMatch rm = read.match();
                try { ChampionStatsData.Game g = parse(rm); if (g != null) raw.addEvents(g, rm.metadata()); } finally { MatchMemoryUtils.release(rm); }
            });
            // previousPatch at root
            String previousPatch = null;
            try { java.util.List<String> patches = PatchUtils.getPatches(); int idx = patches.indexOf(first.patch()); if (idx >=0 && idx+1 < patches.size()) previousPatch = patches.get(idx+1); } catch (Exception ignored) {}
            for (ChampionStatsScope scope : scopes.values()) {
                ChampionStatsDocument doc = new ChampionStatsDocument(scope, 0, 0, previousPatch);
                try {
                    // global totals
                    RawProjection global = raw.project(scope.toFilter().setLane(null));
                    doc.games = global.totalGames();
                    doc.banGames = global.banGames();
                    for (Map.Entry<Integer, int[]> e : global.banCount().entrySet()) {
                        ChampionNode node = doc.champions.computeIfAbsent(e.getKey(), k -> new ChampionNode());
                        node.bans = e.getValue()[0];
                    }
                    for (LaneType lane : persistedLanes(scope)) {
                        Filter laneFilter = scope.toFilter().setLane(lane);
                        RawProjection proj = raw.project(laneFilter);
                        if (proj.pickWin().isEmpty() && proj.banCount().isEmpty() && proj.metricsRaw().isEmpty()) continue;
                        for (Map.Entry<Integer, int[]> e : proj.pickWin().entrySet()) {
                            int champ = e.getKey();
                            ChampionNode node = doc.champions.computeIfAbsent(champ, k -> new ChampionNode());
                            ChampionLeafStats leaf = node.lanes.computeIfAbsent(lane.name(), k -> new ChampionLeafStats());
                            leaf.games = e.getValue()[0];
                            leaf.wins = e.getValue()[1];
                        }
                        for (Map.Entry<Integer, double[]> e : proj.metricsRaw().entrySet()) {
                            int champ = e.getKey();
                            double[] v = e.getValue();
                            ChampionNode node = doc.champions.get(champ);
                            if (node == null) continue;
                            ChampionLeafStats leaf = node.lanes.get(lane.name());
                            if (leaf == null) leaf = node.lanes.computeIfAbsent(lane.name(), k -> new ChampionLeafStats());
                            leaf.kills = (long) v[KDA_KILLS];
                            leaf.deaths = (long) v[KDA_DEATHS];
                            leaf.assists = (long) v[KDA_ASSISTS];
                            leaf.csm = v[CS_PER_MINUTE_SUM];
                            leaf.csmGames = (long) v[CS_PER_MINUTE_GAMES];
                            leaf.gpm = v[GOLD_PER_MINUTE_SUM];
                            leaf.gpmGames = (long) v[GOLD_PER_MINUTE_GAMES];
                        }
                        for (Map.Entry<Integer, Map<Integer, double[]>> e : proj.matchupRaw().entrySet()) {
                            int champ = e.getKey();
                            ChampionNode node = doc.champions.get(champ);
                            if (node == null) continue;
                            ChampionLeafStats leaf = node.lanes.get(lane.name());
                            if (leaf == null) continue;
                            for (Map.Entry<Integer, double[]> me : e.getValue().entrySet()) {
                                int opp = me.getKey();
                                double[] v = me.getValue();
                                MatchupStats ms = leaf.matchups.computeIfAbsent(opp, k -> new MatchupStats());
                                ms.games = (long) v[MATCHES];
                                ms.wins = (long) v[WINS];
                                ms.goldDiff = (long) v[GOLD_DIFF_SUM];
                                ms.goldDiffGames = (long) v[GOLD_DIFF_GAMES];
                                ms.csDiff = (long) v[CS_DIFF_SUM];
                                ms.csDiffGames = (long) v[CS_DIFF_GAMES];
                                ms.soloKills = (long) v[SOLO_KILLS];
                                ms.kills = (long) v[KILLS];
                                ms.kp = v[KILL_PARTICIPATION_SUM];
                                ms.kpGames = (long) v[KILL_PARTICIPATION_GAMES];
                                ms.metricGames = (long) v[METRIC_GAMES];
                            }
                        }
                        for (Map.Entry<Integer, Map<ChampionStatsData.SynergyKey, int[]>> e : proj.synergyRaw().entrySet()) {
                            ChampionNode node = doc.champions.get(e.getKey());
                            if (node == null) continue;
                            ChampionLeafStats leaf = node.lanes.get(lane.name());
                            if (leaf == null) continue;
                            for (Map.Entry<ChampionStatsData.SynergyKey, int[]> synergy : e.getValue().entrySet()) {
                                ChampionStatsData.SynergyKey key = synergy.getKey();
                                int[] value = synergy.getValue();
                                Map<Integer, WinLossStats> byAlly = leaf.synergies.computeIfAbsent(key.lane().name(), k -> new LinkedHashMap<>());
                                WinLossStats w = byAlly.computeIfAbsent(key.champion(), k -> new WinLossStats());
                                w.games = value[0];
                                w.wins = value[1];
                            }
                        }
                        for (Map.Entry<Integer, Map<String, int[]>> e : proj.powerCurveRaw().entrySet()) {
                            ChampionNode node = doc.champions.get(e.getKey());
                            if (node == null) continue;
                            ChampionLeafStats leaf = node.lanes.get(lane.name());
                            if (leaf == null) continue;
                            for (Map.Entry<String, int[]> powerCurve : e.getValue().entrySet()) {
                                WinLossStats w = leaf.powerCurve.computeIfAbsent(powerCurve.getKey(), k -> new WinLossStats());
                                w.games = powerCurve.getValue()[0];
                                w.wins = powerCurve.getValue()[1];
                            }
                        }
                    }
                    addPreviousTrend(doc);
                    MongoDB.upsertChampionStatsDocument(doc);
                    persistedChampions += doc.champions.size();
                } finally {
                    MatchMemoryUtils.release(doc.champions);
                }
            }
            for (ChampionBuildEngine.BuildAccumulator acc : builds.values()) {
                java.util.List<com.safjnest.lol.model.Build> res = ChampionBuildEngine.finish(acc);
                if (res.isEmpty()) res = ChampionBuildEngine.emptyResult(acc.filter());
                com.safjnest.nosql.MongoDB.upsertChampionBuilds(res);
            }
            return new MatrixResult(scopes.size(), 0, persistedChampions);
        } finally {
            raw.clear();
            for (ChampionBuildEngine.BuildAccumulator accumulator : builds.values()) accumulator.clear();
            builds.clear();
        }
    }

    static void recomputeScope(ChampionStatsScope scope) {
        if (scope == null || scope.patch() == null || scope.queue() == null) return;
        recomputeMatrixCoalesced(List.of(scope.toFilter()), List.of());
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

    private static ChampionLeafStats leaf(ChampionNode node, LaneType lane) {
        if (node == null) return null;
        return lane == null ? node.overall() : node.lanes.get(lane.name());
    }

    private static List<LaneType> persistedLanes(ChampionStatsScope scope) {
        if (scope.queue() == null || !GameQueueTypeUtils.hasLane(scope.queue())) return List.of(LaneType.NONE);
        List<LaneType> result = new ArrayList<>(LaneTypeUtils.playables());
        result.add(LaneType.NONE);
        return result;
    }

    private static Filter copyFilter(Filter source, int champion) {
        return new Filter().setChampion(champion).setLane(source.lane()).setQueue(source.queue())
            .setRank(source.rank()).setRankBehavior(source.rankBehavior()).setPatch(source.patch())
            .setRegion(source.region()).setPeriod(source.timeStart(), source.timeEnd());
    }

    private static void addPreviousTrend(ChampionStatsDocument document) {
        if (document.scope == null || document.previousPatch == null || document.previousPatch.isBlank()) return;
        ChampionStatsScope scope = document.scope;
        ChampionStatsScope previousScope = new ChampionStatsScope(scope.queue(), scope.rank(), scope.rankBehavior(),
            document.previousPatch, scope.region());
        ChampionStatsDocument previous = MongoDB.findChampionStatsDocument(previousScope);
        if (previous == null || !previous.ready) return;
        for (Map.Entry<Integer, ChampionNode> champion : document.champions.entrySet()) {
            ChampionNode previousNode = previous.champions.get(champion.getKey());
            if (previousNode == null) continue;
            for (Map.Entry<String, ChampionLeafStats> lane : champion.getValue().lanes.entrySet()) {
                ChampionLeafStats previousLeaf = previousNode.lanes.get(lane.getKey());
                if (previousLeaf != null) lane.getValue().trend = new TrendStats(previousLeaf.games, previousLeaf.wins);
            }
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
        try {
            return parseEventData(players, events);
        } finally {
            MatchMemoryUtils.release(events);
        }
    }

    private static ChampionStatsData.MatchData parseEventData(
            List<ChampionStatsData.Player> players,
            JSONObject events) {
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

    // assemble(RawProjection) removed — new flow uses direct Leaf

    record RawProjection(
        Filter filter,
        int totalGames,
        int banGames,
        Map<Integer, int[]> pickWin,
        Map<Integer, int[]> banCount,
        Map<Integer, double[]> metricsRaw,
        Map<Integer, Map<Integer, double[]>> matchupRaw,
        Map<Integer, Map<ChampionStatsData.SynergyKey, int[]>> synergyRaw,
        Map<Integer, Map<String, int[]>> powerCurveRaw
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
            Map<Integer, Map<Integer, double[]>> matchupAccum = new LinkedHashMap<>();
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
            for (long key : orderedPackedPairs(rollup, filter.lane())) {
                if (!matchesLane(key, filter.lane())) continue;
                int champion = matchupChampion(key);
                int opponent = matchupOpponent(key);
                merge(matchupAccum.computeIfAbsent(champion, ignored -> new LinkedHashMap<>()), opponent,
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

            Map<Integer, int[]> banCount = toJavaMap(rollup.banCount);
            return new RawProjection(filter, rollup.totalGames, rollup.banGames, pickWin,
                banCount, metricAccum, matchupAccum, synergyAccum, powerCurveAccum);
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
                    long key = packedPair(playerIndex, index(opponent.champion()), playerLane);
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

        private List<Long> orderedPackedPairs(RawBucket bucket, LaneType lane) {
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
        private static long packedPair(int champion, int opponent, int lane) { return ((long) champion << 28) | ((long) opponent << LANE_BITS) | lane; }
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
            sequence = 0;
            peakBucketValues = 0;
        }
    }

    private static final class RawBucket {
        private final no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard region;
        private final no.stelar7.api.r4j.basic.constants.types.lol.TierType rank;
        private int totalGames;
        private int banGames;
        private final Int2IntOpenHashMap banCount = new Int2IntOpenHashMap();
        private final Long2ObjectOpenHashMap<int[]> pickWin = new Long2ObjectOpenHashMap<>();
        private final Long2IntOpenHashMap playerOrder = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap matchupOrder = new Long2IntOpenHashMap();
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
            totalGames = 0;
            banGames = 0;
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
