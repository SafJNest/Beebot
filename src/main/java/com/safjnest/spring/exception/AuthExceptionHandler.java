package com.safjnest.spring.exception;

import com.safjnest.auth.AuthException;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AuthExceptionHandler {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthError(String code, String message, Double retryAfter) {
        public AuthError(String code, String message) { this(code, message, null); }
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<AuthError> handle(AuthException error) {
        var response = ResponseEntity.status(error.getStatusCode());
        if (error.retryAfter() != null) response.header("Retry-After", Long.toString((long) Math.ceil(error.retryAfter())));
        return response.body(new AuthError(error.code(), error.getReason(), error.retryAfter()));
    }
}
