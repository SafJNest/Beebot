package com.safjnest.spring.exception;

import com.safjnest.exception.Exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public final class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> application(Exception exception, HttpServletRequest request) {
        return ApiErrors.from(request, exception);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        return ApiErrors.from(request, exception.getStatusCode(), codeFor(exception.getStatusCode()), exception.getReason());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> missingParameter(MissingServletRequestParameterException exception, HttpServletRequest request) {
        return ApiErrors.from(request, HttpStatus.BAD_REQUEST, "invalid_request",
            "Missing required query parameter '" + exception.getParameterName() + "'");
    }

    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<?> missingPathVariable(MissingPathVariableException exception, HttpServletRequest request) {
        return ApiErrors.from(request, HttpStatus.BAD_REQUEST, "invalid_request",
            "Missing required path parameter '" + exception.getVariableName() + "'");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> typeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String message = ApiErrors.isBeebot(request)
            ? "Invalid request parameter"
            : "Invalid value for parameter '" + exception.getName() + "'";

        return ApiErrors.from(request, HttpStatus.BAD_REQUEST, "invalid_request", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> invalidBody(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ApiErrors.from(request, HttpStatus.BAD_REQUEST, "invalid_request", "Invalid JSON request body");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> uploadTooLarge(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return ApiErrors.from(request, HttpStatus.PAYLOAD_TOO_LARGE, "upload_too_large", "Upload exceeds the size limit");
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<?> invalidUpload(java.lang.Exception exception, HttpServletRequest request) {
        return ApiErrors.from(request, HttpStatus.BAD_REQUEST, "invalid_request", "Invalid multipart body or missing part");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<?> unsupportedMediaType(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        if (!ApiErrors.isBeebot(request))
            return unexpected(exception, request);

        return ApiErrors.from(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
            "Unsupported request content type");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<?> methodNotSupported(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return ApiErrors.from(request, HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed",
            "HTTP method '" + exception.getMethod() + "' is not supported for this endpoint");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<?> notFound(java.lang.Exception exception, HttpServletRequest request) {
        LOGGER.warn("No endpoint mapped for {} {}", request.getMethod(), ApiErrors.path(request));

        return ApiErrors.from(request, HttpStatus.NOT_FOUND, "not_found", "Endpoint not found");
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void asyncRequestNotUsable(AsyncRequestNotUsableException exception) {
    }

    @ExceptionHandler(java.lang.Exception.class)
    public ResponseEntity<?> unexpected(java.lang.Exception exception, HttpServletRequest request) {
        LOGGER.error("Unexpected API error for {} {}", request.getMethod(), ApiErrors.path(request), exception);

        return ApiErrors.from(request, HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Unexpected server error");
    }

    /* -------------------------------------------------------------------------- */

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "invalid_request";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            default -> "request_failed";
        };
    }
}
