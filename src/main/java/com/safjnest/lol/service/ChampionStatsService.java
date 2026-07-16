package com.safjnest.lol.service;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ChampionStatistics.LaneStat;
import com.safjnest.lol.model.ChampionStatistics.LaneSynergy;
import com.safjnest.lol.model.ChampionStatistics.Matchup;
import com.safjnest.lol.model.ChampionStatistics.MatchupKey;
import com.safjnest.lol.model.ChampionStatistics.Overview;
import com.safjnest.lol.model.ChampionStatistics.PowerCurvePoint;
import com.safjnest.lol.model.ChampionStatistics.Trend;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.utils.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChampionStatsService {

    private static final List<String> POWER_BUCKETS = List.of("0-15", "15-25", "25-35", "35-45", "45+");
    private static final long AT_15_MS = 15 * 60 * 1000L;
    private static final int GAME_BATCH_SIZE = 1000;

    private record Row(
        int champion,
        LaneType lane,
        boolean win,
        TeamType team,
        String matchId,
        long timeStart,
        long timeEnd,
        String kda,
        Integer cs,
        Integer gold,
        String puuid
    ) {}

    private record RawParticipant(
        int champion,
        LaneType lane,
        boolean win,
        TeamType team,
        String matchId,
        String kda,
        Integer cs,
        Integer gold,
        int summonerId
    ) {}

    private record MatchMeta(String bans, String events, long timeStart, long timeEnd) {}

    private record Snapshot(Integer cs, Integer gold) {}

    private record EventMetric(int kills, int soloKills, int assists, int teamKills, int deaths,
                               boolean available) {}

    private record MatchData(Map<String, EventMetric> eventMetrics, Map<String, Snapshot> snapshots,
                             boolean eventsAvailable) {}

    private record MatchBatch(Map<String, MatchMeta> metadata, Map<String, List<Row>> byMatch) {}

    private static final class Aggregation {
        int totalGames;
        int banGames;
        final Map<Integer, int[]> pickWin = new LinkedHashMap<>();
        final Map<Integer, int[]> banCount = new HashMap<>();
        final Map<Integer, Map<LaneType, int[]>> laneAccum = new HashMap<>();
        final Map<Integer, Map<MatchupKey, MatchupAccumulator>> matchupAccum = new HashMap<>();
        final Map<Integer, Map<SynergyKey, int[]>> synergyAccum = new HashMap<>();
        final Map<Integer, MetricAccumulator> metrics = new HashMap<>();
        final Map<Integer, Map<String, int[]>> powerCurve = new HashMap<>();
    }

    private static final class MetricAccumulator {
        int metricGames;
        long kills;
        long deaths;
        long assists;
        int kdaGames;
        double csPerMinuteSum;
        int csPerMinuteGames;
        double goldPerMinuteSum;
        int goldPerMinuteGames;
        int soloKills;
        int eventKills;
        double killParticipationSum;
        int killParticipationGames;

        private void add(Row row, MatchData data) {
            if (row.kda() != null) {
                int[] values = parseKda(row.kda());
                kills += values[0];
                deaths += values[1];
                assists += values[2];
                kdaGames++;
            }

            double minutes = durationMinutes(row);
            if (row.cs() != null && minutes != 0) {
                csPerMinuteSum += row.cs() / minutes;
                csPerMinuteGames++;
            }
            if (row.gold() != null && minutes != 0) {
                goldPerMinuteSum += row.gold() / minutes;
                goldPerMinuteGames++;
            }

            EventMetric eventMetric = data.eventMetrics().get(row.puuid());
            if (eventMetric == null || !eventMetric.available()) return;
            metricGames++;
            soloKills += eventMetric.soloKills();
            eventKills += eventMetric.kills();
            if (eventMetric.teamKills() > 0) {
                killParticipationSum += (double) (eventMetric.kills() + eventMetric.assists()) / eventMetric.teamKills();
                killParticipationGames++;
            }
        }

        private Double kda() {
            return kdaGames == 0 && metricGames == 0
                ? null : deaths > 0 ? (double) (kills + assists) / deaths : (double) (kills + assists);
        }

        private Double csPerMinute() {
            return csPerMinuteGames > 0 ? csPerMinuteSum / csPerMinuteGames : null;
        }

        private Double goldPerMinute() {
            return goldPerMinuteGames > 0 ? goldPerMinuteSum / goldPerMinuteGames : null;
        }

        private Double soloKillRate() {
            return eventKills > 0 ? (double) soloKills / eventKills : metricGames > 0 ? 0d : null;
        }

        private Double killParticipation() {
            return killParticipationGames > 0 ? killParticipationSum / killParticipationGames : null;
        }
    }

    private static final class MatchupAccumulator {
        int matches;
        int wins;
        int metricGames;
        long goldDiffSum;
        int goldDiffGames;
        double csDiffSum;
        int csDiffGames;
        int soloKills;
        int kills;
        double killParticipationSum;
        int killParticipationGames;

        private void add(Row player, Row opponent, MatchData data) {
            matches++;
            if (player.win()) wins++;

            Snapshot playerSnapshot = data.snapshots().get(player.puuid());
            Snapshot opponentSnapshot = data.snapshots().get(opponent.puuid());
            if (playerSnapshot != null && opponentSnapshot != null) {
                if (playerSnapshot.gold() != null && opponentSnapshot.gold() != null) {
                    goldDiffSum += playerSnapshot.gold() - opponentSnapshot.gold();
                    goldDiffGames++;
                }
                if (playerSnapshot.cs() != null && opponentSnapshot.cs() != null) {
                    csDiffSum += playerSnapshot.cs() - opponentSnapshot.cs();
                    csDiffGames++;
                }
            }

            EventMetric eventMetric = data.eventMetrics().get(player.puuid());
            if (eventMetric == null || !eventMetric.available()) return;
            metricGames++;
            soloKills += eventMetric.soloKills();
            kills += eventMetric.kills();
            if (eventMetric.teamKills() > 0) {
                killParticipationSum += (double) (eventMetric.kills() + eventMetric.assists()) / eventMetric.teamKills();
                killParticipationGames++;
            }
        }

        private Double soloKillRate() {
            return kills > 0 ? (double) soloKills / kills : metricGames > 0 ? 0d : null;
        }

        private Double killParticipation() {
            return killParticipationGames > 0 ? killParticipationSum / killParticipationGames : null;
        }
    }

    private record SynergyKey(int champion, LaneType lane) {}

    public ChampionStatsService() {}

    public Map<Integer, ChampionStatistics> getAll(Filter filter) {
        Map<Integer, ChampionStatistics> cached;
        try {
            cached = LeagueDB.getChampionStats(filter);
        } catch (RuntimeException exception) {
            BotLogger.warning("Invalid persisted champion stats for " + filter.genericKey()
                + ": " + exception.getMessage());
            cached = null;
        }
        return cached != null ? cached : compute(filter, true);
    }

    public ChampionStatistics get(Filter filter) {
        return get(filter, true);
    }

    public Map<Integer, ChampionStatistics> recomputeAll(Filter filter) {
        Map<Integer, ChampionStatistics> computed = compute(filter, false);
        if (computed != null && !computed.isEmpty()) {
            BotLogger.info("Saving champion stats for " + filter.genericKey());
            LeagueDB.saveChampionStats(computed);
            computed.values().forEach(stat -> RedisClient.set(
                RedisKey.CHAMPION_STATS.of(stat.filter().genericKey(), stat.filter().champion()), stat, 0));
        }
        return computed;
    }

    // ============================================================================

    ChampionStatistics get(Filter filter, boolean allowCompute) {
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
            stats = LeagueDB.getChampionStats(filter, filter.champion());
        } catch (RuntimeException exception) {
            BotLogger.warning("Invalid persisted champion stats for " + filter.toKey()
                + ": " + exception.getMessage());
            return null;
        }
        if (stats != null) {
            RedisClient.set(key, stats, 0);
            return stats;
        }
        if (!allowCompute) return null;

        Map<Integer, ChampionStatistics> computed = compute(filter, true);
        stats = computed == null ? null : computed.get(filter.champion());
        if (stats != null) RedisClient.set(key, stats, 0);
        return stats;
    }

    private Map<Integer, ChampionStatistics> compute(Filter filter, boolean save) {
        Aggregation aggregation = new Aggregation();
        long lastMatchId = 0;
        long totalMatches = loadMatchCount(filter);
        long totalBatches = batchCount(totalMatches);
        int batchNumber = 0;

        while (true) {
            List<String> matchIds = loadMatchIds(filter, lastMatchId);
            if (matchIds.isEmpty()) break;

            batchNumber++;
            System.out.println("stats batch " + batchNumber + "/" + totalBatches
                + " started: " + matchIds.size() + " games");
            MatchBatch batch = loadMatchBatch(matchIds);

            for (int index = 0; index < matchIds.size(); index++) {
                String matchId = matchIds.get(index);
                System.out.println("stats game " + (index + 1) + "/" + matchIds.size()
                    + " started: " + matchId);
                processMatch(matchId, filter, batch, aggregation);
                System.out.println("stats game " + (index + 1) + "/" + matchIds.size()
                    + " completed: " + matchId);
            }

            lastMatchId = lastMatchId(matchIds);
            batch.metadata().clear();
            batch.byMatch().clear();
            matchIds.clear();
            System.out.println("stats batch " + batchNumber + "/" + totalBatches + " completed");
        }

        Map<Integer, Trend> trends = computeTrends(filter, aggregation.pickWin);

        Map<Integer, ChampionStatistics> stats = new LinkedHashMap<>();
        for (Map.Entry<Integer, int[]> entry : aggregation.pickWin.entrySet()) {
            int champion = entry.getKey();
            int picks = entry.getValue()[0];
            int wins = entry.getValue()[1];
            int bans = aggregation.banCount.getOrDefault(champion, new int[1])[0];
            double championWinrate = rate(wins, picks);

            List<LaneStat> laneStats = aggregation.laneAccum.getOrDefault(champion, Map.of()).entrySet().stream()
                .map(lane -> new LaneStat(lane.getKey(), lane.getValue()[0], rate(lane.getValue()[1], lane.getValue()[0])))
                .sorted(Comparator.comparingInt(LaneStat::games).reversed())
                .toList();

            Map<MatchupKey, Matchup> matchups = new LinkedHashMap<>();
            for (Map.Entry<MatchupKey, MatchupAccumulator> matchup : aggregation.matchupAccum
                    .getOrDefault(champion, Map.of()).entrySet()) {
                MatchupAccumulator value = matchup.getValue();
                int opponentBans = aggregation.banCount.getOrDefault(matchup.getKey().champion(), new int[1])[0];
                matchups.put(matchup.getKey(), new Matchup(
                    matchup.getKey().champion(),
                    matchup.getKey().lane(),
                    value.matches,
                    value.wins,
                    rate(value.wins, value.matches),
                    rate(value.wins, value.matches) - championWinrate,
                    value.goldDiffGames > 0 ? (int) Math.round((double) value.goldDiffSum / value.goldDiffGames) : null,
                    value.csDiffGames > 0 ? value.csDiffSum / value.csDiffGames : null,
                    value.soloKillRate(),
                    value.killParticipation(),
                    aggregation.banGames > 0 ? (double) opponentBans / aggregation.banGames : null,
                    value.metricGames
                ));
            }

            List<LaneSynergy> synergies = new ArrayList<>();
            for (Map.Entry<SynergyKey, int[]> synergy : aggregation.synergyAccum
                    .getOrDefault(champion, Map.of()).entrySet()) {
                int matches = synergy.getValue()[0];
                int synergyWins = synergy.getValue()[1];
                synergies.add(new LaneSynergy(synergy.getKey().champion(), synergy.getKey().lane(), matches,
                    synergyWins, rate(synergyWins, matches), rate(matches, picks)));
            }
            synergies.sort(Comparator.comparingInt(LaneSynergy::allyChampion).thenComparing(s -> String.valueOf(s.allyLane())));

            Filter championFilter = new Filter()
                .setChampion(champion)
                .setLane(filter.lane())
                .setPatch(filter.patch())
                .setQueue(filter.queue())
                .setRank(filter.rank())
                .setRegion(filter.region());

            MetricAccumulator metric = aggregation.metrics.getOrDefault(champion, new MetricAccumulator());
            ChampionStatistics statistic = new ChampionStatistics(
                championFilter,
                new Overview(
                    aggregation.totalGames,
                    picks,
                    bans,
                    wins,
                    championWinrate,
                    rate(picks, aggregation.totalGames),
                    aggregation.banGames > 0 ? (double) bans / aggregation.banGames : null,
                    metric.kda(),
                    metric.csPerMinute(),
                    metric.goldPerMinute(),
                    null
                ),
                laneStats,
                matchups,
                synergies,
                toPowerCurve(aggregation.powerCurve.getOrDefault(champion, Map.of())),
                trends.get(champion)
            );
            stats.put(champion, statistic);
        }

        if (save && !stats.isEmpty()) {
            stats.values().forEach(statistic -> {
                ChronoTask saveTask = () -> LeagueDB.saveChampionStats(statistic);
                RedisClient.set(RedisKey.CHAMPION_STATS.of(
                    statistic.filter().genericKey(), statistic.filter().champion()), statistic, 0);
                saveTask.queue();
            });
        }
        return stats;
    }

    private static long loadMatchCount(Filter filter) {
        QueryResult result = LeagueDB.get().query(
            "SELECT COUNT(*) AS total_matches FROM `match` m "
            + filter.sqlMatchOnly() + matchLanePredicate(filter)
        );
        return result.isEmpty() ? 0 : result.get(0).getAsLong("total_matches");
    }

    private static List<String> loadMatchIds(Filter filter, long lastId) {
        QueryResult result = LeagueDB.get().query(
            "SELECT m.id AS match_id FROM `match` m "
            + filter.sqlMatchOnly()
            + " AND m.id > " + lastId
            + matchLanePredicate(filter)
            + " ORDER BY m.id ASC LIMIT " + GAME_BATCH_SIZE
        );
        List<String> matchIds = new ArrayList<>();
        for (QueryRecord record : result) matchIds.add(record.get("match_id"));
        return matchIds;
    }

    private static MatchBatch loadMatchBatch(List<String> matchIds) {
        Map<String, MatchMeta> metadata = loadMatchMetadata(matchIds);
        List<RawParticipant> rawParticipants = loadRawParticipants(matchIds);
        Map<Integer, String> puuidBySummoner = loadPuuids(rawParticipants);
        Map<String, List<Row>> byMatch = new LinkedHashMap<>();
        for (RawParticipant participant : rawParticipants) {
            MatchMeta match = metadata.get(participant.matchId());
            if (match == null) continue;
            Row row = new Row(
                participant.champion(),
                participant.lane(),
                participant.win(),
                participant.team(),
                participant.matchId(),
                match.timeStart(),
                match.timeEnd(),
                participant.kda(),
                participant.cs(),
                participant.gold(),
                puuidBySummoner.get(participant.summonerId())
            );
            byMatch.computeIfAbsent(row.matchId(), ignored -> new ArrayList<>()).add(row);
        }
        rawParticipants.clear();
        puuidBySummoner.clear();
        return new MatchBatch(metadata, byMatch);
    }

    private static Map<String, MatchMeta> loadMatchMetadata(List<String> matchIds) {
        String ids = numericInClause(matchIds);
        QueryResult result = LeagueDB.get().query(
            "SELECT m.id AS match_id, m.bans, m.events, m.time_start, m.time_end "
            + "FROM `match` m WHERE m.id IN (" + ids + ")"
        );
        Map<String, MatchMeta> metadata = new LinkedHashMap<>();
        for (QueryRecord record : result) {
            metadata.put(record.get("match_id"), new MatchMeta(
                record.get("bans"),
                record.get("events"),
                timestamp(record, "time_start"),
                timestamp(record, "time_end")
            ));
        }
        return metadata;
    }

    private static List<RawParticipant> loadRawParticipants(List<String> matchIds) {
        String ids = numericInClause(matchIds);
        QueryResult result = LeagueDB.get().query(
            "SELECT p.champion, p.lane, p.win, p.team, p.match_id, p.kda, p.cs, "
            + "p.gold_earned, p.summoner_id FROM participant p WHERE p.match_id IN (" + ids + ")"
        );
        List<RawParticipant> rawParticipants = new ArrayList<>();
        for (QueryRecord record : result) {
            rawParticipants.add(new RawParticipant(
                record.getAsInt("champion"),
                record.getAsLaneType("lane"),
                record.getAsBoolean("win"),
                record.getAsTeamType("team"),
                record.get("match_id"),
                record.get("kda"),
                nullableInt(record.get("cs")),
                nullableInt(record.get("gold_earned")),
                record.getAsInt("summoner_id")
            ));
        }
        System.out.println("stats participant batch loaded: " + result.size() + " rows");
        return rawParticipants;
    }

    private static Map<Integer, String> loadPuuids(List<RawParticipant> rawParticipants) {
        Map<Integer, Boolean> uniqueSummonerIds = new LinkedHashMap<>();
        for (RawParticipant participant : rawParticipants) {
            if (participant.summonerId() > 0) uniqueSummonerIds.put(participant.summonerId(), Boolean.TRUE);
        }
        List<String> summonerIds = new ArrayList<>();
        for (Integer summonerId : uniqueSummonerIds.keySet()) summonerIds.add(String.valueOf(summonerId));

        Map<Integer, String> puuidBySummoner = new HashMap<>();
        for (int start = 0; start < summonerIds.size(); start += GAME_BATCH_SIZE) {
            int end = Math.min(start + GAME_BATCH_SIZE, summonerIds.size());
            String ids = numericInClause(summonerIds.subList(start, end));
            if (ids.isBlank()) continue;
            System.out.println("stats summoner chunk " + (start / GAME_BATCH_SIZE + 1)
                + "/" + batchCount(summonerIds.size()) + " started");
            QueryResult result = LeagueDB.get().query(
                "SELECT id, puuid FROM summoner WHERE id IN (" + ids + ")"
            );
            for (QueryRecord record : result)
                puuidBySummoner.put(record.getAsInt("id"), record.get("puuid"));
            System.out.println("stats summoner chunk " + (start / GAME_BATCH_SIZE + 1)
                + " completed: " + result.size() + " rows");
        }
        summonerIds.clear();
        uniqueSummonerIds.clear();
        return puuidBySummoner;
    }

    private static void processMatch(String matchId, Filter filter, MatchBatch batch, Aggregation aggregation) {
        MatchMeta metadata = batch.metadata().remove(matchId);
        List<Row> match = batch.byMatch().remove(matchId);
        if (metadata == null || match == null || match.isEmpty()) return;

        aggregation.totalGames++;
        if (json(metadata.bans()) != null) aggregation.banGames++;
        addBans(metadata.bans(), aggregation.banCount);
        MatchData matchData = parseMatchData(match, metadata);
        Map<TeamType, List<Row>> byTeam = new HashMap<>();
        for (Row row : match) byTeam.computeIfAbsent(row.team(), ignored -> new ArrayList<>()).add(row);

        for (Row player : match) {
            if (!isTargetLane(player, filter)) continue;
            int[] pick = aggregation.pickWin.computeIfAbsent(player.champion(), ignored -> new int[2]);
            pick[0]++;
            if (player.win()) pick[1]++;

            int[] lane = aggregation.laneAccum.computeIfAbsent(player.champion(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(player.lane(), ignored -> new int[2]);
            lane[0]++;
            if (player.win()) lane[1]++;

            aggregation.metrics.computeIfAbsent(player.champion(), ignored -> new MetricAccumulator())
                .add(player, matchData);
            addPowerCurve(aggregation.powerCurve, player);

            List<Row> allies = byTeam.getOrDefault(player.team(), List.of());
            for (Row ally : allies) {
                if (ally == player || !compatibleSynergy(player.lane(), ally.lane())
                        || player.champion() == ally.champion()) continue;
                int[] synergy = aggregation.synergyAccum.computeIfAbsent(player.champion(), ignored -> new HashMap<>())
                    .computeIfAbsent(new SynergyKey(ally.champion(), ally.lane()), ignored -> new int[2]);
                synergy[0]++;
                if (player.win()) synergy[1]++;
            }
        }

        List<List<Row>> sides = new ArrayList<>(byTeam.values());
        if (sides.size() == 2) {
            accumMatchups(sides.get(0), sides.get(1), matchData, filter, aggregation.matchupAccum);
            accumMatchups(sides.get(1), sides.get(0), matchData, filter, aggregation.matchupAccum);
        }
        match.clear();
    }

    private static String matchLanePredicate(Filter filter) {
        if (filter.lane() == null || !GameQueueTypeUtils.hasLane(filter.queue())) return "";
        return " AND EXISTS (SELECT 1 FROM participant lane_filter"
            + " WHERE lane_filter.match_id = m.id"
            + " AND lane_filter.lane = '" + filter.lane() + "')";
    }

    private static String numericInClause(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            try {
                Long.parseLong(value);
                if (result.length() > 0) result.append(',');
                result.append(value);
            } catch (NumberFormatException ignored) {}
        }
        return result.toString();
    }

    private static long lastMatchId(List<String> matchIds) {
        return Long.parseLong(matchIds.get(matchIds.size() - 1));
    }

    private static long batchCount(long total) {
        return total == 0 ? 0 : (total + GAME_BATCH_SIZE - 1) / GAME_BATCH_SIZE;
    }

    private static Map<Integer, Trend> computeTrends(Filter filter, Map<Integer, int[]> current) {
        if (filter.patch() == null) return Map.of();

        String currentPatch;
        String previousPatch;
        try {
            currentPatch = PatchUtils.getPatch();
            previousPatch = PatchUtils.getPreviousPatch();
        } catch (RuntimeException ignored) {
            return Map.of();
        }
        if (!filter.patch().equals(currentPatch) || previousPatch == null || previousPatch.isBlank()) return Map.of();

        Filter previousFilter = new Filter()
            .setPatch(previousPatch)
            .setQueue(filter.queue())
            .setRank(filter.rank())
            .setRegion(filter.region())
            .setLane(filter.lane());
        Map<Integer, int[]> previous = new HashMap<>();
        long lastMatchId = 0;
        long previousGames = 0;
        long previousTotal = loadMatchCount(previousFilter);
        long previousBatches = batchCount(previousTotal);
        int batchNumber = 0;
        while (true) {
            List<String> matchIds = loadMatchIds(previousFilter, lastMatchId);
            if (matchIds.isEmpty()) break;
            batchNumber++;
            System.out.println("previous batch " + batchNumber + "/" + previousBatches
                + " started: " + matchIds.size() + " games");
            String ids = numericInClause(matchIds);
            QueryResult result = LeagueDB.get().query(
                "SELECT p.champion, p.lane, p.win FROM participant p WHERE p.match_id IN (" + ids + ")"
            );
            for (QueryRecord record : result) {
                LaneType lane = record.getAsLaneType("lane");
                if (filter.lane() != null && lane != filter.lane()) continue;
                int champion = record.getAsInt("champion");
                int[] values = previous.computeIfAbsent(champion, ignored -> new int[2]);
                values[0]++;
                if (record.getAsBoolean("win")) values[1]++;
            }
            previousGames += matchIds.size();
            lastMatchId = lastMatchId(matchIds);
            matchIds.clear();
            System.out.println("previous batch " + batchNumber + "/" + previousBatches
                + " completed: " + result.size() + " rows");
        }
        System.out.println("previous matches elaborated");

        Map<Integer, Trend> trends = new HashMap<>();
        for (Integer champion : current.keySet()) {
            int[] values = previous.get(champion);
            if (values == null || values[0] == 0) continue;
            double previousWinrate = rate(values[1], values[0]);
            double currentWinrate = rate(current.get(champion)[1], current.get(champion)[0]);
            trends.put(champion, new Trend(previousPatch, values[0], previousWinrate,
                currentWinrate - previousWinrate));
        }
        return previousGames == 0 ? Map.of() : trends;
    }

    private static boolean isTargetLane(Row row, Filter filter) {
        return filter.lane() == null || row.lane() == filter.lane();
    }

    private static void accumMatchups(List<Row> team, List<Row> enemies, MatchData data, Filter filter,
                                      Map<Integer, Map<MatchupKey, MatchupAccumulator>> accum) {
        for (Row player : team) {
            if (!isTargetLane(player, filter) || player.lane() == null) continue;
            for (Row opponent : enemies) {
                if (opponent.lane() != player.lane() || opponent.champion() == player.champion()) continue;
                MatchupKey key = new MatchupKey(opponent.champion(), opponent.lane());
                MatchupAccumulator value = accum.computeIfAbsent(player.champion(), ignored -> new HashMap<>())
                    .computeIfAbsent(key, ignored -> new MatchupAccumulator());
                value.add(player, opponent, data);
            }
        }
    }

    private static boolean compatibleSynergy(LaneType primary, LaneType ally) {
        return (primary == LaneType.BOT && ally == LaneType.UTILITY)
            || (primary == LaneType.UTILITY && ally == LaneType.BOT);
    }

    private static void addPowerCurve(Map<Integer, Map<String, int[]>> powerCurve, Row row) {
        long duration = row.timeEnd() - row.timeStart();
        if (duration <= 0) return;
        long minutes = duration / 60000;
        String bucket = minutes <= 15 ? POWER_BUCKETS.get(0)
            : minutes <= 25 ? POWER_BUCKETS.get(1)
            : minutes <= 35 ? POWER_BUCKETS.get(2)
            : minutes <= 45 ? POWER_BUCKETS.get(3)
            : POWER_BUCKETS.get(4);
        int[] values = powerCurve.computeIfAbsent(row.champion(), ignored -> new LinkedHashMap<>())
            .computeIfAbsent(bucket, ignored -> new int[2]);
        values[0]++;
        if (row.win()) values[1]++;
    }

    private static List<PowerCurvePoint> toPowerCurve(Map<String, int[]> values) {
        List<PowerCurvePoint> result = new ArrayList<>();
        for (String bucket : POWER_BUCKETS) {
            int[] value = values.get(bucket);
            if (value != null) result.add(new PowerCurvePoint(bucket, value[0], value[1], rate(value[1], value[0])));
        }
        return result;
    }

    private static MatchData parseMatchData(List<Row> rows, MatchMeta metadata) {
        JSONObject events = json(metadata == null ? null : metadata.events());
        if (events == null) return new MatchData(Map.of(), Map.of(), false);

        JSONObject participantRefs = events.optJSONObject("participants");
        Map<String, Row> byPuuid = new HashMap<>();
        for (Row row : rows) if (row.puuid() != null) byPuuid.put(row.puuid(), row);

        Map<String, EventCounter> counters = new HashMap<>();
        Map<TeamType, Integer> teamKills = new HashMap<>();
        JSONArray kills = events.optJSONArray("champion_kills");
        boolean available = kills != null;
        if (kills != null) {
            for (int i = 0; i < kills.length(); i++) {
                JSONObject kill = kills.optJSONObject(i);
                if (kill == null) continue;
                Row killer = resolve(kill.opt("killer"), participantRefs, byPuuid);
                if (killer != null) {
                    EventCounter counter = counters.computeIfAbsent(killer.puuid(), ignored -> new EventCounter());
                    counter.kills++;
                    JSONArray assists = kill.optJSONArray("assists");
                    if (assists == null || assists.length() == 0) counter.soloKills++;
                    if (killer.team() != null) teamKills.merge(killer.team(), 1, Integer::sum);
                }
                Row victim = resolve(kill.opt("victim"), participantRefs, byPuuid);
                if (victim != null) counters.computeIfAbsent(victim.puuid(), ignored -> new EventCounter()).deaths++;
                JSONArray assists = kill.optJSONArray("assists");
                if (assists != null) {
                    for (int j = 0; j < assists.length(); j++) {
                        Row assister = resolve(assists.opt(j), participantRefs, byPuuid);
                        if (assister != null) counters.computeIfAbsent(assister.puuid(), ignored -> new EventCounter()).assists++;
                    }
                }
            }
        }

        Map<String, EventMetric> metrics = new HashMap<>();
        for (Row row : rows) {
            if (row.puuid() == null || row.puuid().isBlank()) continue;
            EventCounter counter = counters.getOrDefault(row.puuid(), new EventCounter());
            int killsForTeam = row.team() == null ? 0 : teamKills.getOrDefault(row.team(), 0);
            metrics.put(row.puuid(), new EventMetric(counter.kills, counter.soloKills, counter.assists,
                killsForTeam, counter.deaths, available));
        }

        Map<String, Snapshot> snapshots = new HashMap<>();
        JSONArray snapshotArray = events.optJSONArray("snapshots");
        if (snapshotArray != null && participantRefs != null) {
            for (int i = 0; i < snapshotArray.length(); i++) {
                JSONObject snapshot = snapshotArray.optJSONObject(i);
                if (snapshot == null || snapshot.optLong("timestamp", -1) != AT_15_MS) continue;
                JSONObject participants = snapshot.optJSONObject("participants");
                if (participants == null) continue;
                for (String participantId : participants.keySet()) {
                    String puuid = participantRefs.optString(participantId, null);
                    JSONObject values = participants.optJSONObject(participantId);
                    if (puuid != null && values != null)
                        snapshots.put(puuid, new Snapshot(nullableInt(values, "cs"), nullableInt(values, "total_gold")));
                }
            }
        }
        return new MatchData(metrics, snapshots, available);
    }

    private static final class EventCounter {
        int kills;
        int soloKills;
        int assists;
        int deaths;
    }

    private static Row resolve(Object value, JSONObject refs, Map<String, Row> byPuuid) {
        if (value == null || value == JSONObject.NULL) return null;
        String key = String.valueOf(value);
        String puuid = refs == null ? key : refs.optString(key, key);
        return byPuuid.get(puuid);
    }

    private static void addBans(String raw, Map<Integer, int[]> bans) {
        JSONObject object = json(raw);
        if (object == null) return;
        for (String key : object.keySet()) {
            JSONArray values = object.optJSONArray(key);
            if (values == null) continue;
            for (int i = 0; i < values.length(); i++) {
                int champion = values.optInt(i, 0);
                if (champion != 0) bans.computeIfAbsent(champion, ignored -> new int[1])[0]++;
            }
        }
    }

    private static JSONObject json(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return new JSONObject(raw); }
        catch (RuntimeException ignored) { return null; }
    }

    private static Integer nullableInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Integer nullableInt(JSONObject object, String key) {
        return object.has(key) && !object.isNull(key) ? object.optInt(key) : null;
    }

    private static long timestamp(QueryRecord record, String column) {
        String value = record.get(column);
        if (value == null || value.isBlank()) return 0;
        try {
            return Timestamp.valueOf(record.getAsLocalDateTime(column)).getTime();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static double durationMinutes(Row row) {
        long duration = row.timeEnd() - row.timeStart();
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

    private static double rate(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / denominator : 0;
    }
}
