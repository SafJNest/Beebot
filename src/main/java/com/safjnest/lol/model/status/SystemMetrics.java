package com.safjnest.lol.model.status;

import java.util.List;

public record SystemMetrics(
    Cpu cpu,
    Memory memory,
    Disk disk,
    Network network
) {

    public record Cpu(
        Double usage,
        Integer cores,
        List<Double> perCore
    ) {
    }

    public record Memory(
        Long used,
        Long available,
        Long total
    ) {
    }

    public record Disk(
        Long used,
        Long available,
        Long total
    ) {
    }

    public record Network(
        Long receivedBytesPerSecond,
        Long sentBytesPerSecond
    ) {
    }
}
