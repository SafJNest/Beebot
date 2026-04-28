package com.safjnest.lol.build;

import com.safjnest.lol.build.ChampionBuild.SlotOption;
import com.safjnest.lol.build.ChampionStats.LaneStat;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

public class ChampionStatsService {

    private static final int MIN_GAMES     = 1;

    private record Row(int champion, String lane, boolean win, String team, String matchId, String bans) {}

    public Map<Integer, ChampionStats> getAll(BuildFilter filter) {
        QueryResult result = LeagueDB.get().query(
            "SELECT p.champion, p.lane, p.win, p.team, m.id AS match_id, m.bans " +
            filter.sqlAllParticipants()
        );

        Map<String, List<Row>> byMatch = new LinkedHashMap<>();
        for (QueryRecord r : result) {
            Row row = new Row(
                Integer.parseInt(r.get("champion")),
                r.get("lane"),
                r.getAsBoolean("win"),
                r.get("team"),
                r.get("match_id"),
                r.get("bans")
            );
            byMatch.computeIfAbsent(row.matchId(), k -> new ArrayList<>()).add(row);
        }

        int totalGames = byMatch.size();

        Map<Integer, int[]>               pickWin      = new HashMap<>();
        Map<Integer, int[]>               banCount     = new HashMap<>();
        Map<Integer, Map<String, int[]>>  laneAccum    = new HashMap<>();
        Map<Integer, Map<Integer, int[]>> matchupAccum = new HashMap<>();

        for (List<Row> match : byMatch.values()) {
            JSONObject bansObj = new JSONObject(match.get(0).bans());
            for (String key : bansObj.keySet()) {
                JSONArray bans = bansObj.getJSONArray(key);
                for (int i = 0; i < bans.length(); i++) {
                    banCount.computeIfAbsent(bans.getInt(i), k -> new int[1])[0]++;
                }
            }

            Map<String, List<Row>> byTeam = new HashMap<>();
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

            List<SlotOption> matchups = matchupAccum.getOrDefault(champ, Map.of()).entrySet().stream()
                .filter(e -> e.getValue()[0] >= MIN_GAMES)
                .map(e -> new SlotOption(e.getKey(), e.getValue()[0],
                    e.getValue()[0] > 0 ? (double) e.getValue()[1] / e.getValue()[0] : 0))
                .sorted(Comparator.comparingDouble(SlotOption::winrate).reversed())
                .toList();

            BuildFilter champFilter = new BuildFilter()
                .setChampion(champ)
                .setPatch(filter.patch())
                .setQueue(filter.queue())
                .setRegion(filter.region());

            stats.put(champ, new ChampionStats(
                champFilter, totalGames, picks, bans, wins,
                winrate, pickrate, banrate,
                laneStats,
                matchups.stream().toList(),
                matchups.reversed().stream().toList()
            ));
        }

        return stats;
    }

    public ChampionStats get(BuildFilter filter) {
        return getAll(filter).get(filter.champion());
    }

    private void accumMatchups(List<Row> team, List<Row> enemies,
            Map<Integer, Map<Integer, int[]>> accum) {
        for (Row p : team) {
            String oppLane = opponentLane(p.lane());
            for (Row opp : enemies) {
                if (oppLane != null && !oppLane.equals(opp.lane())) continue;
                int[] mw = accum
                    .computeIfAbsent(p.champion(), k -> new HashMap<>())
                    .computeIfAbsent(opp.champion(), k -> new int[2]);
                mw[0]++;
                if (p.win()) mw[1]++;
            }
        }
    }

    private String opponentLane(String lane) {
        if (lane == null) return null;
        return switch (lane) {
            case "UTILITY" -> "BOT";
            case "BOT"     -> "UTILITY";
            default        -> lane;
        };
    }

    public void print(ChampionStats stats) {
        System.out.println("Stats for " + stats.filter().champion() + " in " + stats.filter().lane());
        System.out.println("Games: "    + stats.games());
        System.out.println("Picks: "    + stats.picks());
        System.out.println("Bans: "     + stats.bans());
        System.out.println("Wins: "     + stats.wins());
        System.out.println("Winrate: "  + stats.winrate()  * 100 + "%");
        System.out.println("Pickrate: " + stats.pickrate() * 100 + "%");
        System.out.println("Banrate: "  + stats.banrate()  * 100 + "%");
        System.out.println("Lane stats: "     + stats.laneStats());
        System.out.println("Best matchups: "  + stats.bestMatchups());
        System.out.println("Worst matchups: " + stats.worstMatchups());
    }
}