package com.safjnest.model.spotify;

import lombok.Data;

@Data
public class SpotifyArtist {
    private String name;
    private String imageUrl;
    private String randomTrackUri;
    private int playCount;

    public SpotifyArtist(String name, int playCount, String trackUri) {
        this.name = name;
        this.randomTrackUri = trackUri;
        this.playCount = playCount;
        this.imageUrl = null;
    }
}
