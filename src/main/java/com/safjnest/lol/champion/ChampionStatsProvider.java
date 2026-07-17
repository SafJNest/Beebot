package com.safjnest.lol.champion;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChampionStatsProvider {

    public static final int GAME_BATCH_SIZE = 1000;

    private ChampionStatsProvider() {}

    public static long loadMatchCount(Filter filter) {
        QueryResult result = LeagueDB.get().query(
            "SELECT COUNT(*) AS total_matches FROM `match` m "
            + filter.sqlMatchOnly() + matchLanePredicate(filter)
        );
        long count = result.isEmpty() ? 0 : result.get(0).getAsLong("total_matches");
        result.clear();
        return count;
    }

    public static List<String> loadMatchIds(Filter filter, long lastId) {
        QueryResult result = LeagueDB.get().query(
            "SELECT m.id AS match_id FROM `match` m "
            + filter.sqlMatchOnly()
            + " AND m.id > " + lastId
            + matchLanePredicate(filter)
            + " ORDER BY m.id ASC LIMIT " + GAME_BATCH_SIZE
        );
        List<String> matchIds = new ArrayList<>();
        for (QueryRecord record : result) matchIds.add(record.get("match_id"));
        result.clear();
        return matchIds;
    }

    public static ChampionStatsData.RawBatch loadBatch(List<String> matchIds) {
        Map<String, ChampionStatsData.MatchMeta> metadata = loadMatchMetadata(matchIds);
        List<ChampionStatsData.RawParticipant> rawParticipants = loadRawParticipants(matchIds);
        Map<Integer, String> puuidBySummoner = loadPuuids(rawParticipants);
        Map<String, List<ChampionStatsData.RawParticipant>> byMatch = new LinkedHashMap<>();

        for (ChampionStatsData.RawParticipant participant : rawParticipants) {
            if (!metadata.containsKey(participant.matchId())) continue;
            ChampionStatsData.RawParticipant resolved = new ChampionStatsData.RawParticipant(
                participant.champion(), participant.lane(), participant.win(), participant.team(),
                participant.matchId(), participant.kda(), participant.cs(), participant.gold(),
                participant.summonerId(), puuidBySummoner.get(participant.summonerId())
            );
            byMatch.computeIfAbsent(participant.matchId(), ignored -> new ArrayList<>()).add(resolved);
        }

        rawParticipants.clear();
        puuidBySummoner.clear();
        return new ChampionStatsData.RawBatch(metadata, byMatch);
    }

    public static QueryResult loadTrendParticipants(List<String> matchIds) {
        String ids = numericInClause(matchIds);
        if (ids.isBlank()) return new QueryResult();
        return LeagueDB.get().query(
            "SELECT p.champion, p.lane, p.win FROM participant p WHERE p.match_id IN (" + ids + ")"
        );
    }

    public static long batchCount(long total) {
        return total == 0 ? 0 : (total + GAME_BATCH_SIZE - 1) / GAME_BATCH_SIZE;
    }

    public static ChampionStatsData.RawMatch take(ChampionStatsData.RawBatch batch, String matchId) {
        return new ChampionStatsData.RawMatch(matchId, batch.metadata().remove(matchId),
            batch.participants().remove(matchId));
    }

    public static void clear(ChampionStatsData.RawBatch batch) {
        batch.metadata().clear();
        batch.participants().clear();
    }

    private static Map<String, ChampionStatsData.MatchMeta> loadMatchMetadata(List<String> matchIds) {
        String ids = numericInClause(matchIds);
        QueryResult result = LeagueDB.get().query(
            "SELECT m.id AS match_id, m.bans, m.events, m.time_start, m.time_end "
            + "FROM `match` m WHERE m.id IN (" + ids + ")"
        );
        Map<String, ChampionStatsData.MatchMeta> metadata = new LinkedHashMap<>();
        for (QueryRecord record : result) {
            metadata.put(record.get("match_id"), new ChampionStatsData.MatchMeta(
                record.get("bans"), record.get("events"),
                timestamp(record, "time_start"), timestamp(record, "time_end")
            ));
        }
        result.clear();
        return metadata;
    }

    private static List<ChampionStatsData.RawParticipant> loadRawParticipants(List<String> matchIds) {
        String ids = numericInClause(matchIds);
        QueryResult result = LeagueDB.get().query(
            "SELECT p.champion, p.lane, p.win, p.team, p.match_id, p.kda, p.cs, "
            + "p.gold_earned, p.summoner_id FROM participant p WHERE p.match_id IN (" + ids + ")"
        );
        List<ChampionStatsData.RawParticipant> rawParticipants = new ArrayList<>();
        for (QueryRecord record : result) {
            rawParticipants.add(new ChampionStatsData.RawParticipant(
                record.getAsInt("champion"), record.getAsLaneType("lane"), record.getAsBoolean("win"),
                record.getAsTeamType("team"), record.get("match_id"), record.get("kda"),
                nullableInt(record.get("cs")), nullableInt(record.get("gold_earned")),
                record.getAsInt("summoner_id"), null
            ));
        }
        int resultSize = result.size();
        result.clear();
        System.out.println("stats participant batch loaded: " + resultSize + " rows");
        return rawParticipants;
    }

    private static Map<Integer, String> loadPuuids(List<ChampionStatsData.RawParticipant> rawParticipants) {
        Map<Integer, Boolean> uniqueSummonerIds = new LinkedHashMap<>();
        for (ChampionStatsData.RawParticipant participant : rawParticipants)
            if (participant.summonerId() > 0) uniqueSummonerIds.put(participant.summonerId(), Boolean.TRUE);

        List<String> summonerIds = new ArrayList<>();
        for (Integer summonerId : uniqueSummonerIds.keySet()) summonerIds.add(String.valueOf(summonerId));
        Map<Integer, String> puuidBySummoner = new HashMap<>();
        for (int start = 0; start < summonerIds.size(); start += GAME_BATCH_SIZE) {
            int end = Math.min(start + GAME_BATCH_SIZE, summonerIds.size());
            String ids = numericInClause(summonerIds.subList(start, end));
            if (ids.isBlank()) continue;
            System.out.println("stats summoner chunk " + (start / GAME_BATCH_SIZE + 1)
                + "/" + batchCount(summonerIds.size()) + " started");
            QueryResult result = LeagueDB.get().query("SELECT id, puuid FROM summoner WHERE id IN (" + ids + ")");
            for (QueryRecord record : result)
                puuidBySummoner.put(record.getAsInt("id"), record.get("puuid"));
            int resultSize = result.size();
            result.clear();
            System.out.println("stats summoner chunk " + (start / GAME_BATCH_SIZE + 1)
                + " completed: " + resultSize + " rows");
        }
        summonerIds.clear();
        uniqueSummonerIds.clear();
        return puuidBySummoner;
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

    private static Integer nullableInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static long timestamp(QueryRecord record, String column) {
        String value = record.get(column);
        if (value == null || value.isBlank()) return 0;
        try { return Timestamp.valueOf(record.getAsLocalDateTime(value)).getTime(); }
        catch (RuntimeException ignored) { return 0; }
    }
}
