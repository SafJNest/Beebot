package com.R4B.api.dto.status;

import com.R4B.api.dto.*;
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
