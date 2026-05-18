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

    private final static int EMBED_LIMIT = 3;
    public record LaneStat(LaneType lane, int games, double winrate) {
        public String prettyGames() {
            return String.format("%d", games);
        }

        public String prettyWinrate() {
            return String.format("%.2f", winrate * 100) + "%";
        }
    }
    public record MatchupKey(int champion, LaneType lane) {}
    public record Matchup(int champion, int matches, double winrate) {
        public String prettyMatches() {
            return String.format("%d", matches);
        }

        public String prettyWinrate() {
            return String.format("%.2f", winrate * 100) + "%";
        }
    }

    public String encode() {
        return KryoUtils.encode(this);
    }

    public static ChampionStats decode(String b64) {
        return KryoUtils.decode(b64, ChampionStats.class);
    }

    public Matchup getOpponentMatchup(int opponent, LaneType lane) {
        return matchups().get(new MatchupKey(opponent, lane));
    }

    private List<Matchup> getMatchups(LaneType lane) {
        return matchups().entrySet().stream()
            .filter(entry -> entry.getKey().lane() == lane)
            .map(Map.Entry::getValue)
            .toList();
    }

    public List<Matchup> weakAgainst(LaneType lane) {
        List<Matchup> sameLane = getMatchups(lane);

        double avgGames = sameLane.stream().mapToInt(e -> e.matches()).average().orElse(0);
    
        return sameLane.stream()
            .filter(e -> e.matches() > avgGames)
            .sorted(Comparator.comparingDouble(Matchup::winrate))
            .limit(EMBED_LIMIT)
            .toList();
    }
    
    public List<Matchup> strongAgainst(LaneType lane) {
        List<Matchup> sameLane = getMatchups(lane);
    
        double avgGames = sameLane.stream().mapToInt(e -> e.matches()).average().orElse(0);
    
        return sameLane.stream()
            .filter(e -> e.matches() > avgGames)
            .sorted(Comparator.comparingDouble(Matchup::winrate).reversed())
            .limit(EMBED_LIMIT)
            .toList();
    }

    public List<Matchup> popularMatchups(LaneType lane) {
        return getMatchups(lane).stream()
            .sorted(Comparator.comparingInt(Matchup::matches).reversed())
            .limit(EMBED_LIMIT)
            .toList();
    }

    public LaneStat getLaneStat(LaneType lane) {
        return laneStats().stream()
            .filter(l -> l.lane() == lane)
            .findFirst()
            .orElse(null);
    }

    public String prettyGames() {
        return String.format("%d", games);
    }

    public String prettyWinrate() {
        return String.format("%.2f", winrate * 100) + "%";
    }

    public String prettyPickrate() {
        return String.format("%.2f", pickrate * 100) + "%";
    }

    public String prettyBanrate() {
        return String.format("%.2f", banrate * 100) + "%";
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