package com.safjnest.model.spotify;

import org.json.JSONObject;

import com.safjnest.util.HttpUtils;
import com.safjnest.util.spotify.SpotifyHandler;
import com.safjnest.util.spotify.SpotifyTokenManager;

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

    public SpotifyArtist(String name, String imageUrl) {
        this.name = name;
        this.randomTrackUri = null;
        this.playCount = 0;
        this.imageUrl = imageUrl;
    }

    private String retrieveArtistImageFromTrack() {
        String trackUrl = SpotifyTrack.TRACK_URL + randomTrackUri;
        JSONObject trackResponse = HttpUtils.sendGetRequest(trackUrl, SpotifyTokenManager.getAccessToken());

        try {
            String artistUrl = trackResponse
                    .getJSONArray("artists")
                    .getJSONObject(0)
                    .getString("href");

            JSONObject artistResponse = HttpUtils.sendGetRequest(artistUrl, SpotifyTokenManager.getAccessToken());

            return artistResponse
                    .getJSONArray("images")
                    .getJSONObject(0)
                    .getString("url");

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getImageUrl() {
        if ((imageUrl == null || imageUrl.isEmpty()) && randomTrackUri != null) {
            imageUrl = retrieveArtistImageFromTrack();
        }

        return (imageUrl == null || imageUrl.isEmpty()) ? SpotifyHandler.DEFAULT_IMAGE : imageUrl;
    }

}
