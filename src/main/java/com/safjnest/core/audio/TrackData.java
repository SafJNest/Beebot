package com.safjnest.core.audio;

import com.safjnest.core.audio.types.AudioType;

public class TrackData {
    private AudioType type;
    private String thumbnailUrl;
    private int playlistSongId;

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

    public void setPlaylistSongId(int playlistSongId) {
        this.playlistSongId = playlistSongId;
    }

    public int getPlaylistSongId() {
        return playlistSongId;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
    

    
    public boolean isQueueable() {
        return type == AudioType.AUDIO;
    }
    
}
