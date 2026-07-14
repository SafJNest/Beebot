package com.safjnest.lol.model;

public record ApiResult<T>(Status status, T payload) {

    public enum Status {
        READY,
        PARTIAL,
        PENDING,
        NOT_FOUND
    }

    public static <T> ApiResult<T> ready(T payload) {
        return new ApiResult<>(Status.READY, payload);
    }

    public static <T> ApiResult<T> partial(T payload) {
        return new ApiResult<>(Status.PARTIAL, payload);
    }

    public static <T> ApiResult<T> pending() {
        return new ApiResult<>(Status.PENDING, null);
    }

    public static <T> ApiResult<T> notFound() {
        return new ApiResult<>(Status.NOT_FOUND, null);
    }
}
