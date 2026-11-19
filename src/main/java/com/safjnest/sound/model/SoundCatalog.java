package com.safjnest.sound.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SoundCatalog {

    private SoundCatalog() {}

    public enum Sort {
        RELEVANCE,
        POPULAR,
        NEWEST,
        MOST_LIKED,
        MOST_DISLIKED
    }

    public record Search(String q, String tag, String uploaderId, Long minDurationMs, Long maxDurationMs,
            Sort sort, Integer limit, String cursor) {}

    public record Card(String id, String name, String description, @JsonProperty("public") boolean isPublic, String createdAt, Long durationMs,
            String audioUrl, String imageUrl, Map<String, Object> uploader, List<Tag> tags,
            long plays, long listeners, long likes, long dislikes, long commentCount, @JsonInclude(JsonInclude.Include.NON_NULL) Viewer viewer) {}

    public record Viewer(boolean ownedByMe, SoundVote vote) {}

    public record Page<T>(List<T> items, String nextCursor) {}

    public record Results(List<Card> items, List<Card> mine, String nextCursor) {}

    public record TagCount(String id, String name, long soundCount) {}

    public record Suggestions(List<Card> sounds, List<TagCount> tags) {}

    public record Explore(List<TagCount> popularTags, List<Card> trending, List<Card> latest) {}

    public record Creator(Map<String, Object> user, String uploadingSince, long soundCount,
            long plays, long likes, long dislikes, long commentCount) {}
}
