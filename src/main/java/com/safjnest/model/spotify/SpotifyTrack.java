package com.safjnest.model.spotify;

import java.util.Objects;

public class SpotifyTrack {
    private String name;
    private String artist;
    private String album;
    private String URI;

    public SpotifyTrack(String name, String artist, String album, String URI) {
        this.name = name;
        this.artist = artist;
        this.album = album;
        this.URI = URI;
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%s) | %s", artist, name, album, URI);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getURI() {
        return URI;
    }

    public void setURI(String uRI) {
        URI = uRI;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpotifyTrack)) return false;
        SpotifyTrack that = (SpotifyTrack) o;
        return Objects.equals(URI, that.URI);
    }

    @Override
    public int hashCode() {
        return Objects.hash(URI);
    }
}
