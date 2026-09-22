package com.safjnest.utils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.json.JSONObject;

public class HttpUtils {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
        .build();

    public static String readUrl(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "Beebot")
            .timeout(Duration.ofMillis(READ_TIMEOUT_MS))
            .GET()
            .build();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " for " + url);
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading " + url, exception);
        }
    }

    public static JSONObject sendGetRequest(String url, String bearerToken) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + bearerToken)
            .timeout(Duration.ofMillis(READ_TIMEOUT_MS))
            .GET()
            .build();

        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new JSONObject(response.body());
            }
            System.err.println("HTTP error: " + response.statusCode());
            System.err.println(response.body());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            exception.printStackTrace();
        }
        return null;
    }
}
