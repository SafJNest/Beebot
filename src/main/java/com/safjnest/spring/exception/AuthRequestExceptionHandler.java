package com.safjnest.spring.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.safjnest.spring.controller.beebot.DiscordAuthController;
import com.safjnest.spring.controller.beebot.GuildController;
import com.safjnest.spring.controller.beebot.SoundController;
import com.safjnest.spring.exception.AuthExceptionHandler.AuthError;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {DiscordAuthController.class, GuildController.class, SoundController.class})
public class AuthRequestExceptionHandler {
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AuthError> parameter(Exception exception) {
        return ResponseEntity.badRequest().body(new AuthError("invalid_request", "Invalid request parameter"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthError> handle(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new AuthError( "invalid_request", "Invalid JSON request body"));
    }
}
