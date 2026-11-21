package com.safjnest.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public record R2Settings(boolean enabled, String accountId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String accessKeyId,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String secretAccessKey,
        String soundBucket, String soundPrefix, String soundDirectory, String cdnUrl) {

    @Override
    public String toString() {
        return "R2Settings[enabled=" + enabled + "]";
    }
}
