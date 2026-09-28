package com.safjnest.lol.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record ChampionStatistics(
    Filter filter,
    Overview overview,
    List<LaneStat> laneStats,
    Map<Integer, Matchup> matchups,
    List<LaneSynergy> laneSynergies,
    List<PowerCurvePoint> powerCurve,
    Trend trend
) {

    private static final int EMBED_LIMIT = 3;

    public record Overview(
        int games,
        int picks,
        int bans,
        int wins,
        double winrate,
        double pickrate,
        Double banrate,
        Double kda,
        Double csPerMinute,
        Double goldPerMinute,
        DamageProfile damageProfile
    ) {}

    public record DamageProfile(Double physical, Double magic, Double trueDamage) {}

    public record LaneStat(LaneType lane, int games, double winrate) {
        public String prettyGames() {
            return String.format("%d", games);
        }

        public String prettyWinrate() {
            return String.format("%.2f", winrate * 100) + "%";
        }

        public String prettyPickrate(int totalGames) {
            return String.format("%.2f", getPickrate(totalGames)) + "%";
        }

        public double getPickrate(int totalGames) {
            return totalGames > 0 ? (double) games / totalGames * 100 : 0;
        }
    }

    public record Matchup(
        int matches,
        int wins,
        double winrate,
        Double deltaWinrate,
        Integer goldDiffAt15,
        Double csDiffAt15,
        Double soloKillRate,
        Double killParticipation,
        Double opponentBanRate,
        Integer metricGames,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double kda,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double goldPerMinute,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double deathShare,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double xpDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double killDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double levelDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double turretPlateDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double adjustedWinrate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double weightedDelta,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double matchupScore,
        @JsonInclude(JsonInclude.Include.NON_NULL) String tier,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean reliable,
        long goldDiffAt15Games,
        long csDiffAt15Games,
        long xpDiffAt15Games,
        long killDiffAt15Games,
        long levelDiffAt15Games,
        long turretPlateDiffAt15Games
    ) {
        public Matchup(
            int matches,
            int wins,
            double winrate,
            Double deltaWinrate,
            Integer goldDiffAt15,
            Double csDiffAt15,
            Double soloKillRate,
            Double killParticipation,
            Double opponentBanRate,
            Integer metricGames
        ) {
            this(matches, wins, winrate, deltaWinrate, goldDiffAt15, csDiffAt15,
                soloKillRate, killParticipation, opponentBanRate, metricGames,
                null, null, null, null, null, null, null,
                null, null, null, null, null, 0, 0, 0, 0, 0, 0);
        }

        public Matchup(int matches, double winrate) {
            this(matches, (int) Math.round(matches * winrate), winrate,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, 0, 0, 0, 0, 0, 0);
        }

        public String prettyMatches() {
            return String.format("%d", matches);
        }

        public String prettyWinrate() {
            return String.format("%.2f", winrate * 100) + "%";
        }
    }

    public record LaneSynergy(
        int allyChampion,
        LaneType allyLane,
        int matches,
        int wins,
        double winrate,
        double pickrate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double goldDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double csDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double kda,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double goldPerMinute,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double killParticipation,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double deathShare,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double xpDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double killDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double levelDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double turretPlateDiffAt15,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double adjustedWinrate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double weightedDelta,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double matchupScore,
        @JsonInclude(JsonInclude.Include.NON_NULL) String tier,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean reliable,
        long goldDiffAt15Games,
        long csDiffAt15Games,
        long xpDiffAt15Games,
        long killDiffAt15Games,
        long levelDiffAt15Games,
        long turretPlateDiffAt15Games
    ) {
        public LaneSynergy(
            int allyChampion,
            LaneType allyLane,
            int matches,
            int wins,
            double winrate,
            double pickrate
        ) {
            this(allyChampion, allyLane, matches, wins, winrate, pickrate,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, 0, 0, 0, 0, 0, 0);
        }
    }

    public record PowerCurvePoint(String durationBucket, int games, int wins, double winrate) {}

    public record Trend(String previousPatch, Integer games, Double winrate, Double deltaWinrate) {}

    public String toJson() {
        return JsonCodec.toJson(this);
    }

    public static ChampionStatistics fromJson(String json) {
        try {
            ChampionStatistics statistics = JsonCodec.fromJson(json, ChampionStatistics.class);
            if (statistics == null) return null;
            if (statistics.filter() != null && !(statistics.filter() instanceof Filter)) return null;
            if (statistics.overview() == null || statistics.laneStats() == null
                    || statistics.matchups() == null || statistics.laneSynergies() == null
                    || statistics.powerCurve() == null) return null;
            for (LaneStat lane : statistics.laneStats()) if (lane == null) return null;
            for (Map.Entry<Integer, Matchup> entry : statistics.matchups().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) return null;
            }
            return statistics;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public int games() {
        return overview.games();
    }

    public int picks() {
        return overview.picks();
    }

    public int bans() {
        return overview.bans();
    }

    public int wins() {
        return overview.wins();
    }

    public double winrate() {
        return overview.winrate();
    }

    public double pickrate() {
        return overview.pickrate();
    }

    public double banrate() {
        return overview.banrate() == null ? 0 : overview.banrate();
    }

    public Matchup getOpponentMatchup(int opponent) {
        return matchups().get(opponent);
    }

    private List<Map.Entry<Integer, Matchup>> matchupEntries() {
        return new java.util.ArrayList<>(matchups().entrySet());
    }

    public List<Map.Entry<Integer, Matchup>> weakAgainst() {
        List<Map.Entry<Integer, Matchup>> values = matchupEntries();
        double avgGames = values.stream().mapToInt(entry -> entry.getValue().matches()).average().orElse(0);
        return values.stream().filter(entry -> entry.getValue().matches() > avgGames)
            .sorted(Comparator.comparingDouble(entry -> entry.getValue().winrate())).limit(EMBED_LIMIT).toList();
    }

    public List<Map.Entry<Integer, Matchup>> strongAgainst() {
        List<Map.Entry<Integer, Matchup>> values = matchupEntries();
        double avgGames = values.stream().mapToInt(entry -> entry.getValue().matches()).average().orElse(0);
        return values.stream().filter(entry -> entry.getValue().matches() > avgGames)
            .sorted(Comparator.comparingDouble((Map.Entry<Integer, Matchup> entry) -> entry.getValue().winrate()).reversed())
            .limit(EMBED_LIMIT).toList();
    }

    public List<Map.Entry<Integer, Matchup>> popularMatchups() {
        return matchupEntries().stream()
            .sorted(Comparator.comparingInt((Map.Entry<Integer, Matchup> entry) -> entry.getValue().matches()).reversed())
            .limit(EMBED_LIMIT).toList();
    }

    public LaneStat getLaneStat(LaneType lane) {
        return laneStats().stream().filter(stat -> stat.lane() == lane).findFirst().orElse(null);
    }

    public String prettyGames() {
        return String.format("%d", games());
    }

    public String prettyWinrate() {
        return String.format("%.2f", winrate() * 100) + "%";
    }

    public String prettyPickrate() {
        return String.format("%.2f", pickrate() * 100) + "%";
    }

    public String prettyBanrate() {
        return overview.banrate() == null ? "—" : String.format("%.2f", overview.banrate() * 100) + "%";
    }

    public void print() {
        System.out.println("Stats for " + filter().champion() + " in " + filter().lane());
        System.out.println("Overview: " + overview);
        System.out.println("Lane stats: " + laneStats());
        System.out.println("Matchups: " + matchups());
        System.out.println("Lane synergies: " + laneSynergies());
        System.out.println("Power curve: " + powerCurve());
        System.out.println("Trend: " + trend());
    }
}
