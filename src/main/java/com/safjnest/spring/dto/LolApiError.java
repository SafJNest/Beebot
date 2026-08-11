package com.safjnest.spring.dto;

import com.safjnest.lol.model.ResponseMetadata;

public record LolApiError(
    int status,
    String code,
    String message,
    ResponseMetadata metadata
) {

    public LolApiError(int status, String code, String message) {
        this(status, code, message, null);
    }
}
