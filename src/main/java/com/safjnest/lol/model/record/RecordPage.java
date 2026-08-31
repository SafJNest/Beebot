package com.safjnest.lol.model.record;

import java.util.List;

import com.safjnest.lol.model.ResponseMetadata;

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
