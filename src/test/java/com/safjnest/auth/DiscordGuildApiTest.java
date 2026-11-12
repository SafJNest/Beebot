package com.safjnest.auth;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.lang.reflect.Proxy;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.guild.*;
import com.safjnest.model.guild.GuildSettings;
import com.safjnest.spring.config.BeebotAuthFilter;
import com.safjnest.spring.controller.*;
import com.safjnest.utils.CryptoUtils;
import org.junit.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class DiscordGuildApiTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String GUILDS = "[{\"id\":\"1\",\"name\":\"owner\",\"owner\":true,\"permissions\":\"0\"},"
        + "{\"id\":\"2\",\"name\":\"admin\",\"owner\":false,\"permissions\":\"8\"},"
        + "{\"id\":\"3\",\"name\":\"manager\",\"owner\":false,\"permissions\":\"32\"},"
        + "{\"id\":\"4\",\"name\":\"member\",\"owner\":false,\"permissions\":\"0\"}]";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    public void meAndBothListRoutesShareOneFetchAndPermissionRules() throws Exception {
        var fake = new Transport();
        var fixture = fixture(fake);
        var response = fixture.mvc.perform(get("/api/beebot/me")).andExpect(status().isOk()).andReturn().getResponse();
        var body = json.readTree(response.getContentAsString());
        assertEquals(3, body.size());
        assertTrue(body.get("greets").isObject());
        assertEquals("42", body.get("user").get("id").asText());
        assertEquals(3, body.get("servers").size());
        for (String route : List.of("/api/beebot/guilds", "/api/beebot/servers")) {
            var list = fixture.mvc.perform(get(route)).andExpect(status().isOk()).andReturn().getResponse();
            assertEquals(body.get("servers"), json.readTree(list.getContentAsString()));
        }
        for (String guild : List.of("1", "2", "3")) fixture.mvc.perform(get("/api/beebot/guilds/" + guild)).andExpect(status().isOk());
        fixture.mvc.perform(get("/api/beebot/guilds/4")).andExpect(status().isForbidden());
        assertEquals(1, fake.calls.get());
    }

    @Test
    public void concurrentMeAndGuildRequestShareOneFlight() throws Exception {
        var fake = new Transport(); fake.entered = new CountDownLatch(1); fake.release = new CountDownLatch(1);
        var fixture = fixture(fake);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var me = executor.submit(() -> fixture.mvc.perform(get("/api/beebot/me")).andExpect(status().isOk()));
            assertTrue(fake.entered.await(2, TimeUnit.SECONDS));
            var guild = executor.submit(() -> fixture.mvc.perform(get("/api/beebot/guilds/1")).andExpect(status().isOk()));
            fake.release.countDown();
            me.get(3, TimeUnit.SECONDS); guild.get(3, TimeUnit.SECONDS);
            assertEquals(1, fake.calls.get());
        }
    }

    @Test
    public void rateLimitHasRetryMetadataAndErrorsAreNotCached() throws Exception {
        var fake = new Transport(); fake.status = 429; fake.body = "{\"retry_after\":1.25}";
        var fixture = fixture(fake);
        for (int i = 0; i < 2; i++) {
            var response = fixture.mvc.perform(get("/api/beebot/me")).andExpect(status().isServiceUnavailable()).andReturn().getResponse();
            var body = json.readTree(response.getContentAsString());
            assertEquals("discord_rate_limited", body.get("code").asText());
            assertEquals(1.25, body.get("retryAfter").asDouble(), 0);
            assertEquals("2", response.getHeader("Retry-After"));
            assertEquals(3, body.size());
        }
        assertEquals(2, fake.calls.get());
        fake.status = 200; fake.body = GUILDS;
        fixture.mvc.perform(get("/api/beebot/me")).andExpect(status().isOk());
        assertEquals(3, fake.calls.get());
    }

    @Test
    public void expiredTokensAndDiscordFailuresAreDistinct() throws Exception {
        for (int code : new int[]{401, 403, 500}) {
            var fake = new Transport(); fake.status = code;
            var fixture = fixture(fake);
            var response = fixture.mvc.perform(get("/api/beebot/me")).andReturn().getResponse();
            assertEquals(code == 500 ? 503 : 401, response.getStatus());
            assertEquals(code == 500 ? "discord_unavailable" : "discord_token_expired", json.readTree(response.getContentAsString()).get("code").asText());
        }
        var fake = new Transport(); fake.networkFailure = true;
        fixture(fake).mvc.perform(get("/api/beebot/me")).andExpect(status().isServiceUnavailable());
    }

    @Test
    public void malformedDiscordResponsesAreNotCached() throws Exception {
        for (String invalid : List.of("not-json", "null", "[{}]")) {
            var fake = new Transport(); fake.body = invalid;
            var fixture = fixture(fake);
            fixture.mvc.perform(get("/api/beebot/me")).andExpect(status().isBadGateway());
            fake.body = GUILDS;
            fixture.mvc.perform(get("/api/beebot/me")).andExpect(status().isOk());
            assertEquals(2, fake.calls.get());
        }
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path).servletPath(path);
    }

    private Fixture fixture(Transport fake) {
        var context = new AuthContext(new ApiKey("app", "dashboard", ""), "session", CryptoUtils.encrypt("discord-secret", CryptoUtils.parseEncryptionKey(KEY)),
            Long.MAX_VALUE, new AuthUser("42", "tester", null, null));
        var directory = new DiscordGuildService(new DiscordApi(fake, () -> null), DiscordGuildCache.memory(), KEY);
        var settings = new GuildService(null, null, null, null) {
            @Override public GuildSettings getGuild(String id) { return new GuildSettings(id, "Guild", null, List.of(), List.of(), Map.of()); }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DiscordAuthController(new DiscordAuthService(), directory, contextValue -> Map.of()),
            new GuildController(settings, directory::requireAdministrator)).setControllerAdvice(new AuthExceptionHandler())
            .addFilters(new BeebotAuthFilter((key, bearer, required) -> context)).build();
        return new Fixture(mvc);
    }
    private record Fixture(MockMvc mvc) {}

    private static final class Transport implements DiscordApi.Transport {
        final AtomicInteger calls = new AtomicInteger();
        volatile int status = 200;
        volatile String body = GUILDS;
        volatile boolean networkFailure;
        CountDownLatch entered, release;
        @SuppressWarnings("unchecked")
        public HttpResponse<String> send(HttpRequest request) throws Exception {
            calls.incrementAndGet();
            assertEquals("/api/v10/users/@me/guilds", request.uri().getPath());
            if (entered != null) { entered.countDown(); if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Timed out"); }
            if (networkFailure) throw new java.io.IOException("No network");
            return (HttpResponse<String>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpResponse.class}, (proxy, method, args) -> switch (method.getName()) {
                case "statusCode" -> status;
                case "body" -> body;
                case "headers" -> HttpHeaders.of(Map.of("X-RateLimit-Bucket", List.of("test-bucket"), "X-RateLimit-Remaining", List.of("0")), (name, value) -> true);
                default -> null;
            });
        }
    }
}
