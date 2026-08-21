package com.safjnest.status;

final class StatusRates {

    private StatusRates() {}

    static long bytesPerSecond(long previous, long current, long elapsedNanos) {
        if (elapsedNanos <= 0 || current < previous) return 0;
        return Math.round((current - previous) * 1_000_000_000.0 / elapsedNanos);
    }

    static double opsPerSecond(long previous, long current, long elapsedNanos) {
        if (elapsedNanos <= 0 || current < previous) return 0;
        return Math.round((current - previous) * 1_000_000_000.0 / elapsedNanos * 10.0) / 10.0;
    }

    static double percent(double load) {
        if (Double.isNaN(load) || load < 0) return 0;
        return Math.round(Math.min(100, load * 100) * 10.0) / 10.0;
    }
}
