package com.safjnest.spring.controller;

import com.safjnest.spring.controller.AuthExceptionHandler.AuthError;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {DiscordAuthController.class, GuildController.class})
public class AuthRequestExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthError> handle(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new AuthError( "invalid_request", "Invalid JSON request body"));
    }
}
