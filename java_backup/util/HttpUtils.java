package com.safjnest.util;

import java.net.URI;
import java.net.http.*;
import java.io.IOException;
import org.json.JSONObject;

public class HttpUtils {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static JSONObject sendGetRequest(String url, String bearerToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new JSONObject(response.body());
            } else {
                System.err.println("HTTP error: " + response.statusCode());
                System.err.println(response.body());
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }
}