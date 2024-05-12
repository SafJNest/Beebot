package com.safjnest.Utilities.Audio;

public class TrackData {
    private AudioType type;
    private String thumbnailUrl;

    public TrackData(AudioType type) {
        this.type = type;
    }



    public AudioType getType() {
        return type;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setType(AudioType type) {
        this.type = type;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
    

    
    public boolean isQueueable() {
        return type == AudioType.AUDIO;
    }
    
}
