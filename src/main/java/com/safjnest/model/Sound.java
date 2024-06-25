package com.safjnest.model;

import java.sql.Timestamp;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.ResultRow;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class Sound {
    private final String ID;
    private final String GUILD_ID;
    private final String USER_ID;

    private String name;
    private String extension;
    private boolean isPublic;
    private Timestamp timestamp;

    private AudioTrack track;

    private Tag[] tags;

    public class Tag {
        private final int ID;
        private String name;

        public Tag() {
            this.ID = 0;
            this.name = "";
        }

        public Tag(int ID, String name) {
            this.ID = ID;
            this.name = name;
        }


        public int getId() {
            return ID;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "ID: " + ID + "\n" +
                "Name: " + name;
        }
        
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
    
    public Sound(String ID, String GUILD_ID, String USER_ID, String name, String extension, boolean isPublic, Timestamp timestamp, Tag[] tags) {
        this.ID = ID;
        this.GUILD_ID = GUILD_ID;
        this.USER_ID = USER_ID;
        this.name = name;
        this.extension = extension;
        this.isPublic = isPublic;
        this.timestamp = timestamp;
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

    public Tag[] getTags() {
        return tags;
    }

    public String getPath() {
        return "rsc" + java.io.File.separator + "SoundBoard" + java.io.File.separator + ID + "." + extension;
    }

    public boolean isOpus() {
        return extension.equals("opus");
    }

    public boolean setName(String name) {
        boolean result = DatabaseHandler.updateSound(this.ID, name);
        if (result) {
            this.name = name;
        }
        return result;  
    }

    public boolean setPublic(boolean isPublic) {
        boolean result = DatabaseHandler.updateSound(this.ID, isPublic);
        if (result) {
            this.isPublic = isPublic;
        }
        return result;
    }

    public boolean setTags(Tag[] tags) {
        boolean result = DatabaseHandler.setSoundTags(this.ID, tags);
        if (result) {
            this.tags = tags;
        }
        return result;
    }

    public boolean increaseUserPlays(String userId) {
        return DatabaseHandler.updateUserPlays(this.ID, userId);
    }

    public void setTrack(AudioTrack track) {
        this.track = track;
    }

    /**
     * 0 total 1 user
     * @param userId
     * @return
     */
    public int[] getPlays(String userId) {
        ResultRow plays = DatabaseHandler.getPlays(this.ID, userId);
        if (plays.emptyValues()) return new int[] {0, 0};

        return new int[] {plays.getAsInt("totalTimes"), plays.getAsInt("timesByUser")};
    }


    public int[] getLikesDislikes() {
        ResultRow likes = DatabaseHandler.getLikeDislike(this.ID);
        if (likes.emptyValues()) return new int[] {0, 0};

        return new int[] {likes.getAsInt("likes"), likes.getAsInt("dislikes")};
    }

    public boolean like(String userId) {
        return like(userId, true);
    }
    
    public boolean dislike(String userId) {
        return dislike(userId, true);
    }
    


    public boolean like(String userId, boolean like) {
        return DatabaseHandler.setLikeDislike(this.ID, userId, like, false);
    }

    public boolean dislike(String userId, boolean dislike) {
        return DatabaseHandler.setLikeDislike(this.ID, userId, false, dislike);
    }

    public boolean hasLiked(String userId) {
        return DatabaseHandler.hasInterectedSound(this.ID, userId).getAsBoolean("like");
    }

    public boolean hasDisliked(String userId) {
        return DatabaseHandler.hasInterectedSound(this.ID, userId).getAsBoolean("dislike");
    }


    @Override
    public String toString() {
        return "ID: " + ID + "\n" +
            "Guild ID: " + GUILD_ID + "\n" +
            "User ID: " + USER_ID + "\n" +
            "Name: " + name + "\n" +
            "Extension: " + extension + "\n" +
            "Public: " + isPublic + "\n" +
            "Timestamp: " + timestamp + "\n" +
            "Tags: " + tags;
    }

}


