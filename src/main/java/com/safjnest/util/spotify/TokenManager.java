package com.safjnest.util.spotify;

import org.springframework.web.client.RestTemplate;

import com.safjnest.util.SettingsLoader;

import org.springframework.http.*;
import java.util.Base64;

public class TokenManager {
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String CLIENT_ID = SettingsLoader.getSettings().getJsonSettings().getSpotifyApi().getClientId(); // Replace with your client ID
    private static final String CLIENT_SECRET = SettingsLoader.getSettings().getJsonSettings().getSpotifyApi().getClientSecret(); // Replace with your client secret

    private static String accessToken = null;
    private static long tokenExpirationTime = 0;

    public static String getAccessToken() {
        if (System.currentTimeMillis() >= tokenExpirationTime) {
            refreshAccessToken();
        }
        return accessToken;
    }

    private static void refreshAccessToken() {
        RestTemplate restTemplate = new RestTemplate();

        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String body = "grant_type=client_credentials";
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.exchange(TOKEN_URL, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode() == HttpStatus.OK) {
            String responseBody = response.getBody();
            String newAccessToken = responseBody.split("\"access_token\":\"")[1].split("\"")[0];
            int expiresIn = Integer.parseInt(responseBody.split("\"expires_in\":")[1].split("}")[0]);

            accessToken = newAccessToken;
            tokenExpirationTime = System.currentTimeMillis() + (expiresIn * 1000);
        } else {
            throw new RuntimeException("Failed to obtain access token");
        }
    }
}