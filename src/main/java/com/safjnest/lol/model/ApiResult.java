package com.safjnest.lol.model;

public record ApiResult<T>(Status status, T payload, ResponseMetadata metadata) {

    public ApiResult(Status status, T payload) {
        this(status, payload, null);
    }

    public enum Status {
        READY,
        PARTIAL,
        PENDING,
        NOT_FOUND
    }

    public static <T> ApiResult<T> ready(T payload) {
        return new ApiResult<>(Status.READY, payload, null);
    }

    public static <T> ApiResult<T> ready(T payload, ResponseMetadata metadata) {
        return new ApiResult<>(Status.READY, payload, metadata);
    }

    public static <T> ApiResult<T> partial(T payload) {
        return new ApiResult<>(Status.PARTIAL, payload, null);
    }

    public static <T> ApiResult<T> partial(T payload, ResponseMetadata metadata) {
        return new ApiResult<>(Status.PARTIAL, payload, metadata);
    }

    public static <T> ApiResult<T> pending() {
        return new ApiResult<>(Status.PENDING, null, null);
    }

    public static <T> ApiResult<T> pending(ResponseMetadata metadata) {
        return new ApiResult<>(Status.PENDING, null, metadata);
    }

    public static <T> ApiResult<T> notFound() {
        return new ApiResult<>(Status.NOT_FOUND, null, null);
    }
}
