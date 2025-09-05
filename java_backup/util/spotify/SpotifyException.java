package com.safjnest.util.spotify;

public class SpotifyException extends IllegalArgumentException {
    public enum ErrorType { NOT_LINKED, HISTORY_MISSING, NOT_SUPPORTED, API_ERROR, ERROR_PARSING, INVALID_TIME_RANGE, NO_AUTH }
    private final ErrorType type;

    public SpotifyException(ErrorType type, String message) {
        super(message);
        this.type = type;
    }

    public ErrorType getType() { return type; }
}
