package com.safjnest.lol.model;

public class MatchLookup {

    public enum Status {
        READY,
        PENDING,
        NOT_FOUND
    }

    private final Status status;
    private final Match match;

    private MatchLookup(Status status, Match match) {
        this.status = status;
        this.match = match;
    }

    public static MatchLookup ready(Match match) {
        return new MatchLookup(Status.READY, match);
    }

    public static MatchLookup pending() {
        return new MatchLookup(Status.PENDING, null);
    }

    public static MatchLookup notFound() {
        return new MatchLookup(Status.NOT_FOUND, null);
    }

    public Status getStatus() {
        return status;
    }

    public Match getMatch() {
        return match;
    }
}
