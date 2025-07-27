package com.safjnest.model.spotify;

import java.util.List;

import lombok.Data;

@Data
public class SpotifyAlbum {
    private String name;
    private String artist;
    private List<SpotifyTrack> tracks;
    private String coverUrl;
    private int playCount;


    public SpotifyAlbum(String name, String artist, List<SpotifyTrack> tracks) {
        this.name = name;
        this.artist = artist;
        this.tracks = tracks;
        this.coverUrl = null;
        this.playCount = 0;
    }

    public SpotifyAlbum(String name, String artist, List<SpotifyTrack> tracks, int playCount) {
        this.name = name;
        this.artist = artist;
        this.tracks = tracks;
        this.coverUrl = null;
        this.playCount = playCount;
    }
}
