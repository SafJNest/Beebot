package com.safjnest.model.spotify;

public class spotifyTrackStreaming {
    private String ts;
    private long msPlayed;
    private String name;
    private String artistName;
    private String albumName;
    private String URI;

    public spotifyTrackStreaming(String ts, long msPlayed, String name, String artistName, String albumName, String URI) {
        this.ts = ts;
        this.msPlayed = msPlayed;
        this.name = name;
        this.artistName = artistName;
        this.albumName = albumName;
        this.URI = URI;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s (%s) | %s", ts, artistName,
            name, albumName, URI);
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public long getMsPlayed() {
        return msPlayed;
    }

    public void setMsPlayed(long msPlayed) {
        this.msPlayed = msPlayed;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getURI() {
        return URI;
    }

    public void setURI(String uRI) {
        URI = uRI;
    }
}
