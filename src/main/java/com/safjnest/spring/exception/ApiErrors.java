package com.safjnest.spring.exception;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.safjnest.exception.Exception;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.utils.JsonCodec;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class ApiErrors {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BeebotError(String code, String message, Double retryAfter) {}

    public record LolApiError(int status, String code, String message, ResponseMetadata metadata) {}

    private ApiErrors() {
    }

    public static ResponseEntity<?> from(HttpServletRequest request, Exception exception) {
        HttpStatus status = switch (exception.getType()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_UPSTREAM_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case UNAVAILABLE, RATE_LIMITED -> HttpStatus.SERVICE_UNAVAILABLE;
        };

        return response(request, status, exception.getCode(), exception.getMessage(), exception.getRetryAfter());
    }

    public static ResponseEntity<?> from(HttpServletRequest request, HttpStatusCode status, String code, String message) {
        return response(request, status, code, message, null);
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
        ResponseEntity<?> mapped = from(request, exception);

        response.setStatus(mapped.getStatusCode().value());
        mapped.getHeaders().forEach((name, values) -> {
            for (String value : values)
                response.addHeader(name, value);
        });
        response.getWriter().write(JsonCodec.toJson(mapped.getBody()));
    }

    public static String path(HttpServletRequest request) {
        String included = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        if (included != null)
            return included;

        String path = request.getServletPath();
        if (path != null && !path.isEmpty())
            return path;

        return request.getRequestURI().substring(request.getContextPath().length());
    }

    public static boolean isBeebot(HttpServletRequest request) {
        String path = path(request);

        return path.equals("/api/beebot") || path.startsWith("/api/beebot/");
    }

    /* -------------------------------------------------------------------------- */

    private static ResponseEntity<?> response(HttpServletRequest request, HttpStatusCode status,
            String code, String message, Double retryAfter) {
        String description = message;
        if (description == null || description.isBlank()) {
            HttpStatus known = HttpStatus.resolve(status.value());
            description = known == null ? "Request failed" : known.getReasonPhrase();
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
        if (retryAfter != null)
            response.header(HttpHeaders.RETRY_AFTER, Long.toString((long) Math.ceil(retryAfter)));

        Object body = isBeebot(request)
            ? new BeebotError(code, description, retryAfter)
            : new LolApiError(status.value(), code, description, null);

        return response.body(body);
    }
}
