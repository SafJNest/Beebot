package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

import com.safjnest.lol.model.ChampionTierList;
import com.safjnest.lol.model.ChampionTierSource;
import com.safjnest.lol.model.ChampionView;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.ChampionUtils;

import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public final class ChampionTierAnalyzer {

    private static final int MATCHUP_LIMIT = 3;
    private static final double WINRATE_WEIGHT = 0.50;
    private static final double PICKRATE_WEIGHT = 0.45;
    private static final double BANRATE_WEIGHT = 0.05;
    private static final double S_PLUS_SCORE = 2;
    private static final double S_SCORE = 1;
    private static final double A_SCORE = 0.25;
    private static final double B_SCORE = -0.25;
    private static final double C_SCORE = -1;

    private ChampionTierAnalyzer() {}

    public static List<ChampionTierList.Role> analyze(
            List<Filter> filters,
            Map<String, ChampionTierSource> sources) {
        return analyze(filters, sources, ChampionTierAnalyzer::champion);
    }

    static List<ChampionTierList.Role> analyze(
            List<Filter> filters,
            Map<String, ChampionTierSource> sources,
            IntFunction<ChampionView.Champion> champions) {
        List<ChampionTierList.Role> roles = new ArrayList<>();
        if (filters == null || sources == null || champions == null) return roles;
        RoleEligibility eligible = roleEligibility(filters, sources);
        for (Filter filter : filters) {
            if (filter == null) continue;
            ChampionTierSource source = sources.get(filter.genericKey());
            if (source == null || !source.ready()) continue;
            roles.add(new ChampionTierList.Role(filter.lane(), analyzeRole(filter, source, eligible, champions)));
        }
        return roles;
    }

    // ============================================================================

    private static List<ChampionTierList.Champion> analyzeRole(
            Filter filter,
            ChampionTierSource source,
            RoleEligibility eligibleForRole,
            IntFunction<ChampionView.Champion> champions) {
        List<SourceChampion> eligible = sourceChampions(filter, source, eligibleForRole, champions);
        if (eligible.isEmpty()) return List.of();

        double roleAverageWinrate = roleAverageWinrate(eligible);
        double priorStrength = medianPicks(eligible);
        if (priorStrength == 0) return List.of();
        List<SourceChampion> values = adjustedChampions(eligible, roleAverageWinrate, priorStrength);
        Moments winrates = adjustedWinrateMoments(values, priorStrength);
        Moments pickrates = moments(values, Metric.PICKRATE);
        Moments banrates = moments(values, Metric.BANRATE);
        List<ChampionTierList.Champion> result = new ArrayList<>(values.size());
        for (SourceChampion value : values) {
            double score = z(value.adjustedWinrate(), winrates) * WINRATE_WEIGHT
                + z(value.statistics().pickrate(), pickrates) * PICKRATE_WEIGHT
                + z(value.statistics().banrate(), banrates) * BANRATE_WEIGHT;
            MatchupAnalysis matchups = matchups(filter, value, eligibleForRole, champions);
            result.add(new ChampionTierList.Champion(value.champion(), true, tier(score), score, value.statistics(),
                counters(matchups), strongAgainst(matchups)));
        }
        result.sort(Comparator.comparingDouble(ChampionTierList.Champion::tierScore).reversed()
            .thenComparing(value -> value.champion().id()));
        return result;
    }

    private static RoleEligibility roleEligibility(
            List<Filter> filters,
            Map<String, ChampionTierSource> sources) {
        List<RoleBucket> buckets = roleBuckets(filters, sources);
        Map<Integer, Long> totalPicks = totalPicks(buckets);
        long offRoleResolution = offRoleResolution(buckets, totalPicks);
        List<RolePoint> points = rolePoints(buckets, totalPicks, offRoleResolution);
        if (points.isEmpty()) return new RoleEligibility(Set.of());
        if (buckets.size() == 1) return new RoleEligibility(roleChampions(points));
        boolean[] assignments = highCluster(points);
        Set<RoleChampion> result = new HashSet<>();
        for (int i = 0; i < points.size(); i++) if (assignments[i]) result.add(points.get(i).roleChampion());
        return new RoleEligibility(result);
    }

    private static List<RoleBucket> roleBuckets(
            List<Filter> filters,
            Map<String, ChampionTierSource> sources) {
        List<RoleBucket> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Filter filter : filters) {
            if (filter == null || !keys.add(filter.genericKey())) continue;
            ChampionTierSource source = sources.get(filter.genericKey());
            if (source != null && source.ready()) result.add(new RoleBucket(filter.genericKey(), source));
        }
        return result;
    }

    private static Map<Integer, Long> totalPicks(List<RoleBucket> buckets) {
        Map<Integer, Long> result = new HashMap<>();
        for (RoleBucket bucket : buckets) {
            for (Map.Entry<Integer, ChampionTierSource.Champion> entry : bucket.source().champions().entrySet()) {
                ChampionTierList.Statistics statistics = entry.getValue().statistics();
                if (statistics == null || statistics.picks() <= 0) continue;
                result.merge(entry.getKey(), (long) statistics.picks(), Long::sum);
            }
        }
        return result;
    }

    private static long offRoleResolution(List<RoleBucket> buckets, Map<Integer, Long> totalPicks) {
        long resolution = Long.MAX_VALUE;
        long smallestRole = Long.MAX_VALUE;
        for (RoleBucket bucket : buckets) {
            for (Map.Entry<Integer, ChampionTierSource.Champion> entry : bucket.source().champions().entrySet()) {
                ChampionTierList.Statistics statistics = entry.getValue().statistics();
                if (statistics == null || statistics.picks() <= 0) continue;
                smallestRole = Math.min(smallestRole, statistics.picks());
                long otherRoles = totalPicks.getOrDefault(entry.getKey(), 0L) - statistics.picks();
                if (otherRoles > 0) resolution = Math.min(resolution, otherRoles);
            }
        }
        return resolution < Long.MAX_VALUE ? resolution : smallestRole;
    }

    private static List<RolePoint> rolePoints(
            List<RoleBucket> buckets,
            Map<Integer, Long> totalPicks,
            long offRoleResolution) {
        List<RolePoint> result = new ArrayList<>();
        for (RoleBucket bucket : buckets) {
            for (Map.Entry<Integer, ChampionTierSource.Champion> entry : bucket.source().champions().entrySet()) {
                ChampionTierList.Statistics statistics = entry.getValue().statistics();
                long total = totalPicks.getOrDefault(entry.getKey(), 0L);
                if (statistics == null || statistics.picks() <= 0 || total <= 0) continue;
                double share = (double) statistics.picks() / total;
                double boundedShare = share < 1 || offRoleResolution <= 0 ? share
                    : (double) statistics.picks() / (statistics.picks() + offRoleResolution);
                result.add(new RolePoint(new RoleChampion(bucket.filterKey(), entry.getKey()),
                    Math.log1p(statistics.picks()), Math.log(boundedShare / (1 - boundedShare))));
            }
        }
        return result;
    }

    private static Set<RoleChampion> roleChampions(List<RolePoint> points) {
        Set<RoleChampion> result = new HashSet<>();
        for (RolePoint point : points) result.add(point.roleChampion());
        return result;
    }

    private static boolean[] highCluster(List<RolePoint> points) {
        boolean[] all = new boolean[points.size()];
        Arrays.fill(all, true);
        if (points.size() < 2) return all;

        Moments xMoments = pointMoments(points, true);
        Moments yMoments = pointMoments(points, false);
        List<ClusterPoint> normalized = normalized(points, xMoments, yMoments);
        int low = projectionIndex(normalized, false);
        int high = projectionIndex(normalized, true);
        if (low == high) return all;

        Centroid first = new Centroid(normalized.get(low).x(), normalized.get(low).y());
        Centroid second = new Centroid(normalized.get(high).x(), normalized.get(high).y());
        int[] assignments = new int[points.size()];
        Arrays.fill(assignments, -1);
        while (true) {
            boolean changed = false;
            double firstX = 0;
            double firstY = 0;
            double secondX = 0;
            double secondY = 0;
            int firstCount = 0;
            int secondCount = 0;
            for (int i = 0; i < normalized.size(); i++) {
                ClusterPoint point = normalized.get(i);
                int cluster = distance(point, second) < distance(point, first) ? 1 : 0;
                if (assignments[i] != cluster) changed = true;
                assignments[i] = cluster;
                if (cluster == 0) {
                    firstX += point.x();
                    firstY += point.y();
                    firstCount++;
                } else {
                    secondX += point.x();
                    secondY += point.y();
                    secondCount++;
                }
            }
            if (firstCount == 0 || secondCount == 0) return all;
            first = new Centroid(firstX / firstCount, firstY / firstCount);
            second = new Centroid(secondX / secondCount, secondY / secondCount);
            if (!changed) break;
        }

        int eligible = eligibleCluster(first, second);
        boolean[] result = new boolean[assignments.length];
        for (int i = 0; i < assignments.length; i++) result[i] = assignments[i] == eligible;
        return result;
    }

    private static Moments pointMoments(List<RolePoint> points, boolean xAxis) {
        double sum = 0;
        for (RolePoint point : points) sum += xAxis ? point.x() : point.y();
        double mean = sum / points.size();
        double variance = 0;
        for (RolePoint point : points) {
            double value = xAxis ? point.x() : point.y();
            variance += Math.pow(value - mean, 2);
        }
        return new Moments(mean, Math.sqrt(variance / points.size()));
    }

    private static List<ClusterPoint> normalized(
            List<RolePoint> points,
            Moments xMoments,
            Moments yMoments) {
        List<ClusterPoint> result = new ArrayList<>(points.size());
        for (RolePoint point : points) result.add(new ClusterPoint(
            z(point.x(), xMoments), z(point.y(), yMoments)));
        return result;
    }

    private static int projectionIndex(List<ClusterPoint> points, boolean maximum) {
        int index = 0;
        double selected = points.get(0).x() + points.get(0).y();
        for (int i = 1; i < points.size(); i++) {
            double projection = points.get(i).x() + points.get(i).y();
            if ((maximum && projection > selected) || (!maximum && projection < selected)) {
                selected = projection;
                index = i;
            }
        }
        return index;
    }

    private static double distance(ClusterPoint point, Centroid centroid) {
        return Math.pow(point.x() - centroid.x(), 2) + Math.pow(point.y() - centroid.y(), 2);
    }

    private static int eligibleCluster(Centroid first, Centroid second) {
        if (first.x() > second.x() && first.y() > second.y()) return 0;
        if (second.x() > first.x() && second.y() > first.y()) return 1;
        return first.x() + first.y() >= second.x() + second.y() ? 0 : 1;
    }

    private static List<SourceChampion> sourceChampions(
            Filter filter,
            ChampionTierSource source,
            RoleEligibility eligible,
            IntFunction<ChampionView.Champion> champions) {
        List<SourceChampion> result = new ArrayList<>();
        for (Map.Entry<Integer, ChampionTierSource.Champion> entry : source.champions().entrySet()) {
            ChampionTierList.Statistics statistics = entry.getValue().statistics();
            ChampionView.Champion champion = champions.apply(entry.getKey());
            if (statistics != null && champion != null && eligible.isEligible(filter, entry.getKey())) {
                result.add(new SourceChampion(entry.getKey(), champion, statistics, entry.getValue().matchups(), 0));
            }
        }
        return result;
    }

    private static List<SourceChampion> adjustedChampions(
            List<SourceChampion> values,
            double roleAverageWinrate,
            double priorStrength) {
        List<SourceChampion> result = new ArrayList<>(values.size());
        for (SourceChampion value : values) {
            ChampionTierList.Statistics statistics = value.statistics();
            double adjustedWinrate = (statistics.wins() + priorStrength * roleAverageWinrate)
                / (statistics.picks() + priorStrength);
            result.add(new SourceChampion(value.id(), value.champion(), statistics, value.matchups(), adjustedWinrate));
        }
        return result;
    }

    private static double roleAverageWinrate(List<SourceChampion> values) {
        long picks = 0;
        long wins = 0;
        for (SourceChampion value : values) {
            picks += value.statistics().picks();
            wins += value.statistics().wins();
        }
        if (picks == 0) return 0;
        return (double) wins / picks;
    }

    private static double medianPicks(List<SourceChampion> values) {
        List<Integer> picks = new ArrayList<>(values.size());
        for (SourceChampion value : values) if (value.statistics().picks() > 0) picks.add(value.statistics().picks());
        return median(picks);
    }

    private static List<ChampionTierList.Matchup> counters(MatchupAnalysis matchups) {
        List<ChampionTierList.Matchup> result = reliable(matchups, false);
        result.sort(Comparator.comparingDouble(ChampionTierList.Matchup::weightedDelta)
            .thenComparing(matchup -> matchup.champion().id()));
        return limited(result);
    }

    private static List<ChampionTierList.Matchup> strongAgainst(MatchupAnalysis matchups) {
        List<ChampionTierList.Matchup> result = reliable(matchups, true);
        result.sort(Comparator.comparingDouble(ChampionTierList.Matchup::weightedDelta).reversed()
            .thenComparing(matchup -> matchup.champion().id()));
        return limited(result);
    }

    private static MatchupAnalysis matchups(
            Filter filter,
            SourceChampion value,
            RoleEligibility roleEligibility,
            IntFunction<ChampionView.Champion> champions) {
        List<ChampionTierSource.Matchup> roleMatchups = roleMatchups(filter, value.matchups(), roleEligibility);
        double priorStrength = medianMatchupGames(roleMatchups);
        if (priorStrength == 0) return new MatchupAnalysis(0, List.of());
        List<ChampionTierList.Matchup> result = new ArrayList<>();
        for (ChampionTierSource.Matchup source : roleMatchups) {
            ChampionView.Champion opponent = champions.apply(source.champion());
            if (opponent == null) continue;
            double rawWinrate = (double) source.wins() / source.games();
            double adjustedWinrate = (source.wins() + priorStrength * value.adjustedWinrate())
                / (source.games() + priorStrength);
            double weightedDelta = adjustedWinrate - value.adjustedWinrate();
            result.add(new ChampionTierList.Matchup(opponent, source.games(), source.wins(),
                source.games() - source.wins(), rawWinrate, adjustedWinrate, weightedDelta));
        }
        result.sort(Comparator.comparingDouble(ChampionTierList.Matchup::weightedDelta)
            .thenComparing(matchup -> matchup.champion().id()));
        return new MatchupAnalysis(priorStrength, result);
    }

    private static List<ChampionTierSource.Matchup> roleMatchups(
            Filter filter,
            List<ChampionTierSource.Matchup> values,
            RoleEligibility roleEligibility) {
        List<ChampionTierSource.Matchup> result = new ArrayList<>();
        for (ChampionTierSource.Matchup value : values) {
            if (value != null && value.games() > 0 && roleEligibility.isEligible(filter, value.champion()))
                result.add(value);
        }
        return result;
    }

    private static List<ChampionTierList.Matchup> reliable(MatchupAnalysis matchups, boolean strong) {
        List<ChampionTierList.Matchup> result = new ArrayList<>();
        for (ChampionTierList.Matchup matchup : matchups.values()) {
            if (matchup.games() < matchups.priorStrength()) continue;
            if ((strong && matchup.weightedDelta() > 0) || (!strong && matchup.weightedDelta() < 0)) result.add(matchup);
        }
        return result;
    }

    private static double medianMatchupGames(List<ChampionTierSource.Matchup> values) {
        List<Integer> games = new ArrayList<>(values.size());
        for (ChampionTierSource.Matchup value : values) if (value != null && value.games() > 0) games.add(value.games());
        return median(games);
    }

    private static List<ChampionTierList.Matchup> limited(List<ChampionTierList.Matchup> values) {
        return values.size() <= MATCHUP_LIMIT ? values : new ArrayList<>(values.subList(0, MATCHUP_LIMIT));
    }

    private static Moments adjustedWinrateMoments(List<SourceChampion> values, double priorStrength) {
        double mean = 0;
        for (SourceChampion value : values) mean += value.adjustedWinrate();
        mean /= values.size();
        double variance = 0;
        for (SourceChampion value : values) {
            double posteriorVariance = value.adjustedWinrate() * (1 - value.adjustedWinrate())
                / (value.statistics().picks() + priorStrength + 1);
            variance += Math.pow(value.adjustedWinrate() - mean, 2) + posteriorVariance;
        }
        return new Moments(mean, Math.sqrt(variance / values.size()));
    }

    private static Moments moments(List<SourceChampion> values, Metric metric) {
        double sum = 0;
        int count = 0;
        for (SourceChampion value : values) {
            Double metricValue = metric.value(value.statistics());
            if (metricValue == null) continue;
            sum += metricValue;
            count++;
        }
        if (count == 0) return new Moments(0, 0);
        double mean = sum / count;
        double variance = 0;
        for (SourceChampion value : values) {
            Double metricValue = metric.value(value.statistics());
            if (metricValue != null) variance += Math.pow(metricValue - mean, 2);
        }
        return new Moments(mean, Math.sqrt(variance / count));
    }

    private static double z(Double value, Moments moments) {
        return value == null || moments.deviation() == 0 ? 0 : (value - moments.mean()) / moments.deviation();
    }

    private static double median(List<Integer> values) {
        if (values.isEmpty()) return 0;
        values.sort(Integer::compareTo);
        int middle = values.size() / 2;
        return values.size() % 2 == 0 ? (values.get(middle - 1) + values.get(middle)) / 2d : values.get(middle);
    }

    private static String tier(double score) {
        if (score >= S_PLUS_SCORE) return "S+";
        if (score >= S_SCORE) return "S";
        if (score >= A_SCORE) return "A";
        if (score >= B_SCORE) return "B";
        return score >= C_SCORE ? "C" : "D";
    }

    private static ChampionView.Champion champion(int championId) {
        StaticChampion champion = ChampionUtils.getChampion(championId);
        return champion == null ? null : new ChampionView.Champion(championId, champion.getName(),
            ChampionUtils.getChampionProfilePic(championId));
    }

    private enum Metric {
        PICKRATE {
            @Override
            Double value(ChampionTierList.Statistics statistics) {
                return statistics.pickrate();
            }
        },
        BANRATE {
            @Override
            Double value(ChampionTierList.Statistics statistics) {
                return statistics.banrate();
            }
        };

        abstract Double value(ChampionTierList.Statistics statistics);
    }

    private record RoleBucket(String filterKey, ChampionTierSource source) {}

    private record RoleChampion(String filterKey, int championId) {}

    private record RoleEligibility(Set<RoleChampion> roles) {
        private RoleEligibility {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }

        private boolean isEligible(Filter filter, int champion) {
            return filter != null && roles.contains(new RoleChampion(filter.genericKey(), champion));
        }
    }

    private record RolePoint(RoleChampion roleChampion, double x, double y) {}

    private record ClusterPoint(double x, double y) {}

    private record Centroid(double x, double y) {}

    private record MatchupAnalysis(double priorStrength, List<ChampionTierList.Matchup> values) {}

    private record SourceChampion(
        int id,
        ChampionView.Champion champion,
        ChampionTierList.Statistics statistics,
        List<ChampionTierSource.Matchup> matchups,
        double adjustedWinrate
    ) {}

    private record Moments(double mean, double deviation) {}
}
