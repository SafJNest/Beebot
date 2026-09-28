package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.lol.model.statistics.ProfileMatchupLeaf;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;

/**
 * Response-only matchup ranking.
 *
 * Raw matchup aggregates stay untouched in Mongo/Redis. The scorer mirrors the
 * tier-list approach: sample-aware win-rate shrinkage, standardized features,
 * weighted Z-score, then tier buckets. No pick-rate or ban-rate signal is used.
 */
public final class MatchupTierAnalyzer {

    private static final double S_PLUS_SCORE = 2.0;
    private static final double S_SCORE = 1.0;
    private static final double A_SCORE = 0.25;
    private static final double B_SCORE = -0.25;
    private static final double C_SCORE = -1.0;

    private static final double CHAMPION_WINRATE_WEIGHT = 0.55;
    private static final double CHAMPION_GOLD_15_WEIGHT = 0.20;
    private static final double CHAMPION_CS_15_WEIGHT = 0.10;
    private static final double CHAMPION_SOLO_KILL_WEIGHT = 0.10;
    private static final double CHAMPION_KP_WEIGHT = 0.05;

    private static final double PROFILE_WINRATE_WEIGHT = 0.55;
    private static final double PROFILE_KDA_WEIGHT = 0.20;
    private static final double PROFILE_GPM_WEIGHT = 0.10;
    private static final double PROFILE_KP_WEIGHT = 0.10;
    private static final double PROFILE_DEATH_SHARE_WEIGHT = 0.05;

    private MatchupTierAnalyzer() {}

    public static ChampionStatistics rank(ChampionStatistics statistics) {
        if (statistics == null || statistics.matchups() == null || statistics.matchups().isEmpty()) return statistics;

        List<ChampionCandidate> candidates = new ArrayList<>();
        for (Map.Entry<Integer, ChampionStatistics.Matchup> entry : statistics.matchups().entrySet()) {
            ChampionStatistics.Matchup matchup = entry.getValue();
            if (entry.getKey() == null || matchup == null || matchup.matches() <= 0) continue;
            candidates.add(new ChampionCandidate(entry.getKey(), matchup));
        }
        if (candidates.isEmpty()) return statistics;

        double priorStrength = median(candidates.stream().map(candidate -> candidate.matchup().matches()).toList());
        double baselineWinrate = statistics.winrate();

        double goldMean = mean(candidates, candidate -> asDouble(candidate.matchup().goldDiffAt15()));
        double csMean = mean(candidates, candidate -> candidate.matchup().csDiffAt15());
        double soloKillMean = mean(candidates, candidate -> candidate.matchup().soloKillRate());
        double kpMean = mean(candidates, candidate -> candidate.matchup().killParticipation());

        List<ChampionAdjusted> adjusted = new ArrayList<>(candidates.size());
        for (ChampionCandidate candidate : candidates) {
            ChampionStatistics.Matchup matchup = candidate.matchup();
            int metricGames = matchup.metricGames() == null || matchup.metricGames() <= 0
                ? matchup.matches() : Math.min(matchup.matches(), matchup.metricGames());
            double adjustedWinrate = shrinkRate(matchup.wins(), matchup.matches(), baselineWinrate, priorStrength);
            adjusted.add(new ChampionAdjusted(
                candidate.champion(),
                matchup,
                adjustedWinrate,
                shrinkMetric(asDouble(matchup.goldDiffAt15()), metricGames, goldMean, priorStrength),
                shrinkMetric(matchup.csDiffAt15(), metricGames, csMean, priorStrength),
                shrinkMetric(matchup.soloKillRate(), metricGames, soloKillMean, priorStrength),
                shrinkMetric(matchup.killParticipation(), metricGames, kpMean, priorStrength)
            ));
        }

        Moments winrateMoments = posteriorMoments(adjusted, priorStrength);
        Moments goldMoments = moments(adjusted, ChampionAdjusted::goldDiffAt15);
        Moments csMoments = moments(adjusted, ChampionAdjusted::csDiffAt15);
        Moments soloKillMoments = moments(adjusted, ChampionAdjusted::soloKillRate);
        Moments kpMoments = moments(adjusted, ChampionAdjusted::killParticipation);

        List<ChampionRanked> ranked = new ArrayList<>(adjusted.size());
        for (ChampionAdjusted value : adjusted) {
            double score =
                z(value.adjustedWinrate(), winrateMoments) * CHAMPION_WINRATE_WEIGHT
                + z(value.goldDiffAt15(), goldMoments) * CHAMPION_GOLD_15_WEIGHT
                + z(value.csDiffAt15(), csMoments) * CHAMPION_CS_15_WEIGHT
                + z(value.soloKillRate(), soloKillMoments) * CHAMPION_SOLO_KILL_WEIGHT
                + z(value.killParticipation(), kpMoments) * CHAMPION_KP_WEIGHT;
            ChampionStatistics.Matchup source = value.matchup();
            ChampionStatistics.Matchup response = new ChampionStatistics.Matchup(
                source.matches(),
                source.wins(),
                source.winrate(),
                source.deltaWinrate(),
                source.goldDiffAt15(),
                source.csDiffAt15(),
                source.soloKillRate(),
                source.killParticipation(),
                source.opponentBanRate(),
                source.metricGames(),
                value.adjustedWinrate(),
                value.adjustedWinrate() - baselineWinrate,
                score,
                tier(score),
                source.matches() >= priorStrength
            );
            ranked.add(new ChampionRanked(value.champion(), response));
        }

        ranked.sort(Comparator.comparingDouble((ChampionRanked value) -> value.matchup().matchupScore()).reversed()
            .thenComparing(Comparator.comparingDouble((ChampionRanked value) -> value.matchup().adjustedWinrate()).reversed())
            .thenComparing(Comparator.comparingInt((ChampionRanked value) -> value.matchup().matches()).reversed())
            .thenComparingInt(ChampionRanked::champion));

        Map<Integer, ChampionStatistics.Matchup> matchups = new LinkedHashMap<>();
        for (ChampionRanked value : ranked) matchups.put(value.champion(), value.matchup());

        return new ChampionStatistics(
            statistics.filter(),
            statistics.overview(),
            statistics.laneStats(),
            matchups,
            statistics.laneSynergies(),
            statistics.powerCurve(),
            statistics.trend()
        );
    }

