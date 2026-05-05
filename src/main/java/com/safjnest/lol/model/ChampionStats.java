package com.safjnest.lol.model;

import com.safjnest.lol.build.Filter;
import com.safjnest.util.KryoUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record ChampionStats(
    Filter filter,
    int games,
    int picks,
    int bans,
    int wins,
    double winrate,
    double pickrate,
    double banrate,
    List<LaneStat> laneStats,
    Map<MatchupKey, Matchup> matchups
) {
    public record LaneStat(LaneType lane, int games, double winrate) {}
    public record MatchupKey(int champion, LaneType lane) {}
    public record Matchup(int champion, int matches, double winrate) {}

    public String encode() {
        return KryoUtils.encode(this);
    }

    public static ChampionStats decode(String b64) {
        return KryoUtils.decode(b64, ChampionStats.class);
    }

    public Matchup getOpponentMatchup(int opponent, LaneType lane) {
        return matchups().get(new MatchupKey(opponent, lane));
    }

    public List<Matchup> weakAgainst(LaneType lane) {
        List<Map.Entry<MatchupKey, Matchup>> sameLane = matchups().entrySet().stream()
            .filter(entry -> entry.getKey().lane() == lane)
            .toList();

        double avgGames = sameLane.stream().mapToInt(e -> e.getValue().matches()).average().orElse(0);
    
        return sameLane.stream()
            .filter(entry -> entry.getValue().matches() > avgGames)
            .sorted(Comparator.comparingDouble(entry -> entry.getValue().winrate()))
            .map(Map.Entry::getValue)
            .limit(6)
            .toList();
    }
    
    public List<Matchup> strongAgainst(LaneType lane) {
        List<Map.Entry<MatchupKey, Matchup>> sameLane = matchups().entrySet().stream()
            .filter(entry -> entry.getKey().lane() == lane)
            .toList();
    
        double avgGames = sameLane.stream().mapToInt(e -> e.getValue().matches()).average().orElse(0);
    
        return sameLane.stream()
            .filter(entry -> entry.getValue().matches() > avgGames)
            .sorted(Comparator.comparingDouble(entry -> -entry.getValue().winrate()))
            .map(Map.Entry::getValue)
            .limit(6)
            .toList();
    }

    public void print() {
        System.out.println("Stats for " + filter().champion() + " in " + filter().lane());
        System.out.println("Games: "    + games);
        System.out.println("Picks: "    + picks);
        System.out.println("Bans: "     + bans);
        System.out.println("Wins: "     + wins);
        System.out.println("Winrate: "  + winrate  * 100 + "%");
        System.out.println("Pickrate: " + pickrate * 100 + "%");
        System.out.println("Banrate: "  + banrate  * 100 + "%");
        System.out.println("Lane stats: " + laneStats());
        System.out.println("Matchups: "   + matchups());
    }
}