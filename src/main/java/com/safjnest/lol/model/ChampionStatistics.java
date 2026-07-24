package com.safjnest.lol.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record ChampionStatistics(
    Filter filter,
    Overview overview,
    List<LaneStat> laneStats,
    @JsonSerialize(keyUsing = MatchupKeySerializer.class)
    @JsonDeserialize(keyUsing = MatchupKeyDeserializer.class)
    Map<MatchupKey, Matchup> matchups,
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

    public record MatchupKey(int champion, LaneType lane) {}

    public static final class MatchupKeySerializer extends JsonSerializer<MatchupKey> {
        @Override
        public void serialize(MatchupKey value, JsonGenerator generator, SerializerProvider provider) throws java.io.IOException {
            generator.writeFieldName(value.champion() + "|" + (value.lane() == null ? "" : value.lane().name()));
        }
    }

    public static final class MatchupKeyDeserializer extends KeyDeserializer {
        @Override
        public Object deserializeKey(String key, DeserializationContext context) {
            String[] values = key == null ? new String[0] : key.split("\\|", -1);
            if (values.length != 2) throw new IllegalArgumentException("Invalid matchup key " + key);
            LaneType lane = values[1].isBlank() ? null : LaneType.valueOf(values[1]);
            return new MatchupKey(Integer.parseInt(values[0]), lane);
        }
    }

    public record Matchup(
        int champion,
        LaneType lane,
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
        public Matchup(int champion, int matches, double winrate) {
            this(champion, null, matches, (int) Math.round(matches * winrate), winrate,
                null, null, null, null, null, null, null);
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
        double pickrate
    ) {}

    public record PowerCurvePoint(String durationBucket, int games, int wins, double winrate) {}

    public record Trend(String previousPatch, Integer games, Double winrate, Double deltaWinrate) {}

    /** Compatibility constructor for existing non-HTTP consumers. */
    public ChampionStatistics(
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
        this(
            filter,
            new Overview(games, picks, bans, wins, winrate, pickrate, banrate, null, null, null, null),
            laneStats,
            matchups,
            List.of(),
            List.of(),
            null
        );
    }

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
            for (Map.Entry<MatchupKey, Matchup> entry : statistics.matchups().entrySet()) {
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

    public Matchup getOpponentMatchup(int opponent, LaneType lane) {
        return matchups().get(new MatchupKey(opponent, lane));
    }

    private List<Matchup> getMatchups(LaneType lane) {
        return matchups().entrySet().stream()
            .filter(entry -> lane == null || entry.getKey().lane() == lane)
            .map(Map.Entry::getValue)
            .toList();
    }

    public List<Matchup> weakAgainst(LaneType lane) {
        List<Matchup> sameLane = getMatchups(lane);
        double avgGames = sameLane.stream().mapToInt(Matchup::matches).average().orElse(0);
        return sameLane.stream().filter(e -> e.matches() > avgGames)
            .sorted(Comparator.comparingDouble(Matchup::winrate)).limit(EMBED_LIMIT).toList();
    }

    public List<Matchup> strongAgainst(LaneType lane) {
        List<Matchup> sameLane = getMatchups(lane);
        double avgGames = sameLane.stream().mapToInt(Matchup::matches).average().orElse(0);
        return sameLane.stream().filter(e -> e.matches() > avgGames)
            .sorted(Comparator.comparingDouble(Matchup::winrate).reversed()).limit(EMBED_LIMIT).toList();
    }

    public List<Matchup> popularMatchups(LaneType lane) {
        return getMatchups(lane).stream()
            .sorted(Comparator.comparingInt(Matchup::matches).reversed()).limit(EMBED_LIMIT).toList();
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
