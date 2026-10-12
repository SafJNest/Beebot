package com.hive;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;

import com.hive.job.Job;

public record HiveConfig(String identity, Clock clock, int queuedSnapshotLimit, int snapshotLimit,
    Duration shutdownTimeout, BiConsumer<Job<?>, String> observer) {

    public HiveConfig {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        Objects.requireNonNull(observer, "observer");
        if (identity.isBlank() || queuedSnapshotLimit < 0 || snapshotLimit < 1 || shutdownTimeout.isNegative())
            throw new IllegalArgumentException("Invalid Hive configuration");
    }

    public static HiveConfig defaults(String identity) {
        return new HiveConfig(identity, Clock.systemUTC(), 20, 10000, Duration.ofSeconds(30), (job, event) -> {});
    }
}
