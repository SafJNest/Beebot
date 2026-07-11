package com.safjnest.spring.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.safjnest.spring.dto.LolApiError;

@RestControllerAdvice
public class LolApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<LolApiError> handleResponseStatus(ResponseStatusException exception) {
        return error(
            exception.getStatusCode(),
            codeFor(exception.getStatusCode()),
            exception.getReason()
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<LolApiError> handleMissingParameter(MissingServletRequestParameterException exception) {
        return error(
            HttpStatus.BAD_REQUEST,
            "invalid_request",
            "Missing required query parameter '" + exception.getParameterName() + "'"
        );
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<LolApiError> handleMissingPathVariable(MissingPathVariableException exception) {
        return error(
            HttpStatus.BAD_REQUEST,
            "invalid_request",
            "Missing required path parameter '" + exception.getVariableName() + "'"
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<LolApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error(
            HttpStatus.BAD_REQUEST,
            "invalid_request",
            "Invalid value for parameter '" + exception.getName() + "'"
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<LolApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return error(
            HttpStatus.METHOD_NOT_ALLOWED,
            "method_not_allowed",
            "HTTP method '" + exception.getMethod() + "' is not supported for this endpoint"
        );
    }

    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<LolApiError> handleNotFound(Exception exception) {
        return error(HttpStatus.NOT_FOUND, "not_found", "Endpoint not found");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<LolApiError> handleUnexpected(Exception exception) {
        exception.printStackTrace();
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected server error");
    }

    private ResponseEntity<LolApiError> error(HttpStatusCode status, String code, String message) {
        String errorMessage = message != null && !message.isBlank()
            ? message
            : HttpStatus.valueOf(status.value()).getReasonPhrase();
        return ResponseEntity.status(status).body(new LolApiError(status.value(), code, errorMessage));
    }

    private String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "invalid_request";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            default -> "request_failed";
        };
    }
}
