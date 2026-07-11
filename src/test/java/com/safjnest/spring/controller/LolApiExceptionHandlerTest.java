package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.spring.dto.LolApiError;

public class LolApiExceptionHandlerTest {

    @Test
    public void shouldReturnTheSameBodyForBadRequests() {
        LolApiExceptionHandler handler = new LolApiExceptionHandler();
        ResponseEntity<LolApiError> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid LeagueShard 'EUW'")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(new LolApiError(400, "invalid_request", "Invalid LeagueShard 'EUW'"), response.getBody());
    }

    @Test
    public void shouldReturnTheSameBodyForNotFoundResponses() {
        LolApiExceptionHandler handler = new LolApiExceptionHandler();
        ResponseEntity<LolApiError> response = handler.handleResponseStatus(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found")
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(new LolApiError(404, "not_found", "Profile not found"), response.getBody());
    }

    @Test
    public void shouldReturnTheSameBodyForMissingParameters() {
        LolApiExceptionHandler handler = new LolApiExceptionHandler();
        ResponseEntity<LolApiError> response = handler.handleMissingParameter(
            new MissingServletRequestParameterException("q", "String")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(
            new LolApiError(400, "invalid_request", "Missing required query parameter 'q'"),
            response.getBody()
        );
    }
}
