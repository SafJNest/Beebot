package com.safjnest.model.spotify;

import java.util.Objects;

import org.json.JSONObject;

import com.safjnest.util.HttpUtils;
import com.safjnest.util.spotify.SpotifyHandler;
import com.safjnest.util.spotify.SpotifyTokenManager;

import lombok.Data;

@Data
public class SpotifyTrack {
    public static final String TRACK_URL = SpotifyHandler.SPOTIFY_API_BASE_URL + "/tracks/";

    private String name;
    private String artist;
    private String album;
    private String id;
    private int playCount; // Optional field to store play count
    private String imageUrl;

    public SpotifyTrack(String id) {
        this.id = id;
        this.name = null;
        this.artist = null;
        this.album = null;
        this.playCount = 0;
        this.imageUrl = null;
    }

    public SpotifyTrack(String name, String artist, String album, String id) {
        this.name = name;
        this.artist = artist;
        this.album = album;
        this.id = id;
        this.playCount = 0;
    }

    public SpotifyTrack(String name, String artist, String album, String id, int playCount, String imageUrl) {
        this.name = name;
        this.artist = artist;
        this.album = album;
        this.id = id;
        this.playCount = playCount;
        this.imageUrl = imageUrl;
    }

    private String retrieveImage() {
        String url = TRACK_URL + id;
        JSONObject response = HttpUtils.sendGetRequest(url, SpotifyTokenManager.getAccessToken());

        try {
            return response
                    .getJSONObject("album")
                    .getJSONArray("images")
                    .getJSONObject(0)
                    .getString("url");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getImageUrl() {
        if ((imageUrl == null || imageUrl.isEmpty()) && id != null) {
            imageUrl = retrieveImage();
        }

        return (imageUrl == null || imageUrl.isEmpty()) ? SpotifyHandler.DEFAULT_IMAGE : imageUrl;
    }

    @Override
    public String toString() {
        return String.format("%s - %s (%s) | %s", artist, name, album, id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpotifyTrack)) return false;
        SpotifyTrack that = (SpotifyTrack) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
