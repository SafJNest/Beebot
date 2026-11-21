package com.safjnest.sound.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record SoundComment(String id, @JsonInclude(JsonInclude.Include.NON_NULL) String soundId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String soundboardId, String parentId, Map<String, Object> author,
        String text, String createdAt, String editedAt, boolean deleted, boolean pinned, boolean hearted,
        long likes, long replyCount, boolean uploader, @JsonInclude(JsonInclude.Include.NON_NULL) Viewer viewer) {

    public enum Sort {
        TOP,
        NEWEST,
        OLDEST,
        MOST_DISLIKED
    }

    public record Viewer(boolean ownedByMe, SoundVote vote) {}

    public record Edit(String text, Boolean pinned, Boolean hearted) {}

    public record Change(SoundComment comment, long commentCount, String previousPinnedId) {}

    public record Removal(SoundComment comment, long commentCount) {}
}
