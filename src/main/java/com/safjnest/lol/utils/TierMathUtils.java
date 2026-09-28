package com.safjnest.lol.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Shared statistical primitives for tier and matchup ranking.
 */
public final class TierMathUtils {

    public static final double S_PLUS_SCORE = 2.0;
    public static final double S_SCORE = 1.0;
    public static final double A_SCORE = 0.25;
    public static final double B_SCORE = -0.25;
    public static final double C_SCORE = -1.0;

    private TierMathUtils() {}

    public static double median(Collection<? extends Number> values) {
        if (values == null || values.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(values.size());
        for (Number value : values) if (value != null) sorted.add(value.doubleValue());
        if (sorted.isEmpty()) return 0;
        sorted.sort(Double::compareTo);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
            ? (sorted.get(middle - 1) + sorted.get(middle)) / 2d
            : sorted.get(middle);
    }

    public static Moments moments(Collection<? extends Number> values) {
        if (values == null || values.isEmpty()) return new Moments(0, 0);
        double sum = 0;
        int count = 0;
        for (Number value : values) {
            if (value == null) continue;
            double current = value.doubleValue();
            if (!Double.isFinite(current)) continue;
            sum += current;
            count++;
        }
        if (count == 0) return new Moments(0, 0);
        double mean = sum / count;
        double variance = 0;
        for (Number value : values) {
            if (value == null) continue;
            double current = value.doubleValue();
            if (!Double.isFinite(current)) continue;
            variance += Math.pow(current - mean, 2);
        }
        return new Moments(mean, Math.sqrt(variance / count));
    }

    public static Moments posteriorRateMoments(
            List<Double> adjustedRates,
            List<? extends Number> sampleSizes,
            double priorStrength) {
        if (adjustedRates == null || sampleSizes == null || adjustedRates.isEmpty()
                || adjustedRates.size() != sampleSizes.size()) return new Moments(0, 0);
        double sum = 0;
        int count = 0;
        for (Double rate : adjustedRates) {
            if (rate == null || !Double.isFinite(rate)) continue;
            sum += rate;
            count++;
        }
        if (count == 0) return new Moments(0, 0);
        double mean = sum / count;
        double variance = 0;
        int varianceCount = 0;
        for (int i = 0; i < adjustedRates.size(); i++) {
            Double rate = adjustedRates.get(i);
            Number sample = sampleSizes.get(i);
            if (rate == null || sample == null || !Double.isFinite(rate)) continue;
            double denominator = Math.max(0, sample.doubleValue()) + Math.max(0, priorStrength) + 1;
            double posteriorVariance = denominator <= 0 ? 0 : rate * (1 - rate) / denominator;
            variance += Math.pow(rate - mean, 2) + Math.max(0, posteriorVariance);
            varianceCount++;
        }
        return new Moments(mean, varianceCount == 0 ? 0 : Math.sqrt(variance / varianceCount));
    }

    public static double z(Double value, Moments moments) {
        if (value == null || moments == null || !Double.isFinite(value)
                || moments.deviation() == 0 || !Double.isFinite(moments.deviation())) return 0;
        return (value - moments.mean()) / moments.deviation();
    }

    public static double shrinkRate(long wins, long games, double priorMean, double priorStrength) {
        if (games <= 0) return priorMean;
        double strength = Math.max(0, priorStrength);
        if (strength == 0) return (double) wins / games;
        return (wins + strength * priorMean) / (games + strength);
    }

    public static Double shrinkValue(Double value, long games, double priorMean, double priorStrength) {
        if (value == null || !Double.isFinite(value)) return null;
        if (games <= 0) return value;
        double strength = Math.max(0, priorStrength);
        if (strength == 0) return value;
        return (value * games + priorMean * strength) / (games + strength);
    }

    public static String tier(double score) {
        if (score >= S_PLUS_SCORE) return "S+";
        if (score >= S_SCORE) return "S";
        if (score >= A_SCORE) return "A";
        if (score >= B_SCORE) return "B";
        if (score >= C_SCORE) return "C";
        return "D";
    }

    public record Moments(double mean, double deviation) {}
}