    public static ProfileMatchups rank(ProfileMatchups profile) {
        if (profile == null || profile.champions() == null) return profile;
        for (Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queues : profile.champions().values()) {
            if (queues == null) continue;
            for (Map<String, ProfileMatchupLeaf> positions : queues.values()) {
                if (positions == null) continue;
                for (ProfileMatchupLeaf leaf : positions.values()) rankLeaf(leaf);
            }
        }
        return profile;
    }

    private static void rankLeaf(ProfileMatchupLeaf leaf) {
        if (leaf == null || leaf.matchups == null || leaf.matchups.isEmpty()) return;

        List<ProfileCandidate> candidates = new ArrayList<>();
        List<Map.Entry<String, ProfileLeafStats>> special = new ArrayList<>();
        for (Map.Entry<String, ProfileLeafStats> entry : leaf.matchups.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            if ("others".equals(entry.getKey()) || "0".equals(entry.getKey()) || entry.getValue().games <= 0) {
                special.add(entry);
                continue;
            }
            candidates.add(new ProfileCandidate(entry.getKey(), entry.getValue()));
        }
        if (candidates.isEmpty()) return;

        double priorStrength = median(candidates.stream().map(candidate -> (int) candidate.stats().games).toList());
        double baselineWinrate = leaf.winrate();

        double kdaMean = meanProfile(candidates, candidate -> candidate.stats().kdaVal());
        double gpmMean = meanProfile(candidates, candidate -> goldPerMinute(candidate.stats()));
        double kpMean = meanProfile(candidates, candidate -> candidate.stats().avgKillParticipation());
        double deathShareMean = meanProfile(candidates, candidate -> candidate.stats().avgDeathShare());

        List<ProfileAdjusted> adjusted = new ArrayList<>(candidates.size());
        for (ProfileCandidate candidate : candidates) {
            ProfileLeafStats stats = candidate.stats();
            int games = (int) stats.games;
            adjusted.add(new ProfileAdjusted(
                candidate.key(),
                stats,
                shrinkRate(stats.wins, games, baselineWinrate, priorStrength),
                shrinkMetric(stats.kdaVal(), games, kdaMean, priorStrength),
                shrinkMetric(goldPerMinute(stats), games, gpmMean, priorStrength),
                shrinkMetric(stats.avgKillParticipation(), games, kpMean, priorStrength),
                shrinkMetric(stats.avgDeathShare(), games, deathShareMean, priorStrength)
            ));
        }

        Moments winrateMoments = posteriorProfileMoments(adjusted, priorStrength);
        Moments kdaMoments = profileMoments(adjusted, ProfileAdjusted::kda);
        Moments gpmMoments = profileMoments(adjusted, ProfileAdjusted::goldPerMinute);
        Moments kpMoments = profileMoments(adjusted, ProfileAdjusted::killParticipation);
        Moments deathShareMoments = profileMoments(adjusted, ProfileAdjusted::deathShare);

        List<ProfileRanked> ranked = new ArrayList<>(adjusted.size());
        for (ProfileAdjusted value : adjusted) {
            double score =
                z(value.adjustedWinrate(), winrateMoments) * PROFILE_WINRATE_WEIGHT
                + z(value.kda(), kdaMoments) * PROFILE_KDA_WEIGHT
                + z(value.goldPerMinute(), gpmMoments) * PROFILE_GPM_WEIGHT
                + z(value.killParticipation(), kpMoments) * PROFILE_KP_WEIGHT
                - z(value.deathShare(), deathShareMoments) * PROFILE_DEATH_SHARE_WEIGHT;

            ProfileLeafStats stats = value.stats();
            stats.adjustedWinrate = value.adjustedWinrate();
            stats.weightedDelta = value.adjustedWinrate() - baselineWinrate;
            stats.matchupScore = score;
            stats.tier = tier(score);
            stats.reliable = stats.games >= priorStrength;
            ranked.add(new ProfileRanked(value.key(), stats));
        }

        ranked.sort(Comparator.comparingDouble((ProfileRanked value) -> value.stats().matchupScore).reversed()
            .thenComparing(Comparator.comparingDouble((ProfileRanked value) -> value.stats().adjustedWinrate).reversed())
            .thenComparing(Comparator.comparingLong((ProfileRanked value) -> value.stats().games).reversed())
            .thenComparing(ProfileRanked::key));

        Map<String, ProfileLeafStats> sorted = new LinkedHashMap<>();
        for (ProfileRanked value : ranked) sorted.put(value.key(), value.stats());
        for (Map.Entry<String, ProfileLeafStats> entry : special) sorted.put(entry.getKey(), entry.getValue());
        leaf.matchups = sorted;
    }

