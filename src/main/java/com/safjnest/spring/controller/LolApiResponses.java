package com.safjnest.spring.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.spring.dto.LolApiError;

public final class LolApiResponses {

    private LolApiResponses() {}

    public static <T> ResponseEntity<?> from(
            ApiResult<T> result,
            String pendingCode,
            String pendingMessage,
            String notFoundMessage
    ) {
        return switch (result.status()) {
            case READY, PARTIAL -> ResponseEntity.ok(result.payload());
            case PENDING -> ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new LolApiError(HttpStatus.ACCEPTED.value(), pendingCode, pendingMessage, result.metadata()));
            case NOT_FOUND -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        };
    }
}
