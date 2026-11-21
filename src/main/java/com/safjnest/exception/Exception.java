package com.safjnest.exception;

import java.util.Objects;

public final class Exception extends RuntimeException {

    public enum Type {
        INVALID_REQUEST,
        UNAUTHENTICATED,
        FORBIDDEN,
        NOT_FOUND,
        CONFLICT,
        INVALID_UPSTREAM_RESPONSE,
        UNAVAILABLE,
        RATE_LIMITED
    }

    private final Type type;
    private final String code;
    private final Double retryAfter;

    public Exception(Type type, String code, String message) {
        this(type, code, message, null, null);
    }

    public Exception(Type type, String code, String message, Double retryAfter) {
        this(type, code, message, null, retryAfter);
    }

    public Exception(Type type, String code, String message, Throwable cause, Double retryAfter) {
        super(message, cause);

        this.type = Objects.requireNonNull(type);
        this.code = Objects.requireNonNull(code);
        this.retryAfter = retryAfter;
    }

    public static Exception invalidRequest(String message) {
        return new Exception(Type.INVALID_REQUEST, "invalid_request", message);
    }

    public Type getType() {
        return type;
    }

    public String getCode() {
        return code;
    }

    public Double getRetryAfter() {
        return retryAfter;
    }
}
