package com.safjnest.lol.build;

import com.safjnest.util.KryoUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

import java.util.List;
import java.util.Map;

public record ChampionStats(
    ChampionFilter filter,
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
    public record Matchup(int matches, double winrate) {}

    public String encode() {
        return KryoUtils.encode(this);
    }

    public static ChampionStats decode(String b64) {
        return KryoUtils.decode(b64, ChampionStats.class);
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