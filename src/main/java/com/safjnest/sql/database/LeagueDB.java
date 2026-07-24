package com.safjnest.sql.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.utils.ParticipantBuildCodec;
import com.safjnest.sql.AbstractDB;
import com.safjnest.utils.SettingsLoader;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public final class LeagueDB extends AbstractDB {

    private static final LeagueDB INSTANCE = new LeagueDB();

    private LeagueDB() {
    }

    @Override
    protected String getDatabase() {
        return SettingsLoader.getSettings().getJsonSettings().getLeagueDatabase().getDatabaseName();
    }

    public static LeagueDB get() {
        return INSTANCE;
    }

    public static List<Match> getMatchesByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        StringBuilder placeholders = new StringBuilder("?");
        for (int index = 1; index < ids.size(); index++) placeholders.append(",?");
        String query = "SELECT * FROM `match` WHERE id IN (" + placeholders + ") ORDER BY id ASC";

        try (Connection connection = INSTANCE.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < ids.size(); index++) statement.setInt(index + 1, ids.get(index));
            try (ResultSet result = statement.executeQuery()) {
                Map<Integer, Match> matches = readMatches(result);
                loadParticipants(connection, matches);
                connection.commit();
                return new ArrayList<>(matches.values());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read MariaDB match migration ids=" + ids.size(), exception);
        }
    }

    // ============================================================================

    private static Map<Integer, Match> readMatches(ResultSet result) throws SQLException {
        Map<Integer, Match> matches = new LinkedHashMap<>();
        while (result.next()) {
            Match match = readMatch(result);
            matches.put(match.id, match);
        }
        return matches;
    }

    private static void loadParticipants(Connection connection, Map<Integer, Match> matches) throws SQLException {
        if (matches.isEmpty()) return;

        String inClause = matches.keySet().toString().replace("[", "(").replace("]", ")");
        String query = "SELECT st.*, sm.id AS match_id, su.puuid AS puuid, su.riot_id AS riot_id "
                + "FROM participant st "
                + "JOIN `match` sm ON st.match_id = sm.id "
                + "LEFT JOIN summoner su ON su.id = st.summoner_id "
                + "WHERE st.match_id IN " + inClause + ";";

        try (var statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            while (result.next()) {
                Participant participant = readParticipant(result);
                Match match = matches.get(participant.matchId);
                if (match != null) match.participants.add(participant);
            }
        }
    }

    private static Match readMatch(ResultSet result) throws SQLException {
        Match match = new Match();
        match.id = result.getInt("id");
        match.gameId = result.getString("game_id");
        match.leagueShard = enumValue(LeagueShard.class, result.getString("region"));
        match.queue = enumValue(GameQueueType.class, result.getString("queue"));
        match.rank = enumValue(TierType.class, result.getString("rank"));
        match.lastUpdate = timestamp(result, "last_update");
        match.timeStart = timestamp(result, "time_start");
        match.timeEnd = timestamp(result, "time_end");
        match.patch = result.getString("patch");
        match.participants = new ArrayList<>();

        JSONObject bans = jsonObject(result.getString("bans"));
        boolean legacyBlueRedOrdinals = bans.has("0") && !bans.has("2");
        for (String key : bans.keySet()) {
            TeamType team;
            try {
                team = TeamType.valueOf(key);
            } catch (IllegalArgumentException ignored) {
                try {
                    int ordinal = Integer.parseInt(key);
                    if (legacyBlueRedOrdinals) ordinal++;
                    if (ordinal < 0 || ordinal >= TeamType.values().length) continue;
                    team = TeamType.values()[ordinal];
                } catch (NumberFormatException ignoredOrdinal) {
                    continue;
                }
            }
            if (team != TeamType.BLUE && team != TeamType.RED) continue;
            JSONArray values = bans.optJSONArray(key);
            List<Integer> championIds = new ArrayList<>();
            if (values != null) for (int index = 0; index < values.length(); index++) championIds.add(values.optInt(index, 0));
            match.bans.put(team, championIds);
        }
        return match;
    }

    private static Participant readParticipant(ResultSet result) throws SQLException {
        Participant participant = new Participant();
        participant.id = result.getInt("id");
        participant.summonerId = result.getInt("summoner_id");
        participant.matchId = result.getInt("match_id");
        participant.win = result.getBoolean("win");
        participant.kda = result.getString("kda");
        participant.champion = result.getInt("champion");
        participant.level = result.getInt("level");
        participant.team = enumValue(TeamType.class, result.getString("team"));
        participant.lane = enumValue(LaneType.class, result.getString("lane"));
        participant.roleQuestId = result.getInt("role_quest_id");
        participant.subTeam = result.getInt("subteam");
        participant.subTeamPlacement = result.getInt("subteam_placement");
        participant.rank = enumValue(TierDivisionType.class, result.getString("rank"));
        participant.lp = result.getInt("lp");
        participant.gain = result.getInt("gain");
        participant.damage = result.getInt("damage");
        participant.damageBuilding = result.getInt("damage_building");
        participant.healing = result.getInt("healing");
        participant.cs = result.getInt("cs");
        participant.goldEarned = result.getInt("gold_earned");
        participant.ward = result.getInt("ward");
        participant.wardKilled = result.getInt("ward_killed");
        participant.visionScore = result.getInt("vision_score");
        participant.doubles = result.getInt("doubles");
        participant.triples = result.getInt("triples");
        participant.quadruples = result.getInt("quadruples");
        participant.pentas = result.getInt("pentas");
        participant.q = result.getInt("q");
        participant.w = result.getInt("w");
        participant.e = result.getInt("e");
        participant.r = result.getInt("r");
        participant.d = result.getInt("d");
        participant.f = result.getInt("f");
        participant.puuid = result.getString("puuid");
        applyRiotId(participant, result.getString("riot_id"));

        JSONObject pings = jsonObject(result.getString("pings"));
        for (String key : pings.keySet()) participant.pings.put(key, pings.optInt(key, 0));
        ParticipantBuildCodec.apply(participant, result.getString("build"));
        return participant;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static long timestamp(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? 0 : value.getTime();
    }

    private static JSONObject jsonObject(String value) {
        return value == null || value.isBlank() ? new JSONObject() : new JSONObject(value);
    }

    private static void applyRiotId(Participant participant, String value) {
        if (value == null || value.isBlank()) return;
        String[] parts = value.split("#", 2);
        participant.riotId = parts[0];
        participant.riotTag = parts.length > 1 ? parts[1] : null;
    }
}
