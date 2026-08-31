package com.safjnest.lol.model.record;

import java.util.List;

import com.safjnest.lol.model.ResponseMetadata;

public record ProfileRecordPage(List<ProfileRecord> records, long lastUpdate, ResponseMetadata metadata) {

    public ProfileRecordPage withMetadata(ResponseMetadata value) {
        return new ProfileRecordPage(records, lastUpdate, value);
    }
}
