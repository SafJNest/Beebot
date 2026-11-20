package com.safjnest.sound.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public enum SoundVote {
    LIKE(1),
    DISLIKE(-1);

    private final int value;

    SoundVote(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static SoundVote fromValue(int value) {
        return switch (value) {
            case 1 -> LIKE;
            case -1 -> DISLIKE;
            default -> null;
        };
    }

    public record Result(@JsonInclude(JsonInclude.Include.NON_NULL) String soundId,
            @JsonInclude(JsonInclude.Include.NON_NULL) String soundboardId, String commentId, long likes,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long dislikes, SoundVote vote) {}
}
