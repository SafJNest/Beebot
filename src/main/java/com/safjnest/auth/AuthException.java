package com.safjnest.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class AuthException extends ResponseStatusException {
    private final String code;
    private final Double retryAfter;
    public AuthException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }
    public AuthException(HttpStatus status, String code, String message, Double retryAfter) {
        super(status, message);
        this.code = code;
        this.retryAfter = retryAfter;
    }
    public Double retryAfter() { return retryAfter; }
    public String code() { return code; }
}
