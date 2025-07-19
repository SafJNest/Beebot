package com.safjnest.model.spotify;

import java.util.List;

import lombok.Data;

@Data
public class SpotifyAlbum {
    private String name;
    private String artist;
    private List<SpotifyTrack> tracks;

    public SpotifyAlbum(String name, String artist, List<SpotifyTrack> tracks) {
        this.name = name;
        this.artist = artist;
        this.tracks = tracks;
    }
}
