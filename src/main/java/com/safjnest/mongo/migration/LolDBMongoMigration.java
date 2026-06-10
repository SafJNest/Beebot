package com.safjnest.mongo.migration;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import com.safjnest.lol.dto.MasteryDTO;
import com.safjnest.lol.dto.MatchDTO;
import com.safjnest.lol.dto.ParticipantDTO;
import com.safjnest.lol.dto.RankDTO;
import com.safjnest.lol.dto.SummonerDTO;
import com.safjnest.lol.model.Participant;
import com.safjnest.lol.utils.ParticipantBuildCodec;
import com.safjnest.mongo.LolMongoCache;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public final class LolDBMongoMigration {

    private LolDBMongoMigration() {}

    public static int migrateSummoners(int afterId, int batchSize) {
        QueryResult rows = LeagueDB.get().query(
            "SELECT * FROM summoner WHERE id > " + afterId + " ORDER BY id LIMIT " + batchSize
        );
        if (rows.isEmpty()) return afterId;

        String ids = ids(rows);
        Map<Integer, List<RankDTO>> ranks = loadRanks(ids);
        Map<Integer, List<MasteryDTO>> masteries = loadMasteries(ids);
        List<SummonerDTO> batch = new ArrayList<>(rows.size());

        for (QueryRecord row : rows) {
            int id = row.getAsInt("id");
            String riotId = row.get("riot_id");
            String[] riotIdParts = splitRiotId(riotId);

            batch.add(new SummonerDTO(
                row.get("puuid"),
                null,
                null,
                riotIdParts[0],
                riotIdParts[1],
                riotId,
                row.getAsInt("icon"),
                row.getAsLong("level"),
                0,
                enumValue(LeagueShard.class, row.get("region")),
                row.get("user_id"),
                row.getAsBoolean("ban"),
                row.getAsBoolean("tracking"),
                ranks.getOrDefault(id, List.of()),
                masteries.getOrDefault(id, List.of()),
                System.currentTimeMillis()
            ));
        }

        LolMongoCache.summoners().upsertAll(batch);
        return rows.get(rows.size() - 1).getAsInt("id");
    }

    public static int migrateMatches(int afterId, int batchSize) {
        QueryResult rows = LeagueDB.get().query(
            "SELECT * FROM `match` WHERE id > " + afterId + " ORDER BY id LIMIT " + batchSize
        );
        if (rows.isEmpty()) return afterId;

        String ids = ids(rows);
        Map<Integer, List<ParticipantDTO>> participants = loadParticipants(ids);
        List<MatchDTO> batch = new ArrayList<>(rows.size());

        for (QueryRecord row : rows) {
            int id = row.getAsInt("id");
            LeagueShard shard = enumValue(LeagueShard.class, row.get("region"));
            String gameId = shard != null ? shard.name() + "_" + row.get("game_id") : row.get("game_id");

            batch.add(new MatchDTO(
                id,
                gameId,
                shard,
                enumValue(GameQueueType.class, row.get("queue")),
                parseBans(row.get("bans")),
                row.get("events"),
                timestamp(row.get("time_start")),
                timestamp(row.get("time_end")),
                row.get("patch"),
                participants.getOrDefault(id, List.of()),
                System.currentTimeMillis()
            ));
        }

        LolMongoCache.matches().upsertAll(batch);
        return rows.get(rows.size() - 1).getAsInt("id");
    }

    private static Map<Integer, List<RankDTO>> loadRanks(String ids) {
        Map<Integer, List<RankDTO>> result = new HashMap<>();
        QueryResult rows = LeagueDB.get().query(
            "SELECT * FROM `rank` WHERE summoner_id IN (" + ids + ")"
        );
        for (QueryRecord row : rows) {
            TierDivisionType rank = enumValue(TierDivisionType.class, row.get("rank"));
            RankDTO dto = new RankDTO(
                enumValue(GameQueueType.class, row.get("queue")),
                rank != null ? rank.getTier() : null,
                rank != null ? rank.getDivision() : null,
                rank,
                null,
                row.getAsInt("lp"),
                row.getAsInt("wins"),
                row.getAsInt("losses"),
                false,
                false,
                false,
                false
            );
            result.computeIfAbsent(row.getAsInt("summoner_id"), key -> new ArrayList<>()).add(dto);
        }
        return result;
    }

    private static Map<Integer, List<MasteryDTO>> loadMasteries(String ids) {
        Map<Integer, List<MasteryDTO>> result = new HashMap<>();
        QueryResult rows = LeagueDB.get().query(
            "SELECT * FROM masteries WHERE summoner_id IN (" + ids + ")"
        );
        for (QueryRecord row : rows) {
            MasteryDTO dto = new MasteryDTO(
                row.getAsInt("champion_id"),
                row.getAsInt("champion_level"),
                row.getAsInt("champion_points"),
                0,
                0,
                false,
                timestamp(row.get("last_play_time")),
                0
            );
            result.computeIfAbsent(row.getAsInt("summoner_id"), key -> new ArrayList<>()).add(dto);
        }
        return result;
    }

    private static Map<Integer, List<ParticipantDTO>> loadParticipants(String ids) {
        Map<Integer, List<ParticipantDTO>> result = new HashMap<>();
        QueryResult rows = LeagueDB.get().query(
            "SELECT p.*, s.puuid FROM participant p "
                + "LEFT JOIN summoner s ON s.id = p.summoner_id "
                + "WHERE p.match_id IN (" + ids + ")"
        );

        for (QueryRecord row : rows) {
            Participant participant = new Participant();
            participant.id = row.getAsInt("id");
            participant.summonerId = row.getAsInt("summoner_id");
            participant.matchId = row.getAsInt("match_id");
            participant.win = row.getAsBoolean("win");
            participant.kda = row.get("kda");
            participant.champion = row.getAsInt("champion");
            participant.lane = row.getAsLaneType("lane");
            participant.team = row.getAsTeamType("team");
            participant.rank = row.getAsTier("rank");
            participant.gain = row.getAsInt("gain");
            participant.damage = row.getAsInt("damage");
            participant.damageBuilding = row.getAsInt("damage_building");
            participant.healing = row.getAsInt("healing");
            participant.cs = row.getAsInt("cs");
            participant.goldEarned = row.getAsInt("gold_earned");
            participant.ward = row.getAsInt("ward");
            participant.wardKilled = row.getAsInt("ward_killed");
            participant.visionScore = row.getAsInt("vision_score");
            participant.subTeam = row.getAsInt("subteam");
            participant.subTeamPlacement = row.getAsInt("subteam_placement");
            participant.puuid = row.get("puuid");
            participant.level = row.getAsInt("level");
            participant.doubles = row.getAsInt("doubles");
            participant.triples = row.getAsInt("triples");
            participant.quadruples = row.getAsInt("quadruples");
            participant.pentas = row.getAsInt("pentas");
            participant.q = row.getAsInt("q");
            participant.w = row.getAsInt("w");
            participant.e = row.getAsInt("e");
            participant.r = row.getAsInt("r");
            participant.d = row.getAsInt("d");
            participant.f = row.getAsInt("f");
            parsePings(participant, row.get("pings"));
            ParticipantBuildCodec.apply(participant, row.get("build"));

            result.computeIfAbsent(participant.matchId, key -> new ArrayList<>())
                .add(ParticipantDTO.from(participant));
        }
        return result;
    }

    private static Map<TeamType, Integer> parseBans(String value) {
        Map<TeamType, Integer> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) return result;

        try {
            JSONObject json = new JSONObject(value);
            for (String key : json.keySet()) {
                TeamType team = TeamType.values()[Integer.parseInt(key)];
                result.put(team, json.getJSONArray(key).isEmpty() ? null : json.getJSONArray(key).getInt(0));
            }
        } catch (RuntimeException ignored) {}
        return result;
    }

    private static void parsePings(Participant participant, String value) {
        if (value == null || value.isBlank()) return;
        try {
            JSONObject json = new JSONObject(value);
            for (String key : json.keySet()) participant.pings.put(key, json.getInt(key));
        } catch (RuntimeException ignored) {}
    }

    private static String ids(QueryResult rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (QueryRecord row : rows) ids.add(String.valueOf(row.getAsInt("id")));
        return String.join(",", ids);
    }

    private static String[] splitRiotId(String riotId) {
        if (riotId == null) return new String[] {null, null};
        int separator = riotId.lastIndexOf('#');
        if (separator < 0) return new String[] {riotId, null};
        return new String[] {riotId.substring(0, separator), riotId.substring(separator + 1)};
    }

    private static long timestamp(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Timestamp.valueOf(value).getTime();
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
