package com.R4B.api.dto;

import com.R4B.api.dto.*;

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
