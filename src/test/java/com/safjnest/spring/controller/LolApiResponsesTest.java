package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.spring.dto.LolApiError;

public class LolApiResponsesTest {

    @Test
    public void mapsReadyAndPartialToOk() {
        ResponseEntity<?> ready = LolApiResponses.from(ApiResult.ready("ready"), "pending", "pending", "missing");
        ResponseEntity<?> partial = LolApiResponses.from(ApiResult.partial("partial"), "pending", "pending", "missing");

        assertEquals(HttpStatus.OK, ready.getStatusCode());
        assertEquals("ready", ready.getBody());
        assertEquals(HttpStatus.OK, partial.getStatusCode());
        assertEquals("partial", partial.getBody());
    }

    @Test
    public void mapsPendingToTypedApiError() {
        ResponseEntity<?> response = LolApiResponses.from(
            ApiResult.pending(), "data_pending", "Data is pending", "missing"
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody() instanceof LolApiError);
        LolApiError error = (LolApiError) response.getBody();
        assertEquals("data_pending", error.code());
    }

    @Test
    public void mapsNotFoundToNotFoundException() {
        try {
            LolApiResponses.from(ApiResult.notFound(), "pending", "pending", "missing");
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
            return;
        }
        throw new AssertionError("Expected a not found response");
    }
}
