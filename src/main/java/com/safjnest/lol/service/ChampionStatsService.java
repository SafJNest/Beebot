package com.safjnest.lol.service;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.lol.build.Filter;
import com.safjnest.lol.model.ChampionStats;
import com.safjnest.lol.model.ChampionStats.LaneStat;
import com.safjnest.lol.model.ChampionStats.Matchup;
import com.safjnest.lol.model.ChampionStats.MatchupKey;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class ChampionStatsService {

    public ChampionStatsService() {
    }

    private record Row(int champion, LaneType lane, boolean win, TeamType team, String matchId, String bans) {}

    public Map<Integer, ChampionStats> getAll(Filter filter) {
        Map<Integer, ChampionStats> cached = LeagueDB.getChampionStats(filter);
        if (cached != null) return cached;

        Map<Integer, ChampionStats> computed = compute(filter);
        return computed;
    }

    public ChampionStats get(Filter filter) {
        String key = RedisKey.CHAMPION_STATS.of(filter.genericKey(), filter.champion());
        ChampionStats stats = RedisClient.get(key, ChampionStats.class);
        if (stats != null) return stats;

        stats = LeagueDB.getChampionStats(filter, filter.champion());
        if (stats != null) {
            RedisClient.set(key, stats, 0);
            return stats;
        }

        Map<Integer, ChampionStats> computed = compute(filter);
        stats = computed != null ? computed.get(filter.champion()) : null;
        if (stats != null) RedisClient.set(key, stats, 0);
        return stats;
    }

    public Map<Integer, ChampionStats> compute(Filter filter) {
        QueryResult result = LeagueDB.get().query(
            "SELECT p.champion, p.lane, p.win, p.team, m.id AS match_id, m.bans " +
            filter.sqlAllParticipants()
        );

        Map<String, List<Row>> byMatch = new LinkedHashMap<>();
        for (QueryRecord r : result) {
            Row row = new Row(
                Integer.parseInt(r.get("champion")),
                r.getAsLaneType("lane"),
                r.getAsBoolean("win"),
                r.getAsTeamType("team"),
                r.get("match_id"),
                r.get("bans")
            );
            byMatch.computeIfAbsent(row.matchId(), k -> new ArrayList<>()).add(row);
        }

        int totalGames = byMatch.size();

        Map<Integer, int[]>                  pickWin      = new HashMap<>();
        Map<Integer, int[]>                  banCount     = new HashMap<>();
        Map<Integer, Map<LaneType, int[]>>   laneAccum    = new HashMap<>();
        Map<Integer, Map<MatchupKey, int[]>> matchupAccum = new HashMap<>();

        for (List<Row> match : byMatch.values()) {
            JSONObject bansObj = new JSONObject(match.get(0).bans());
            for (String key : bansObj.keySet()) {
                JSONArray bans = bansObj.getJSONArray(key);
                for (int i = 0; i < bans.length(); i++) {
                    banCount.computeIfAbsent(bans.getInt(i), k -> new int[1])[0]++;
                }
            }

            Map<TeamType, List<Row>> byTeam = new HashMap<>();
            for (Row p : match) {
                byTeam.computeIfAbsent(p.team(), k -> new ArrayList<>()).add(p);

                int[] pw = pickWin.computeIfAbsent(p.champion(), k -> new int[2]);
                pw[0]++;
                if (p.win()) pw[1]++;

                int[] lpw = laneAccum
                    .computeIfAbsent(p.champion(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(p.lane(), k -> new int[2]);
                lpw[0]++;
                if (p.win()) lpw[1]++;
            }

            List<List<Row>> sides = new ArrayList<>(byTeam.values());
            if (sides.size() == 2) {
                accumMatchups(sides.get(0), sides.get(1), matchupAccum);
                accumMatchups(sides.get(1), sides.get(0), matchupAccum);
            }
        }

        Map<Integer, ChampionStats> stats = new HashMap<>();
        for (int champ : pickWin.keySet()) {
            int[] pw  = pickWin.get(champ);
            int[] bc  = banCount.getOrDefault(champ, new int[1]);
            int picks = pw[0], wins = pw[1], bans = bc[0];

            double winrate  = picks > 0      ? (double) wins  / picks      : 0;
            double pickrate = totalGames > 0 ? (double) picks / totalGames : 0;
            double banrate  = totalGames > 0 ? (double) bans  / totalGames : 0;

            List<LaneStat> laneStats = laneAccum.getOrDefault(champ, Map.of()).entrySet().stream()
                .map(e -> new LaneStat(e.getKey(), e.getValue()[0],
                    e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
                .sorted(Comparator.comparingInt(LaneStat::games).reversed())
                .toList();

            Map<MatchupKey, Matchup> matchups = new LinkedHashMap<>();
            matchupAccum.getOrDefault(champ, Map.of()).forEach((key, val) ->
                matchups.put(key, new Matchup(key.champion(), val[0],
                    val[0] > 0 ? (double) val[1] / val[0] : 0))
            );

            Filter champFilter = new Filter()
                .setChampion(champ)
                .setPatch(filter.patch())
                .setQueue(filter.queue())
                .setRank(filter.rank())
                .setRegion(filter.region());
            
            stats.put(champ, new ChampionStats(
                champFilter, totalGames, picks, bans, wins,
                winrate, pickrate, banrate,
                laneStats, matchups
            ));
        }

        if (stats != null && !stats.isEmpty()) {
            stats.values().forEach(stat -> {
                ChronoTask a = () -> LeagueDB.saveChampionStats(stat);
                String key = RedisKey.CHAMPION_STATS.of(stat.filter().genericKey(), stat.filter().champion());
                RedisClient.set(key, stat, 0);
                a.queue();
            });
        }
        return stats;
    }

    private void accumMatchups(List<Row> team, List<Row> enemies,
            Map<Integer, Map<MatchupKey, int[]>> accum) {
        for (Row p : team) {
            for (Row opp : enemies) {
                if (p.lane() != opp.lane()) continue;
                MatchupKey key = new MatchupKey(opp.champion(), opp.lane());
                int[] mw = accum
                    .computeIfAbsent(p.champion(), k -> new HashMap<>())
                    .computeIfAbsent(key, k -> new int[2]);
                mw[0]++;
                if (p.win()) mw[1]++;
            }
        }
    }
}