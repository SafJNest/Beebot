package com.safjnest.model.spotify;

public class SpotifyTrackStreaming {
    private String ts;
    private long msPlayed;
    private SpotifyTrack track;

    public SpotifyTrackStreaming(String ts, long msPlayed, String name, String artistName, String albumName, String URI) {
        this.ts = ts;
        this.msPlayed = msPlayed;
        this.track = new SpotifyTrack(name, artistName, albumName, URI);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", ts, track.toString());
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

    public SpotifyTrack getTrack() {
        return track;
    }

    public void setTrack(SpotifyTrack track) {
        this.track = track;
    }

    public String toValues(String userId) {
        String safeTs = ts.replace("T", " ").replace("Z", "");
        String name = track.getName().replace("'", "''");
        String artist = track.getArtist().replace("'", "''");
        String album = track.getAlbum().replace("'", "''");
        String uri = track.getURI().replace("'", "''");
        String safeUserId = userId.replace("'", "''");

        return String.format("('%s', '%s', %d, '%s', '%s', '%s', '%s')",
                safeTs, safeUserId, msPlayed, name, artist, album, uri);
    }

    public String getArtistName() {
        return track.getArtist();
    }

    public String getAlbumName() {
        return track.getAlbum();
    }

    public String getSpotifyTrackUri() {
        return track.getURI();
    }

}
