package com.safjnest.lol.champion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.mongo.MongoDB;
import com.safjnest.mongo.MongoRecord;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;

public final class ChampionStatsProvider {

    public static final int GAME_BATCH_SIZE = 1000;

    private ChampionStatsProvider() {}

    public static long loadMatchCount(Filter filter) {
        return MongoDB.countChampionMatchesByFilter(filter);
    }

    public static List<String> loadMatchIds(Filter filter, long lastId) {
        return MongoDB.findChampionLegacyMatchIds(filter, lastId, GAME_BATCH_SIZE);
    }

    public static ChampionStatsData.RawBatch loadBatch(List<String> matchIds) {
        Map<String, ChampionStatsData.MatchMeta> metadata = new LinkedHashMap<>();
        Map<String, List<ChampionStatsData.RawParticipant>> byMatch = new LinkedHashMap<>();
        for (MongoRecord record : MongoDB.findChampionRecordsByLegacyIds(matchIds)) {
            Match match = MongoDB.read(record, Match.class);
            String matchId = String.valueOf(match.id);
            metadata.put(matchId, new ChampionStatsData.MatchMeta(
                    new JSONObject(match.bans == null ? Map.of() : match.bans).toString(),
                    new JSONObject(match.eventData == null ? Map.of() : match.eventData).toString(),
                    match.timeStart,
                    match.timeEnd
            ));
            List<ChampionStatsData.RawParticipant> participants = byMatch.computeIfAbsent(matchId, ignored -> new ArrayList<>());
            if (match.participants == null) continue;
            for (Participant participant : match.participants) {
                participants.add(new ChampionStatsData.RawParticipant(
                        participant.champion, participant.lane, participant.win, participant.team,
                        matchId, participant.kda, participant.cs, participant.goldEarned,
                        participant.summonerId, participant.puuid
                ));
            }
        }
        return new ChampionStatsData.RawBatch(metadata, byMatch);
    }

    public static QueryResult loadTrendParticipants(List<String> matchIds) {
        QueryResult result = new QueryResult();
        for (MongoRecord record : MongoDB.findChampionRecordsByLegacyIds(matchIds)) {
            Match match = MongoDB.read(record, Match.class);
            if (match.participants == null) continue;
            for (Participant participant : match.participants) {
                QueryRecord row = new QueryRecord();
                row.put("champion", String.valueOf(participant.champion));
                row.put("lane", participant.lane == null ? null : participant.lane.name());
                row.put("win", String.valueOf(participant.win));
                result.add(row);
            }
        }
        result.setSuccess(true);
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
}