    private static double shrinkRate(long wins, int games, double priorMean, double priorStrength) {
        if (games <= 0) return priorMean;
        if (priorStrength <= 0) return (double) wins / games;
        return (wins + priorStrength * priorMean) / (games + priorStrength);
    }

    private static Double shrinkMetric(Double value, int games, double mean, double priorStrength) {
        if (value == null) return null;
        if (games <= 0 || priorStrength <= 0) return value;
        return (value * games + mean * priorStrength) / (games + priorStrength);
    }

    private static double goldPerMinute(ProfileLeafStats stats) {
        if (stats == null || stats.playtime <= 0) return 0;
        return stats.gold / (stats.playtime / 60_000d);
    }

    private static double mean(List<ChampionCandidate> values, ChampionMetric metric) {
        double sum = 0;
        int count = 0;
        for (ChampionCandidate value : values) {
            Double current = metric.value(value);
            if (current == null) continue;
            sum += current;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static double meanProfile(List<ProfileCandidate> values, ProfileMetric metric) {
        double sum = 0;
        int count = 0;
        for (ProfileCandidate value : values) {
            Double current = metric.value(value);
            if (current == null) continue;
            sum += current;
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }

    private static Moments posteriorMoments(List<ChampionAdjusted> values, double priorStrength) {
        double mean = values.stream().mapToDouble(ChampionAdjusted::adjustedWinrate).average().orElse(0);
        double variance = 0;
        for (ChampionAdjusted value : values) {
            double posteriorVariance = value.adjustedWinrate() * (1 - value.adjustedWinrate())
                / (value.matchup().matches() + priorStrength + 1);
            variance += Math.pow(value.adjustedWinrate() - mean, 2) + posteriorVariance;
        }
        return new Moments(mean, Math.sqrt(variance / values.size()));
    }

    private static Moments posteriorProfileMoments(List<ProfileAdjusted> values, double priorStrength) {
        double mean = values.stream().mapToDouble(ProfileAdjusted::adjustedWinrate).average().orElse(0);
        double variance = 0;
        for (ProfileAdjusted value : values) {
            double posteriorVariance = value.adjustedWinrate() * (1 - value.adjustedWinrate())
                / (value.stats().games + priorStrength + 1);
            variance += Math.pow(value.adjustedWinrate() - mean, 2) + posteriorVariance;
        }
        return new Moments(mean, Math.sqrt(variance / values.size()));
    }

    private static Moments moments(List<ChampionAdjusted> values, AdjustedChampionMetric metric) {
        double sum = 0;
        int count = 0;
        for (ChampionAdjusted value : values) {
            Double current = metric.value(value);
            if (current == null) continue;
            sum += current;
            count++;
        }
        if (count == 0) return new Moments(0, 0);
        double mean = sum / count;
        double variance = 0;
        for (ChampionAdjusted value : values) {
            Double current = metric.value(value);
            if (current != null) variance += Math.pow(current - mean, 2);
        }
        return new Moments(mean, Math.sqrt(variance / count));
    }

    private static Moments profileMoments(List<ProfileAdjusted> values, AdjustedProfileMetric metric) {
        double sum = 0;
        int count = 0;
        for (ProfileAdjusted value : values) {
            Double current = metric.value(value);
            if (current == null) continue;
            sum += current;
            count++;
        }
        if (count == 0) return new Moments(0, 0);
        double mean = sum / count;
        double variance = 0;
        for (ProfileAdjusted value : values) {
            Double current = metric.value(value);
            if (current != null) variance += Math.pow(current - mean, 2);
        }
        return new Moments(mean, Math.sqrt(variance / count));
    }

    private static double z(Double value, Moments moments) {
        return value == null || moments.deviation() == 0 ? 0 : (value - moments.mean()) / moments.deviation();
    }

    private static double median(List<Integer> values) {
        if (values == null || values.isEmpty()) return 0;
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
            ? (sorted.get(middle - 1) + sorted.get(middle)) / 2d
            : sorted.get(middle);
    }

    private static String tier(double score) {
        if (score >= S_PLUS_SCORE) return "S+";
        if (score >= S_SCORE) return "S";
        if (score >= A_SCORE) return "A";
        if (score >= B_SCORE) return "B";
        if (score >= C_SCORE) return "C";
        return "F";
    }

    private static Double asDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    @FunctionalInterface
    private interface ChampionMetric {
        Double value(ChampionCandidate value);
    }

    @FunctionalInterface
    private interface ProfileMetric {
        Double value(ProfileCandidate value);
    }

    @FunctionalInterface
    private interface AdjustedChampionMetric {
        Double value(ChampionAdjusted value);
    }

    @FunctionalInterface
    private interface AdjustedProfileMetric {
        Double value(ProfileAdjusted value);
    }

    private record Moments(double mean, double deviation) {}

    private record ChampionCandidate(int champion, ChampionStatistics.Matchup matchup) {}

    private record ChampionAdjusted(
        int champion,
        ChampionStatistics.Matchup matchup,
        double adjustedWinrate,
        Double goldDiffAt15,
        Double csDiffAt15,
        Double soloKillRate,
        Double killParticipation
    ) {}

    private record ChampionRanked(int champion, ChampionStatistics.Matchup matchup) {}

    private record ProfileCandidate(String key, ProfileLeafStats stats) {}

    private record ProfileAdjusted(
        String key,
        ProfileLeafStats stats,
        double adjustedWinrate,
        Double kda,
        Double goldPerMinute,
        Double killParticipation,
        Double deathShare
    ) {}

    private record ProfileRanked(String key, ProfileLeafStats stats) {}
}
