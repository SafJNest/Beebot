package com.safjnest.lol.model.record;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.safjnest.lol.model.ResponseMetadata;

public record ProfileRecordPage(Map<String, ProfileRecord> records, long lastUpdate, ResponseMetadata metadata) {

    public static ProfileRecordPage of(List<ProfileRecord> flat, long lastUpdate, ResponseMetadata metadata) {
        if (flat == null || flat.isEmpty()) return new ProfileRecordPage(Map.of(), lastUpdate, metadata);
        Map<String, ProfileRecord> grouped = new LinkedHashMap<>();
        for (ProfileRecord record : flat) {
            if (record == null || record.metric == null) continue;
            String key = record.metric.name().toLowerCase(Locale.ROOT);
            grouped.putIfAbsent(key, record);
        }
        return new ProfileRecordPage(Collections.unmodifiableMap(grouped), lastUpdate, metadata);
    }

    public ProfileRecordPage withMetadata(ResponseMetadata value) {
        return new ProfileRecordPage(records, lastUpdate, value);
    }
}
