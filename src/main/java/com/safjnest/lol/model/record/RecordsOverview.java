package com.safjnest.lol.model.record;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.safjnest.lol.model.ResponseMetadata;

public record RecordsOverview(
    Map<String, List<ProfileRecord>> records,
    ResponseMetadata metadata
) {

    public static RecordsOverview of(List<ProfileRecord> flat, ResponseMetadata metadata) {
        if (flat == null || flat.isEmpty()) return new RecordsOverview(Map.of(), metadata);
        Map<String, List<ProfileRecord>> grouped = new LinkedHashMap<>();
        for (ProfileRecord record : flat) {
            if (record == null || record.metric == null) continue;
            String key = record.metric.name().toLowerCase(Locale.ROOT);
            grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(record);
        }
        for (Map.Entry<String, List<ProfileRecord>> entry : grouped.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        return new RecordsOverview(Collections.unmodifiableMap(grouped), metadata);
    }
}
