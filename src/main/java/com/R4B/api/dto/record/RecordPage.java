package com.R4B.api.dto.record;

import java.util.List;

import com.R4B.api.dto.*;
import com.R4B.lol.RecordMetric;

public record RecordPage(
    RecordMetric metric,
    List<ProfileRecord> records,
    int limit,
    int offset,
    long total,
    boolean hasMore,
    ResponseMetadata metadata
) {

    public RecordPage withMetadata(ResponseMetadata value) {
        return new RecordPage(metric, records, limit, offset, total, hasMore, value);
    }
}
