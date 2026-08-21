package com.safjnest.status;

import java.util.ArrayList;
import java.util.List;

import com.safjnest.lol.model.status.MongoMemoryMetrics;
import com.safjnest.lol.model.status.MongoMetrics;
import com.safjnest.lol.model.status.MongoOperationRates;
import com.safjnest.lol.model.status.MongoOperationSample;
import com.safjnest.lol.model.status.MongoOperationsMetrics;
import com.safjnest.lol.model.status.MongoPerformanceMetrics;
import com.safjnest.nosql.MongoCommandMonitor;
import com.safjnest.nosql.MongoDB;
import com.safjnest.nosql.MongoServerStatusSnapshot;

public final class MongoMetricsSampler {

    public static final int SAMPLE_INTERVAL_SECONDS = 1;
    public static final int SERIES_CAPACITY = 300;

    private static final MongoMetricsSampler INSTANCE = new MongoMetricsSampler();

    private final Object lock = new Object();
    private final List<MongoOperationSample> series = new ArrayList<>(SERIES_CAPACITY);
    private MongoOperationRates currentRates = emptyRates();
    private MongoPerformanceMetrics performance = emptyPerformance();
    private Long connections;
    private MongoMemoryMetrics memory;
    private MongoServerStatusSnapshot previousStatus;
    private MongoCommandMonitor.ClientOpcounters previousClient;
    private long previousSampleAtNanos;
    private volatile MongoMetrics snapshot = emptyMetrics();

    private MongoMetricsSampler() {}

    public static void sampleTick() {
        INSTANCE.tick();
    }

    public static MongoMetrics snapshot() {
        return INSTANCE.snapshot;
    }

    // ============================================================================

    private void tick() {
        synchronized (lock) {
            long nowNanos = System.nanoTime();
            long elapsedNanos = previousSampleAtNanos == 0 ? 0 : nowNanos - previousSampleAtNanos;
            MongoServerStatusSnapshot status = readServerStatus();
            MongoOperationRates rates = ratesFromClient(elapsedNanos);
            if (rates == null) rates = ratesFromStatus(status, elapsedNanos);
            if (rates == null) rates = emptyRates();

            MongoCommandMonitor.tickSecond();
            performance = MongoCommandMonitor.snapshotPerformance();
            currentRates = rates;
            appendSample(new MongoOperationSample(System.currentTimeMillis(), rates));
            connections = status == null ? connections : status.connections();
            memory = status == null ? memory : new MongoMemoryMetrics(status.residentMb(), status.virtualMb());
            previousSampleAtNanos = nowNanos;
            snapshot = new MongoMetrics(
                    new MongoOperationsMetrics(SAMPLE_INTERVAL_SECONDS, currentRates, List.copyOf(series)),
                    performance,
                    connections,
                    memory);
        }
    }

    private MongoServerStatusSnapshot readServerStatus() {
        try {
            return MongoDB.serverStatusSnapshot();
        } catch (Exception ignored) {
            return null;
        }
    }

    private MongoOperationRates ratesFromStatus(MongoServerStatusSnapshot status, long elapsedNanos) {
        if (status == null || elapsedNanos <= 0) {
            if (status != null) previousStatus = status;
            return null;
        }
        MongoCommandMonitor.ClientOpcounters counters = status.opcounters();
        MongoOperationRates rates = null;
        if (previousStatus != null) {
            rates = ratesFromDelta(previousStatus.opcounters(), counters, elapsedNanos);
        }
        previousStatus = status;
        return rates;
    }

    private MongoOperationRates ratesFromClient(long elapsedNanos) {
        MongoCommandMonitor.ClientOpcounters counters = MongoCommandMonitor.clientOpcounters();
        if (elapsedNanos <= 0) {
            previousClient = counters;
            return null;
        }
        MongoOperationRates rates = null;
        if (previousClient != null) {
            rates = ratesFromDelta(previousClient, counters, elapsedNanos);
        }
        previousClient = counters;
        return rates;
    }

    private static MongoOperationRates ratesFromDelta(
            MongoCommandMonitor.ClientOpcounters previous,
            MongoCommandMonitor.ClientOpcounters current,
            long elapsedNanos) {
        double insert = StatusRates.opsPerSecond(previous.insert(), current.insert(), elapsedNanos);
        double query = StatusRates.opsPerSecond(previous.query(), current.query(), elapsedNanos);
        double update = StatusRates.opsPerSecond(previous.update(), current.update(), elapsedNanos);
        double delete = StatusRates.opsPerSecond(previous.delete(), current.delete(), elapsedNanos);
        double command = StatusRates.opsPerSecond(previous.command(), current.command(), elapsedNanos);
        double getmore = StatusRates.opsPerSecond(previous.getmore(), current.getmore(), elapsedNanos);
        double total = insert + query + update + delete + command + getmore;
        return new MongoOperationRates(insert, query, update, delete, command, getmore, total);
    }

    private void appendSample(MongoOperationSample sample) {
        if (series.size() >= SERIES_CAPACITY) series.remove(0);
        series.add(sample);
    }

    private static MongoMetrics emptyMetrics() {
        return new MongoMetrics(
                new MongoOperationsMetrics(SAMPLE_INTERVAL_SECONDS, emptyRates(), List.of()),
                emptyPerformance(),
                null,
                null);
    }

    private static MongoOperationRates emptyRates() {
        return new MongoOperationRates(0, 0, 0, 0, 0, 0, 0);
    }

    private static MongoPerformanceMetrics emptyPerformance() {
        return new MongoPerformanceMetrics(
                null,
                null,
                MongoCommandMonitor.RECENT_WINDOW_SECONDS,
                MongoCommandMonitor.SLOW_WINDOW_SECONDS,
                java.util.Map.of(),
                List.of(),
                List.of());
    }
}
