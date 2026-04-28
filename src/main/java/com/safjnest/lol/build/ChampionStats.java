package com.safjnest.lol.build;

import com.safjnest.lol.build.ChampionBuild.SlotOption;

import java.util.List;

public record ChampionStats(
    BuildFilter filter,
    int games,
    int picks,
    int bans,
    int wins,
    double winrate,
    double pickrate,
    double banrate,
    List<LaneStat> laneStats,
    List<SlotOption> bestMatchups,
    List<SlotOption> worstMatchups
) {
    public record LaneStat(String lane, int games, double winrate) {}

    public void print() {
        System.out.println("Stats for " + filter().champion() + " in " + filter().lane());
        System.out.println("Games: " + games);
        System.out.println("Picks: " + picks);
        System.out.println("Bans: " + bans);
        System.out.println("Wins: " + wins);
        System.out.println("Winrate: " + winrate * 100 + "%");
        System.out.println("Pickrate: " + pickrate * 100 + "%");
        System.out.println("Banrate: " + banrate * 100 + "%");

        System.out.println("Lane stats: " + laneStats());
        System.out.println("Best matchups: " + bestMatchups());
        System.out.println("Worst matchups: " + worstMatchups());
    }
}