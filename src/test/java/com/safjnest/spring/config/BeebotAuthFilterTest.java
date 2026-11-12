package com.safjnest.spring.config;

import com.safjnest.auth.ApiKey;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.safjnest.auth.AuthException;
import com.safjnest.sql.database.AuthDB;
import com.safjnest.auth.DiscordAuthService;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.safjnest.spring.controller.DiscordAuthController;
import com.safjnest.spring.controller.AuthExceptionHandler;
import com.safjnest.spring.controller.AuthRequestExceptionHandler;
import org.springframework.http.MediaType;

public class BeebotAuthFilterTest {
    @Test
    public void protectsNewRoutesAndLeavesLolPublic() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        BeebotAuthFilter filter = new BeebotAuthFilter((key, bearer, sessionRequired) -> {
            invoked.set(true);
            assertTrue(sessionRequired);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "invalid_api_key", "Application credentials are missing or invalid");
        });
        MockHttpServletResponse protectedResponse = execute(filter, "GET", "/api/beebot/new-route", "/api/beebot/new-route");
        assertTrue(invoked.get());
        assertEquals(401, protectedResponse.getStatus());
        assertTrue(protectedResponse.getContentAsString().contains("invalid_api_key"));
        invoked.set(false);
        assertEquals(200, execute(filter, "GET", "/api/lol/status", "/api/lol/status").getStatus());
        assertFalse(invoked.get());
    }

    @Test
    public void onlyExactPostLoginEndpointsArePreSession() throws Exception {
        AtomicBoolean session = new AtomicBoolean(true);
        BeebotAuthFilter filter = new BeebotAuthFilter((key, bearer, required) -> { session.set(required); return null; });
        execute(filter, "POST", "/api/beebot/auth/session", "/api/beebot/auth/session");
        assertFalse(session.get());
        execute(filter, "POST", "/api/beebot/auth/discord/authorize", "/api/beebot/auth/discord/authorize");
        assertFalse(session.get());
        execute(filter, "GET", "/api/beebot/auth/session", "/api/beebot/auth/session");
        assertTrue(session.get());
        execute(filter, "GET", "/api/beebot", "/api/beebot");
        assertTrue(session.get());
        assertEquals(400, execute(filter, "POST", "/api/beebot/auth/session", "/api/beebot/auth/session;x=1").getStatus());
        assertEquals(400, execute(filter, "POST", "/api/beebot/auth/session", "/api/beebot/auth/%73ession").getStatus());
    }

    @Test
    public void mockMvcPassesVerifiedContextToNewController() throws Exception {
        var client = new ApiKey("client", "dashboard", "https://example.com/callback");
        var context = new AuthContext(client, "hash", "encrypted", 1, null);
        var filter = new BeebotAuthFilter((key, bearer, required) -> {
            if (!"valid".equals(key) || !required) throw new AuthException(HttpStatus.UNAUTHORIZED, "invalid_api_key", "Invalid key");
            return context;
        });
        var mvc = MockMvcBuilders.standaloneSetup(new NewController()).addFilters(filter).build();
        mvc.perform(get("/api/beebot/new-controller").servletPath("/api/beebot/new-controller"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/beebot/new-controller").servletPath("/api/beebot/new-controller").header("X-API-Key", "valid"))
            .andExpect(status().isOk()).andExpect(content().string("dashboard"));
    }

    @Test
    public void invalidAuthBodiesReturnSanitizedBadRequest() throws Exception {
        var client = new ApiKey("client", "dashboard", "https://example.com/callback");
        var context = new AuthContext(client, null, null, 0, null);
        var mvc = MockMvcBuilders.standaloneSetup(new DiscordAuthController())
            .setControllerAdvice(new AuthExceptionHandler(), new AuthRequestExceptionHandler())
            .addFilters(new BeebotAuthFilter((key, bearer, required) -> context)).build();
        for (String path : new String[]{"/api/beebot/auth/session", "/api/beebot/auth/discord/authorize"}) {
            for (String body : new String[]{"null", "{\"secret\":\"do-not-echo\"", ""}) {
                var response = mvc.perform(post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
                assertTrue(response.contains("invalid_request"));
                assertFalse(response.contains("do-not-echo"));
            }
        }
    }

    @RestController
    static class NewController {
        @GetMapping("/api/beebot/new-controller")
        public String get(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context) {
            return context.client().name();
        }
    }

    private static MockHttpServletResponse execute(BeebotAuthFilter filter, String method, String path, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setServletPath(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
