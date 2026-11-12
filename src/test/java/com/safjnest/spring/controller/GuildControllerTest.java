package com.safjnest.spring.controller;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordAuthService;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.auth.DiscordApi.Server;
import com.safjnest.guild.GuildService;
import com.safjnest.model.guild.GuildSettings;
import com.safjnest.spring.config.BeebotAuthFilter;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class GuildControllerTest {

    @Test
    public void checksAuthorizationOnEveryGetAndPostBeforeAccessingSettings() throws Exception {
        AtomicInteger accesses = new AtomicInteger();
        AtomicInteger checks = new AtomicInteger();
        AtomicReference<List<Server>> servers = new AtomicReference<>(List.of(new Server("1", "Guild", null, true)));
        GuildService service = new GuildService(null, null, null, null) {
            @Override
            public GuildSettings getGuild(String guildId) {
                accesses.incrementAndGet();
                return new GuildSettings(guildId, "Guild", null, List.of(), List.of(), Map.of());
            }
            @Override
            public Map<String, Object> updateSettings(String guildId, JsonNode body) {
                accesses.incrementAndGet();
                return Map.of();
            }
        };
        GuildController controller = new GuildController(service, (context, guildId) -> {
            checks.incrementAndGet();
            for (Server server : servers.get()) if (server.id().equals(guildId)) return;
            throw new AuthException(HttpStatus.FORBIDDEN, "guild_forbidden", "Guild access denied");
        });
        MockMvc mvc = mvc(controller);
        var response = mvc.perform(get("/api/beebot/guilds/1").servletPath("/api/beebot/guilds/1"))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getContentAsString().contains("\"id\":\"1\""));
        mvc.perform(post("/api/beebot/guilds/1/settings").servletPath("/api/beebot/guilds/1/settings")
            .contentType(MediaType.APPLICATION_JSON).content("{\"leave\":{}}"))
            .andExpect(status().isOk());
        servers.set(List.of());
        mvc.perform(get("/api/beebot/guilds/1").servletPath("/api/beebot/guilds/1"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/beebot/guilds/1/settings").servletPath("/api/beebot/guilds/1/settings")
            .contentType(MediaType.APPLICATION_JSON).content("{\"leave\":{}}"))
            .andExpect(status().isForbidden());
        assertEquals(4, checks.get());
        assertEquals(2, accesses.get());
    }

    @Test
    public void rejectsMalformedJsonAndPathWithoutEchoingRequestContents() throws Exception {
        GuildController controller = new GuildController(null, (context, guildId) -> { throw new AuthException(HttpStatus.FORBIDDEN, "guild_forbidden", "Guild access denied"); });
        var response = mvc(controller).perform(post("/api/beebot/guilds/1/settings")
            .servletPath("/api/beebot/guilds/1/settings").contentType(MediaType.APPLICATION_JSON)
            .content("{\"do-not-echo\":")).andExpect(status().isBadRequest()).andReturn().getResponse();
        assertFalse(response.getContentAsString().contains("do-not-echo"));
        assertTrue(response.getContentAsString().contains("invalid_request"));
        mvc(controller).perform(get("/api/beebot/guilds/invalid").servletPath("/api/beebot/guilds/invalid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    public void propagatesDiscordFailureBeforeReadingOrWritingGuild() throws Exception {
        GuildController controller = new GuildController(null, (context, guildId) -> {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_unavailable", "Discord is unavailable");
        });
        mvc(controller).perform(get("/api/beebot/guilds/1").servletPath("/api/beebot/guilds/1"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    public void guildRoutesRequireApplicationAndUserCredentials() throws Exception {
        GuildController controller = new GuildController(null, (context, guildId) -> { throw new AssertionError("Must not authorize"); });
        var mvc = MockMvcBuilders.standaloneSetup(controller).addFilters(new BeebotAuthFilter((key, bearer, required) -> {
            assertTrue(required);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "invalid_session", "Missing credentials");
        })).build();
        mvc.perform(get("/api/beebot/guilds/1").servletPath("/api/beebot/guilds/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/beebot/guilds/1/settings").servletPath("/api/beebot/guilds/1/settings")
            .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    private MockMvc mvc(GuildController controller) {
        AuthContext context = new AuthContext(null, "hash", "encrypted", Long.MAX_VALUE,
            new AuthUser("2", "admin", null, null));
        return MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new AuthExceptionHandler(), new AuthRequestExceptionHandler())
            .addFilters(new BeebotAuthFilter((key, bearer, required) -> context)).build();
    }
}
