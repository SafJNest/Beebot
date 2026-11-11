package com.safjnest.model.sound;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.ArrayList;

import java.sql.Timestamp;
import java.util.List;

import com.safjnest.core.audio.types.AudioType;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Sound {
    @JsonProperty("id")
    private final String ID;
    @JsonProperty("guildId")
    private final String GUILD_ID;
    @JsonProperty("userId")
    private final String USER_ID;

    @JsonProperty("name")
    private volatile String name;
    private String extension;
    @JsonProperty("public")
    private volatile boolean isPublic;
    private Timestamp timestamp;

    @JsonProperty("plays")
    private volatile int plays;
    @JsonProperty("likes")
    private volatile int likes;
    @JsonProperty("dislikes")
    private volatile int dislikes;

    private AudioTrack track;

    private volatile List<Tag> tags;

    @JsonProperty private Map<String, Object> uploader;
    @JsonProperty private String url;
    @JsonProperty private Long durationMs;
    @JsonProperty private long listeners;
    @JsonProperty private long myPlays;
    @JsonProperty private boolean likedByMe;
    @JsonProperty private boolean dislikedByMe;
    @JsonProperty private boolean ownedByMe;
    @JsonProperty private boolean canEdit;
    @JsonProperty private boolean canDelete;
    @JsonProperty private long soundboardCount;

    public static Sound forResponse(Sound source, QueryRecord current, List<Tag> tags, QueryRecord extra,
            String viewerId, boolean administrator, String cdn, Map<String, Object> uploader) {
        Sound result = new Sound(current, tags);
        result.uploader = uploader;
        result.url = cdn == null || cdn.isBlank() ? null : cdn.replaceAll("/+$", "") + "/sounds/" + result.ID + "." + result.extension;
        result.durationMs = source.durationMs;
        result.listeners = extra.getAsLong("listeners");
        result.myPlays = extra.getAsLong("my_plays");
        result.likedByMe = extra.getAsInt("vote") == 1;
        result.dislikedByMe = extra.getAsInt("vote") == -1;
        result.ownedByMe = viewerId.equals(result.USER_ID);
        result.canEdit = result.ownedByMe;
        result.canDelete = result.ownedByMe || administrator;
        result.soundboardCount = extra.getAsLong("boards");
        return result;
    }

    @JsonProperty("createdAt")
    public String getCreatedAt() { return timestamp == null ? null : timestamp.toInstant().toString(); }

    @JsonProperty("tags")
    public List<Tag> getJsonTags() {
        ArrayList<Tag> result = new ArrayList<>();
        if (tags != null) for (Tag tag : tags) if (!tag.isEmpty()) result.add(tag);
        return result;
    }

    public Sound() {
        this.ID = null;
        this.GUILD_ID = null;
        this.USER_ID = null;
        this.name = null;
        this.extension = null;
        this.isPublic = false;
        this.timestamp = null;
        this.tags = null;
    }

    public Sound(QueryRecord data, List<Tag> tags) {
        this.ID = data.get("id");
        this.GUILD_ID = data.get("guild_id");
        this.USER_ID = data.get("user_id");

        this.name = data.get("name");
        this.extension = data.get("extension");
        this.isPublic = data.getAsBoolean("public");
        this.timestamp = data.getAsTimestamp("time");

        this.plays = data.getAsInt("plays");
        this.likes = data.getAsInt("likes");
        this.dislikes = data.getAsInt("dislikes");
        
        this.tags = tags;
    }

    public String getId() {
        return ID;
    }

    public String getGuildId() {
        return GUILD_ID;
    }

    public String getUserId() {
        return USER_ID;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public long getTimestampSecond() {
        return timestamp.getTime() / 1000;
    }

    public AudioTrack getAsTrack() {
        return track;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public String getPath() {
        return "rsc" + java.io.File.separator + "sounds" + java.io.File.separator + ID + "." + extension;
    }

    public boolean isOpus() {
        return extension.equals("opus");
    }

    public synchronized boolean setName(String name) {
        boolean result = BotDB.updateSound(this.ID, name);
        if (result) {
            this.name = name;
        }
        return result;  
    }

    public synchronized boolean setPublic(boolean isPublic) {
        boolean result = BotDB.updateSound(this.ID, isPublic);
        if (result) {
            this.isPublic = isPublic;
        }
        return result;
    }

    public synchronized boolean setTags(List<Tag> tags) {
        boolean result = BotDB.setSoundTags(this.ID, tags);
        if (result) {
            this.tags = tags;
        }
        return result;
    }

    public boolean increaseUserPlays(String userId, String guildId) {
        return increaseUserPlays(userId, guildId, AudioType.AUDIO);
    }

    public synchronized boolean increaseUserPlays(String userId, String guildId, AudioType source) {
        boolean result = BotDB.updateUserPlays(this.ID, userId, guildId, source.ordinal());
        if (result) plays++;
        return result;
    }

    public void setTrack(AudioTrack track) {
        this.track = track;
        this.durationMs = track == null ? null : track.getDuration();
    }

    public String getFormattedTags() {
        StringBuilder sb = new StringBuilder();
        for (Tag tag : tags) {
            if (!tag.isEmpty()) sb.append(tag.getName()).append(", ");
        }
        return sb.length() > 2 ? sb.toString().substring(0, sb.length() - 2) : "";
    }

    /**
     * 0 total 1 user
     * @param userId
     * @return
     */
    public int[] getPlays(String userId) {
        QueryRecord plays = BotDB.getPlays(this.ID, userId);
        if (plays.emptyValues()) return new int[] {this.plays, 0};

        return new int[] {this.plays, plays.getAsInt("times")};
    }

    public int getGlobalPlays() {
        return this.plays;
    }

    public int retriveGlobalPlays() {
        QueryRecord plays = BotDB.getGlobalPlays(this.ID);
        if (plays.emptyValues()) return 0;
        this.plays = plays.getAsInt("times");

        return this.plays;
    }

    public int[] getLikesDislikes(boolean retrive) {
        return retrive ? retriveLikeDislike() : new int[] {likes, dislikes};
    }

    private int[] retriveLikeDislike() {
        QueryRecord likes = BotDB.getLikeDislike(this.ID);
        if (likes.emptyValues()) return new int[] {0, 0};

        return new int[] {likes.getAsInt("likes"), likes.getAsInt("dislikes")};
    }

    private void updateLikeDislike() {
        int[] likeDislike = retriveLikeDislike();
        this.likes = likeDislike[0];
        this.dislikes = likeDislike[1];
    }

    public int getLikes() {
        return likes;
    }

    public int getDislikes() {
        return dislikes;
    }

    public boolean like(String userId) {
        return like(userId, true);
    }
    
    public boolean dislike(String userId) {
        return dislike(userId, true);
    }
    


    public synchronized boolean like(String userId, boolean like) {
        boolean result = BotDB.setLikeDislike(this.ID, userId, (like ? 1 : 0));
        if (result) {
            updateLikeDislike();
        }
        return result;
    }

    public synchronized boolean dislike(String userId, boolean dislike) {
        boolean result = BotDB.setLikeDislike(this.ID, userId, (dislike ? -1 : 0));
        if (result) {
            updateLikeDislike();
        }
        return result;
    }

    public boolean hasLiked(String userId) {
        return BotDB.hasInterectedSound(this.ID, userId).getAsBoolean("like");
    }

    public boolean hasDisliked(String userId) {
        return BotDB.hasInterectedSound(this.ID, userId).getAsBoolean("dislike");
    }


    @Override
    public String toString() {
        return "ID: " + ID;
    }

}


