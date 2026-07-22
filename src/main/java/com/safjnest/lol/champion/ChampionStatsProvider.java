package com.safjnest.lol.champion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.json.JSONObject;

import com.safjnest.lol.model.Filter;
import com.safjnest.mongo.MongoDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryRecordParser;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;

public final class ChampionStatsProvider {

    public static final int GAME_BATCH_SIZE = 1000;

    private ChampionStatsProvider() {}

    public static long loadMatchCount(Filter filter) {
        return MongoDB.countChampionMatchesByFilter(filter);
    }

    public static List<String> loadMatchIds(Filter filter, String lastFullGameId) {
        return MongoDB.findChampionMatchIds(filter, lastFullGameId, GAME_BATCH_SIZE);
    }

    public static ChampionStatsData.RawBatch loadBatch(List<String> matchIds) {
        Map<String, ChampionStatsData.MatchMeta> metadata = new LinkedHashMap<>();
        Map<String, List<ChampionStatsData.RawParticipant>> byMatch = new LinkedHashMap<>();
        for (Document document : MongoDB.findChampionRawDocuments(matchIds)) {
            String matchId = String.valueOf(document.get("_id"));
            metadata.put(matchId, new ChampionStatsData.MatchMeta(
                    json(document.get("bans")), json(document.get("events")),
                    number(document.get("timeStart")), number(document.get("timeEnd"))
            ));
            List<ChampionStatsData.RawParticipant> participants = byMatch.computeIfAbsent(matchId, ignored -> new ArrayList<>());
            for (Document participant : participants(document.get("participants"))) {
                participants.add(new ChampionStatsData.RawParticipant(
                        participant.getInteger("champion", 0), lane(participant), participant.getBoolean("win", false),
                        team(participant), matchId, participant.getString("kda"), participant.getInteger("cs", 0),
                        participant.getInteger("goldEarned", 0), participant.getString("puuid")
                ));
            }
        }
        return new ChampionStatsData.RawBatch(metadata, byMatch);
    }

    public static List<QueryRecord> loadTrendParticipants(List<String> matchIds) {
        List<QueryRecord> result = new ArrayList<>();
        for (Document document : MongoDB.findChampionRawDocuments(matchIds)) {
            for (Document participant : participants(document.get("participants"))) {
                LaneType lane = lane(participant);
                result.add(QueryRecordParser.fromMap(Map.of(
                        "champion", participant.getInteger("champion", 0),
                        "lane", lane == null ? "" : lane.name(),
                        "win", participant.getBoolean("win", false))));
            }
        }
        return result;
    }

    public static long batchCount(long total) {
        return total == 0 ? 0 : (total + GAME_BATCH_SIZE - 1) / GAME_BATCH_SIZE;
    }

    public static ChampionStatsData.RawMatch take(ChampionStatsData.RawBatch batch, String matchId) {
        return new ChampionStatsData.RawMatch(matchId, batch.metadata().remove(matchId), batch.participants().remove(matchId));
    }

    public static void clear(ChampionStatsData.RawBatch batch) {
        batch.metadata().clear();
        batch.participants().clear();
    }

    private static List<Document> participants(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        List<Document> result = new ArrayList<>(values.size());
        for (Object item : values) if (item instanceof Document document) result.add(document);
        return result;
    }

    private static String json(Object value) {
        if (!(value instanceof Map<?, ?> map) || map.isEmpty()) return "{}";
        return new JSONObject(map).toString();
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }

    private static LaneType lane(Document participant) {
        try { return LaneType.valueOf(participant.getString("lane")); }
        catch (RuntimeException ignored) { return null; }
    }

    private static TeamType team(Document participant) {
        try { return TeamType.valueOf(participant.getString("team")); }
        catch (RuntimeException ignored) { return null; }
    }
}
