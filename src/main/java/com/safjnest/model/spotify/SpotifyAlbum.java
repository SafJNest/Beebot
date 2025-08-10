package com.safjnest.model.spotify;

import java.util.List;

import com.safjnest.util.spotify.SpotifyHandler;

import lombok.Data;

@Data
public class SpotifyAlbum {
    private String name;
    private String artist;
    private List<SpotifyTrack> tracks;
    private String imageUrl;
    private int playCount;


    public SpotifyAlbum(String name, String artist, List<SpotifyTrack> tracks) {
        this.name = name;
        this.artist = artist;
        this.tracks = tracks;
        this.imageUrl = null;
        this.playCount = 0;
    }

    public SpotifyAlbum(String name, String artist, List<SpotifyTrack> tracks, int playCount) {
        this.name = name;
        this.artist = artist;
        this.tracks = tracks;
        this.imageUrl = null;
        this.playCount = playCount;
    }

    public String getImageUrl() {
        if ((imageUrl == null || imageUrl.isEmpty()) && !tracks.isEmpty()) {
            imageUrl = tracks.get(0).getImageUrl();
        }

        return (imageUrl == null || imageUrl.isEmpty()) ? SpotifyHandler.DEFAULT_IMAGE : imageUrl;
    }
}
