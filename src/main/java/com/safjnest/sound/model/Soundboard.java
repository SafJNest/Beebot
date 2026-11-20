package com.safjnest.sound.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.safjnest.database.R2;
import com.safjnest.database.sql.QueryRecord;

public record Soundboard(String id, String guildId, String userId, String name, String thumbnailUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer soundCount, boolean canEdit,
        @JsonProperty("public") boolean isPublic, SortMode sortMode, long likes, long plays, long commentCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) Viewer viewer) {

    public enum SortMode {
        NAME_ASC,
        NAME_DESC,
        MANUAL
    }

    public record PublicDetail(Soundboard board, List<SoundCatalog.Card> sounds) {}

    public record Edit(String name, Boolean isPublic, SortMode sortMode, List<String> soundIds) {}

    public record Viewer(SoundVote vote) {}

    public static Soundboard fromRow(QueryRecord row, boolean canEdit, boolean includeCount, boolean includeViewer) {
        String key = row.get("thumbnail");
        String thumbnail = key == null || key.isBlank() ? null : R2.getInstance().url(key);
        Integer soundCount = includeCount ? row.getAsInt("sound_count") : null;
        Viewer viewer = includeViewer ? new Viewer(SoundVote.fromValue(row.getAsInt("vote"))) : null;

        return new Soundboard(row.get("id"), row.get("guild_id"), row.get("user_id"), row.get("name"), thumbnail,
            soundCount, canEdit, row.getAsBoolean("public"), SortMode.valueOf(row.get("sort_mode")),
            row.getAsLong("likes"), row.getAsLong("plays"), row.getAsLong("comments"), viewer);
    }
}
