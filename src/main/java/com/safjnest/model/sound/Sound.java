package com.safjnest.model.sound;

import java.sql.Timestamp;
import java.util.List;

import com.safjnest.core.audio.types.AudioType;
import com.safjnest.sql.BotDB;
import com.safjnest.sql.QueryRecord;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class Sound {
    private final String ID;
    private final String GUILD_ID;
    private final String USER_ID;

    private String name;
    private String extension;
    private boolean isPublic;
    private Timestamp timestamp;

    private int plays;
    private int likes;
    private int dislikes;

    private AudioTrack track;

    private List<Tag> tags;

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

    public boolean setName(String name) {
        boolean result = BotDB.updateSound(this.ID, name);
        if (result) {
            this.name = name;
        }
        return result;  
    }

    public boolean setPublic(boolean isPublic) {
        boolean result = BotDB.updateSound(this.ID, isPublic);
        if (result) {
            this.isPublic = isPublic;
        }
        return result;
    }

    public boolean setTags(List<Tag> tags) {
        boolean result = BotDB.setSoundTags(this.ID, tags);
        if (result) {
            this.tags = tags;
        }
        return result;
    }

    public boolean increaseUserPlays(String userId) {
        return increaseUserPlays(userId, AudioType.AUDIO);
    }

    public boolean increaseUserPlays(String userId, AudioType source) {
        boolean result = BotDB.updateUserPlays(this.ID, userId, source.ordinal());
        if (result) plays++;
        return result;
    }

    public void setTrack(AudioTrack track) {
        this.track = track;
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
    


    public boolean like(String userId, boolean like) {
        boolean result = BotDB.setLikeDislike(this.ID, userId, (like ? 1 : 0));
        if (result) {
            updateLikeDislike();
        }
        return result;
    }

    public boolean dislike(String userId, boolean dislike) {
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


