package com.safjnest.lol.model.status;

public record JvmMetrics(
    Double cpu,
    Memory memory,
    Integer threads,
    Integer peakThreads,
    Long uptime
) {

    public record Memory(
        Long used,
        Long committed,
        Long max
    ) {
    }
}
