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
import com.safjnest.lol.utils.TierMathUtils;
import com.safjnest.lol.utils.TierMathUtils.Moments;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public final class MatchupRankingAnalyzer {

    private static final int WINRATE = 0;
    private static final int KDA = 1;
    private static final int GOLD_PER_MINUTE = 2;
    private static final int KILL_PARTICIPATION = 3;
    private static final int DEATH_SHARE = 4;
    private static final int GOLD_DIFF_15 = 5;
    private static final int CS_DIFF_15 = 6;
    private static final int XP_DIFF_15 = 7;
    private static final int KILL_DIFF_15 = 8;
    private static final int PLATE_DIFF_15 = 9;
    private static final int METRIC_COUNT = 10;
    private static final double[] WEIGHTS = {0.275, 0.10, 0.05, 0.05, -0.025, 0.125, 0.10, 0.10, 0.125, 0.05};

    private MatchupRankingAnalyzer() {}

    public static ChampionStatistics rank(ChampionStatistics statistics) {
        if (statistics == null) return null;
        return new ChampionStatistics(statistics.filter(), statistics.overview(), statistics.laneStats(),
            rankChampionMatchups(statistics), rankChampionSynergies(statistics), statistics.powerCurve(), statistics.trend());
    }

    public static ProfileMatchups rank(ProfileMatchups profile) {
        if (profile == null || profile.champions() == null) return profile;
        for (Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queues : profile.champions().values()) {
            if (queues == null) continue;
            for (Map<String, ProfileMatchupLeaf> positions : queues.values()) {
                if (positions == null) continue;
                for (ProfileMatchupLeaf leaf : positions.values()) {
                    if (leaf == null) continue;
                    leaf.matchups = rankProfileRelations(leaf.matchups, leaf.winrate());
                    leaf.synergies = rankProfileRelations(leaf.synergies, leaf.winrate());
                }
            }
        }
        return profile;
    }

    private static Map<Integer, ChampionStatistics.Matchup> rankChampionMatchups(ChampionStatistics statistics) {
        Map<Integer, ChampionStatistics.Matchup> source = statistics.matchups();
        if (source == null || source.isEmpty()) return source == null ? Map.of() : source;
        List<ChampionStatistics.Matchup> values = new ArrayList<>();
        List<Integer> champions = new ArrayList<>();
        for (Map.Entry<Integer, ChampionStatistics.Matchup> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue().matches() > 0) {
                champions.add(entry.getKey());
                values.add(entry.getValue());
            }
        }
        if (values.isEmpty()) return source;

        double prior = TierMathUtils.median(values.stream().map(ChampionStatistics.Matchup::matches).toList());
        double baseline = statistics.winrate();
        List<double[]> adjusted = new ArrayList<>(values.size());
        for (ChampionStatistics.Matchup value : values) {
            double[] raw = championMetrics(value);
            int[] samples = championSamples(value);
            raw[WINRATE] = TierMathUtils.shrinkRate(value.wins(), value.matches(), baseline, prior);
            for (int metric = 1; metric < METRIC_COUNT; metric++)
                raw[metric] = shrink(raw[metric], samples[metric], metricMean(values, metric), prior);
            adjusted.add(raw);
        }
        Moments[] moments = moments(adjusted);

        List<ChampionRanked> ranked = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            ChampionStatistics.Matchup raw = values.get(i);
            double[] metrics = adjusted.get(i);
            double score = score(metrics, moments);
            ChampionStatistics.Matchup response = new ChampionStatistics.Matchup(
                raw.matches(), raw.wins(), raw.winrate(), raw.deltaWinrate(), raw.goldDiffAt15(), raw.csDiffAt15(),
                raw.soloKillRate(), raw.killParticipation(), raw.opponentBanRate(), raw.metricGames(),
                raw.kda(), raw.goldPerMinute(), raw.deathShare(), raw.xpDiffAt15(), raw.killDiffAt15(),
                raw.levelDiffAt15(), raw.turretPlateDiffAt15(), metrics[WINRATE], metrics[WINRATE] - baseline,
                score, TierMathUtils.tier(score), raw.matches() >= prior, raw.goldDiffAt15Games(),
                raw.csDiffAt15Games(), raw.xpDiffAt15Games(), raw.killDiffAt15Games(), raw.levelDiffAt15Games(),
                raw.turretPlateDiffAt15Games());
            ranked.add(new ChampionRanked(champions.get(i), response));
        }
        ranked.sort(Comparator.comparingDouble((ChampionRanked value) -> value.matchup().matchupScore()).reversed()
            .thenComparing(Comparator.comparingDouble((ChampionRanked value) -> value.matchup().adjustedWinrate()).reversed())
            .thenComparing(Comparator.comparingInt((ChampionRanked value) -> value.matchup().matches()).reversed())
            .thenComparingInt(ChampionRanked::champion));
        Map<Integer, ChampionStatistics.Matchup> result = new LinkedHashMap<>();
        for (ChampionRanked value : ranked) result.put(value.champion(), value.matchup());
        return result;
    }

    private static List<ChampionStatistics.LaneSynergy> rankChampionSynergies(ChampionStatistics statistics) {
        List<ChampionStatistics.LaneSynergy> source = statistics.laneSynergies();
        if (source == null || source.isEmpty()) return source == null ? List.of() : source;
        Map<LaneType, List<ChampionStatistics.LaneSynergy>> groups = new LinkedHashMap<>();
        for (ChampionStatistics.LaneSynergy synergy : source)
            if (synergy != null && synergy.matches() > 0)
                groups.computeIfAbsent(synergy.allyLane(), ignored -> new ArrayList<>()).add(synergy);

        List<ChampionStatistics.LaneSynergy> result = new ArrayList<>();
        double baseline = statistics.winrate();
        for (List<ChampionStatistics.LaneSynergy> group : groups.values()) {
            double prior = TierMathUtils.median(group.stream().map(ChampionStatistics.LaneSynergy::matches).toList());
            List<double[]> adjusted = new ArrayList<>(group.size());
            for (ChampionStatistics.LaneSynergy value : group) {
                double[] metrics = synergyMetrics(value);
                int[] samples = synergySamples(value);
                metrics[WINRATE] = TierMathUtils.shrinkRate(value.wins(), value.matches(), baseline, prior);
                for (int metric = 1; metric < METRIC_COUNT; metric++)
                    metrics[metric] = shrink(metrics[metric], samples[metric], meanSynergy(group, metric), prior);
                adjusted.add(metrics);
            }
            Moments[] moments = moments(adjusted);
            for (int i = 0; i < group.size(); i++) {
                ChampionStatistics.LaneSynergy raw = group.get(i);
                double[] metrics = adjusted.get(i);
                double score = score(metrics, moments);
                result.add(new ChampionStatistics.LaneSynergy(raw.allyChampion(), raw.allyLane(), raw.matches(),
                    raw.wins(), raw.winrate(), raw.pickrate(), raw.goldDiffAt15(), raw.csDiffAt15(), raw.kda(),
                    raw.goldPerMinute(), raw.killParticipation(), raw.deathShare(), raw.xpDiffAt15(),
                    raw.killDiffAt15(), raw.levelDiffAt15(), raw.turretPlateDiffAt15(), metrics[WINRATE],
                    metrics[WINRATE] - baseline, score, TierMathUtils.tier(score), raw.matches() >= prior,
                    raw.goldDiffAt15Games(), raw.csDiffAt15Games(), raw.xpDiffAt15Games(),
                    raw.killDiffAt15Games(), raw.levelDiffAt15Games(), raw.turretPlateDiffAt15Games()));
            }
        }
        result.sort(Comparator.comparingDouble((ChampionStatistics.LaneSynergy value) -> value.matchupScore()).reversed()
            .thenComparing(Comparator.comparingDouble((ChampionStatistics.LaneSynergy value) -> value.adjustedWinrate()).reversed())
            .thenComparing(Comparator.comparingInt((ChampionStatistics.LaneSynergy value) -> value.matches()).reversed())
            .thenComparingInt(ChampionStatistics.LaneSynergy::allyChampion)
            .thenComparing(value -> value.allyLane() == null ? "" : value.allyLane().name()));
        return result;
    }

    private static Map<String, ProfileLeafStats> rankProfileRelations(Map<String, ProfileLeafStats> source, double baseline) {
        if (source == null || source.isEmpty()) return source == null ? new LinkedHashMap<>() : source;
        List<ProfileValue> values = new ArrayList<>();
        ProfileLeafStats zero = null;
        ProfileLeafStats others = null;
        for (Map.Entry<String, ProfileLeafStats> entry : source.entrySet()) {
            String key = entry.getKey();
            ProfileLeafStats stats = entry.getValue();
            if (key == null || stats == null) continue;
            if ("0".equals(key)) zero = stats;
            else if ("others".equals(key)) others = stats;
            else if (stats.games > 0) values.add(new ProfileValue(key, stats));
        }
        if (values.isEmpty()) return appendPlaceholders(new LinkedHashMap<>(), zero, others);

        double prior = TierMathUtils.median(values.stream().map(value -> value.stats().games).toList());
        List<double[]> adjusted = new ArrayList<>(values.size());
        for (ProfileValue value : values) {
            ProfileLeafStats stats = value.stats();
            double[] metrics = profileMetrics(stats);
            int[] samples = profileSamples(stats);
            metrics[WINRATE] = TierMathUtils.shrinkRate(stats.wins, (int) stats.games, baseline, prior);
            for (int metric = 1; metric < METRIC_COUNT; metric++)
                metrics[metric] = shrink(metrics[metric], samples[metric], meanProfile(values, metric), prior);
            adjusted.add(metrics);
        }
        Moments[] moments = moments(adjusted);

        List<ProfileRanked> ranked = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            ProfileValue value = values.get(i);
            ProfileLeafStats stats = value.stats();
            double[] metrics = adjusted.get(i);
            double score = score(metrics, moments);
            stats.adjustedWinrate = metrics[WINRATE];
            stats.weightedDelta = metrics[WINRATE] - baseline;
            stats.matchupScore = score;
            stats.tier = TierMathUtils.tier(score);
            stats.reliable = stats.games >= prior;
            stats.kda = stats.kdaVal();
            stats.goldPerMinute = Double.isFinite(goldPerMinute(stats)) ? goldPerMinute(stats) : null;
            stats.killParticipation = stats.avgKillParticipation();
            stats.deathShare = stats.avgDeathShare();
            stats.goldDiffAt15 = finiteAverage(stats.goldDiffAt15Sum, stats.goldDiffAt15Games);
            stats.csDiffAt15 = finiteAverage(stats.csDiffAt15Sum, stats.csDiffAt15Games);
            stats.xpDiffAt15 = finiteAverage(stats.xpDiffAt15Sum, stats.xpDiffAt15Games);
            stats.killDiffAt15 = finiteAverage(stats.killDiffAt15Sum, stats.killDiffAt15Games);
            stats.levelDiffAt15 = finiteAverage(stats.levelDiffAt15Sum, stats.levelDiffAt15Games);
            stats.turretPlateDiffAt15 = finiteAverage(stats.plateDiffAt15Sum, stats.plateDiffAt15Games);
            ranked.add(new ProfileRanked(value.key(), stats));
        }
        ranked.sort(Comparator.comparingDouble((ProfileRanked value) -> value.stats().matchupScore).reversed()
            .thenComparing(Comparator.comparingDouble((ProfileRanked value) -> value.stats().adjustedWinrate).reversed())
            .thenComparing(Comparator.comparingLong((ProfileRanked value) -> value.stats().games).reversed())
            .thenComparingInt(value -> championId(value.key())));
        Map<String, ProfileLeafStats> result = new LinkedHashMap<>();
        for (ProfileRanked value : ranked) result.put(value.key(), value.stats());
        return appendPlaceholders(result, zero, others);
    }

    private static Map<String, ProfileLeafStats> appendPlaceholders(
            Map<String, ProfileLeafStats> result, ProfileLeafStats zero, ProfileLeafStats others) {
        if (zero != null) result.put("0", zero);
        if (others != null) result.put("others", others);
        return result;
    }

    private static double[] championMetrics(ChampionStatistics.Matchup value) {
        return new double[]{value.winrate(), asDouble(value.kda()), asDouble(value.goldPerMinute()),
            asDouble(value.killParticipation()), asDouble(value.deathShare()),
            observedMetric(value.goldDiffAt15(), value.goldDiffAt15Games()),
            observedMetric(value.csDiffAt15(), value.csDiffAt15Games()),
            observedMetric(value.xpDiffAt15(), value.xpDiffAt15Games()),
            observedMetric(value.killDiffAt15(), value.killDiffAt15Games()),
            observedMetric(value.turretPlateDiffAt15(), value.turretPlateDiffAt15Games())};
    }

    private static int[] championSamples(ChampionStatistics.Matchup value) {
        int metricGames = value.metricGames() == null || value.metricGames() <= 0
            ? value.matches() : Math.min(value.matches(), value.metricGames());
        return new int[]{value.matches(), value.matches(), value.matches(), metricGames, metricGames,
            sampleCount(value.goldDiffAt15Games(), value.matches()),
            sampleCount(value.csDiffAt15Games(), value.matches()),
            sampleCount(value.xpDiffAt15Games(), value.matches()),
            sampleCount(value.killDiffAt15Games(), value.matches()),
            sampleCount(value.turretPlateDiffAt15Games(), value.matches())};
    }

    private static double[] synergyMetrics(ChampionStatistics.LaneSynergy value) {
        return new double[]{value.winrate(), asDouble(value.kda()), asDouble(value.goldPerMinute()),
            asDouble(value.killParticipation()), asDouble(value.deathShare()),
            observedMetric(value.goldDiffAt15(), value.goldDiffAt15Games()),
            observedMetric(value.csDiffAt15(), value.csDiffAt15Games()),
            observedMetric(value.xpDiffAt15(), value.xpDiffAt15Games()),
            observedMetric(value.killDiffAt15(), value.killDiffAt15Games()),
            observedMetric(value.turretPlateDiffAt15(), value.turretPlateDiffAt15Games())};
    }

    private static int[] synergySamples(ChampionStatistics.LaneSynergy value) {
        return new int[]{value.matches(), value.matches(), value.matches(), value.matches(), value.matches(),
            sampleCount(value.goldDiffAt15Games(), value.matches()),
            sampleCount(value.csDiffAt15Games(), value.matches()),
            sampleCount(value.xpDiffAt15Games(), value.matches()),
            sampleCount(value.killDiffAt15Games(), value.matches()),
            sampleCount(value.turretPlateDiffAt15Games(), value.matches())};
    }

    private static double[] profileMetrics(ProfileLeafStats stats) {
        return new double[]{stats.winratePercent() / 100d, stats.kdaVal(), goldPerMinute(stats),
            stats.avgKillParticipation() / 100d, stats.avgDeathShare() / 100d,
            average(stats.goldDiffAt15Sum, stats.goldDiffAt15Games), average(stats.csDiffAt15Sum, stats.csDiffAt15Games),
            average(stats.xpDiffAt15Sum, stats.xpDiffAt15Games), average(stats.killDiffAt15Sum, stats.killDiffAt15Games),
            average(stats.plateDiffAt15Sum, stats.plateDiffAt15Games)};
    }

    private static int[] profileSamples(ProfileLeafStats stats) {
        return new int[]{(int) stats.games, (int) stats.games, (int) stats.games, (int) stats.games,
            (int) stats.games, (int) stats.goldDiffAt15Games, (int) stats.csDiffAt15Games,
            (int) stats.xpDiffAt15Games, (int) stats.killDiffAt15Games, (int) stats.plateDiffAt15Games};
    }

    private static double metricMean(List<ChampionStatistics.Matchup> values, int metric) {
        List<Double> samples = new ArrayList<>();
        for (ChampionStatistics.Matchup value : values) addSample(samples, championMetrics(value)[metric]);
        return TierMathUtils.moments(samples).mean();
    }

    private static double meanSynergy(List<ChampionStatistics.LaneSynergy> values, int metric) {
        List<Double> samples = new ArrayList<>();
        for (ChampionStatistics.LaneSynergy value : values) addSample(samples, synergyMetrics(value)[metric]);
        return TierMathUtils.moments(samples).mean();
    }

    private static double meanProfile(List<ProfileValue> values, int metric) {
        List<Double> samples = new ArrayList<>();
        for (ProfileValue value : values) addSample(samples, profileMetrics(value.stats())[metric]);
        return TierMathUtils.moments(samples).mean();
    }

    private static void addSample(List<Double> samples, double value) {
        if (Double.isFinite(value)) samples.add(value);
    }

    private static double shrink(double value, int samples, double mean, double prior) {
        return Double.isFinite(value) ? TierMathUtils.shrinkValue(value, samples, mean, prior) : Double.NaN;
    }

    private static Moments[] moments(List<double[]> values) {
        Moments[] result = new Moments[METRIC_COUNT];
        for (int metric = 0; metric < METRIC_COUNT; metric++) {
            List<Double> samples = new ArrayList<>();
            for (double[] value : values) addSample(samples, value[metric]);
            result[metric] = TierMathUtils.moments(samples);
        }
        return result;
    }

    private static double score(double[] values, Moments[] moments) {
        double score = 0;
        for (int metric = 0; metric < METRIC_COUNT; metric++)
            score += TierMathUtils.z(values[metric], moments[metric]) * WEIGHTS[metric];
        return score;
    }

    private static double goldPerMinute(ProfileLeafStats stats) {
        return stats.playtime <= 0 ? Double.NaN : stats.gold / (stats.playtime / 60_000d);
    }

    private static double average(double sum, long count) {
        return count == 0 ? Double.NaN : sum / count;
    }

    private static Double finiteAverage(double sum, long count) {
        return count == 0 ? null : sum / count;
    }

    private static double asDouble(Number value) { return value == null ? Double.NaN : value.doubleValue(); }

    private static int sampleCount(long samples, int matches) {
        return (int) Math.max(0, Math.min(matches, Math.min(Integer.MAX_VALUE, samples)));
    }

    private static double observedMetric(Number value, long samples) {
        return samples <= 0 ? Double.NaN : asDouble(value);
    }

    private static int championId(String key) {
        try { return Integer.parseInt(key); }
        catch (NumberFormatException ignored) { return Integer.MAX_VALUE; }
    }

    private record ChampionRanked(int champion, ChampionStatistics.Matchup matchup) {}
    private record ProfileValue(String key, ProfileLeafStats stats) {}
    private record ProfileRanked(String key, ProfileLeafStats stats) {}
}
