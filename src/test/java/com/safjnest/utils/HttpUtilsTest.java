package com.safjnest.utils;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.safjnest.discord.auth.DiscordApi;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class HttpUtilsTest {

    private static final String URL = "https://example.invalid/resource?name=a%2Bb";

    @Test
    public void getPreservesUriHeadersAndRequestTimeout() {
        HttpRequest request = HttpUtils.getRequest(URL, Map.of("Authorization", "Bearer token", "Accept", "application/json"), Duration.ofSeconds(15));

        assertEquals("GET", request.method());
        assertEquals(URL, request.uri().toString());
        assertEquals("Bearer token", request.headers().firstValue("Authorization").orElse(null));
        assertEquals("application/json", request.headers().firstValue("Accept").orElse(null));
        assertEquals(Duration.ofSeconds(15), request.timeout().orElseThrow());
        assertTrue(request.bodyPublisher().isEmpty());
    }

    @Test
    public void formPreservesFieldOrderAndUtf8Escaping() throws java.lang.Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("first key", "a +&/à");
        fields.put("second", "https://example/callback?x=1&y=2");
        HttpRequest request = HttpUtils.formRequest(URL, fields, Duration.ofSeconds(15));

        assertEquals("POST", request.method());
        assertEquals("application/x-www-form-urlencoded", request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("first+key=a+%2B%26%2F%C3%A0&second=https%3A%2F%2Fexample%2Fcallback%3Fx%3D1%26y%3D2", body(request));
    }

    @Test
    public void sendReturnsTheCompleteResponseWithoutClassifyingOrRetryingIt() throws java.lang.Exception {
        HttpResponse<String> response = reply(429, "limited", Map.of("Retry-After", List.of("1.25")));
        AtomicInteger calls = new AtomicInteger();
        HttpUtils.Transport transport = request -> {
            calls.incrementAndGet();
            return response;
        };
        HttpResponse<String> result = HttpUtils.send(transport, HttpUtils.getRequest(URL, Map.of(), Duration.ofSeconds(10)));

        assertSame(response, result);
        assertEquals(429, result.statusCode());
        assertEquals("limited", result.body());
        assertEquals("1.25", result.headers().firstValue("Retry-After").orElse(null));
        assertEquals(1, calls.get());
    }

    @Test
    public void readUrlKeepsUserAgentTimeoutAndTextResult() throws java.lang.Exception {
        String result = HttpUtils.readUrl(URL, request -> {
            assertEquals("GET", request.method());
            assertEquals("Beebot", request.headers().firstValue("User-Agent").orElse(null));
            assertFalse(request.headers().firstValue("Authorization").isPresent());
            assertEquals(Duration.ofSeconds(10), request.timeout().orElseThrow());
            return reply(200, "versions and runes", Map.of());
        });

        assertEquals("versions and runes", result);
    }

    @Test
    public void readUrlKeepsStatusAndIoFailuresAsIoExceptions() {
        IOException status = assertThrows(IOException.class, () -> HttpUtils.readUrl(URL, request -> reply(503, "outage", Map.of())));
        IOException unavailable = new IOException("Unavailable");

        assertEquals("HTTP 503 for " + URL, status.getMessage());
        assertSame(unavailable, assertThrows(IOException.class, () -> HttpUtils.readUrl(URL, request -> { throw unavailable; })));
    }

    @Test
    public void jsonGetKeepsBearerAcceptAndJsonParsing() {
        JSONObject result = HttpUtils.sendGetRequest(URL, "token", request -> {
            assertEquals("Bearer token", request.headers().firstValue("Authorization").orElse(null));
            assertEquals("application/json", request.headers().firstValue("Accept").orElse(null));
            assertEquals(Duration.ofSeconds(10), request.timeout().orElseThrow());
            return reply(200, "{\"name\":\"Artist\"}", Map.of());
        });

        assertEquals("Artist", result.getString("name"));
        assertThrows(JSONException.class, () -> HttpUtils.sendGetRequest(URL, "token", request -> reply(200, "invalid JSON", Map.of())));
    }

    @Test
    public void jsonGetKeepsNullOnStatusAndNetworkFailures() {
        PrintStream previous = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setErr(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertNull(HttpUtils.sendGetRequest(URL, "token", request -> reply(503, "Unavailable", Map.of())));
            assertNull(HttpUtils.sendGetRequest(URL, "token", request -> { throw new IOException("Disconnected"); }));
        } finally {
            System.setErr(previous);
        }

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("HTTP error: 503"));
    }

    @Test
    public void interruptedSendRetainsTheFlagAndException() {
        InterruptedException interrupted = new InterruptedException("Cancelled");

        try {
            assertSame(interrupted, assertThrows(InterruptedException.class, () -> HttpUtils.send(request -> { throw interrupted; },
                HttpUtils.getRequest(URL, Map.of(), Duration.ofSeconds(10)))));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void interruptedReadUrlStillWrapsIoAndRetainsTheFlag() {
        try {
            IOException failure = assertThrows(IOException.class, () -> HttpUtils.readUrl(URL, request -> { throw new InterruptedException(); }));
            assertTrue(failure.getCause() instanceof InterruptedException);
            assertEquals("Interrupted while reading " + URL, failure.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void oauthExchangeKeepsItsFieldsEndpointAndTimeout() throws java.lang.Exception {
        DiscordApi api = new DiscordApi(request -> {
            assertEquals("https://discord.com/api/oauth2/token", request.uri().toString());
            assertEquals(Duration.ofSeconds(15), request.timeout().orElseThrow());
            assertEquals("application/x-www-form-urlencoded", request.headers().firstValue("Content-Type").orElse(null));

            try {
                assertEquals("client_id=app%26&client_secret=secret+%2B&grant_type=authorization_code&code=code%2F&redirect_uri=https%3A%2F%2Fexample%2Fcallback", body(request));
            } catch (java.lang.Exception exception) {
                throw new IOException(exception);
            }

            return reply(200, "{\"access_token\":\"token\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"identify guilds\"}", Map.of());
        }, () -> null);

        assertEquals("token", api.exchange("app&", "secret +", "code/", "https://example/callback").accessToken());
    }

    /* -------------------------------------------------------------------------- */

    private static String body(HttpRequest request) throws java.lang.Exception {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        CompletableFuture<String> completed = new CompletableFuture<>();

        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                result.writeBytes(bytes);
            }

            public void onError(Throwable exception) {
                completed.completeExceptionally(exception);
            }

            public void onComplete() {
                completed.complete(result.toString(StandardCharsets.UTF_8));
            }
        });

        return completed.get(5, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> reply(int status, String body, Map<String, List<String>> headers) {
        return (HttpResponse<String>) Proxy.newProxyInstance(HttpResponse.class.getClassLoader(), new Class<?>[] {HttpResponse.class},
            (object, method, args) -> switch (method.getName()) {
                case "statusCode" -> status;
                case "body" -> body;
                case "headers" -> HttpHeaders.of(headers, (name, value) -> true);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
