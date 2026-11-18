package com.safjnest.utils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

import org.json.JSONObject;

public final class HttpUtils {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final Transport transport = createTransport(Duration.ofMillis(CONNECT_TIMEOUT_MS), HttpResponse.BodyHandlers.ofString());

    @FunctionalInterface
    public interface Transport {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private HttpUtils() {
    }

    public static Transport createTransport(Duration connectTimeout, HttpResponse.BodyHandler<String> bodyHandler) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

        return request -> client.send(request, bodyHandler);
    }

    public static HttpRequest getRequest(String url, Map<String, String> headers, Duration timeout) {
        return request(url, headers, timeout).GET().build();
    }

    public static HttpRequest formRequest(String url, Map<String, String> fields, Duration timeout) {
        StringJoiner body = new StringJoiner("&");

        for (Map.Entry<String, String> field : fields.entrySet())
            body.add(encode(field.getKey()) + "=" + encode(field.getValue()));

        return request(url, Map.of("Content-Type", "application/x-www-form-urlencoded"), timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
            .build();
    }

    public static HttpResponse<String> send(Transport transport, HttpRequest request) throws IOException, InterruptedException {
        try {
            return transport.send(request);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw exception;
        }
    }

    public static String readUrl(String url) throws IOException {
        return readUrl(url, transport);
    }

    public static String readUrl(String url, Transport transport) throws IOException {
        HttpRequest request = getRequest(url, Map.of("User-Agent", "Beebot"), Duration.ofMillis(READ_TIMEOUT_MS));

        try {
            HttpResponse<String> response = send(transport, request);
            int status = response.statusCode();
            if (status < 200 || status >= 300)
                throw new IOException("HTTP " + status + " for " + url);

            return response.body();
        } catch (InterruptedException exception) {
            throw new IOException("Interrupted while reading " + url, exception);
        }
    }

    public static JSONObject sendGetRequest(String url, String bearerToken) {
        return sendGetRequest(url, bearerToken, transport);
    }

    public static JSONObject sendGetRequest(String url, String bearerToken, Transport transport) {
        HttpRequest request = getRequest(url, Map.of("Accept", "application/json", "Authorization", "Bearer " + bearerToken),
            Duration.ofMillis(READ_TIMEOUT_MS));

        try {
            HttpResponse<String> response = send(transport, request);
            if (response.statusCode() >= 200 && response.statusCode() < 300)
                return new JSONObject(response.body());

            System.err.println("HTTP error: " + response.statusCode());
            System.err.println(response.body());
        } catch (IOException | InterruptedException exception) {
            exception.printStackTrace();
        }

        return null;
    }

    /* -------------------------------------------------------------------------- */

    private static HttpRequest.Builder request(String url, Map<String, String> headers, Duration timeout) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        headers.forEach(request::header);

        return request;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
