package com.safjnest.spring;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.spring.controller.lol.LolApiResponses;
import com.safjnest.spring.exception.ApiErrors;
import com.safjnest.spring.exception.ApiExceptionHandler;

import jakarta.servlet.RequestDispatcher;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

public class ApiErrorsTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> SCOPES = List.of("beebot", "lol");

    private final ProbeController controller = new ProbeController();
    private MockMvc mvc;

    @Before
    public void setup() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    public void filterAndAdviceShareBodyStatusAndRetryHeader() throws java.lang.Exception {
        Exception failure = new Exception(Type.RATE_LIMITED, "discord_rate_limited", "Discord rate limit exceeded", 1.25);
        controller.failure = failure;
        MockHttpServletResponse advised = mvc.perform(get("/api/beebot/probe/application"))
            .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "2"))
            .andReturn().getResponse();
        MockHttpServletResponse filtered = new MockHttpServletResponse();
        AuthFilter filter = new AuthFilter((key, bearer, session) -> { throw failure; });

        filter.doFilter(request("GET", "/api/beebot/me"), filtered, (request, response) -> fail("Filter must stop"));

        assertEquals(advised.getStatus(), filtered.getStatus());
        assertEquals(advised.getHeader("Retry-After"), filtered.getHeader("Retry-After"));
        assertEquals(JSON.readTree(advised.getContentAsString()), JSON.readTree(filtered.getContentAsString()));
        assertEquals("no-store", filtered.getHeader("Cache-Control"));
    }

    @Test
    public void preservesDistinctErrorContractsWithoutSerializingTheCause() throws java.lang.Exception {
        controller.failure = new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found", new IOException("private cause"), null);

        for (String scope : SCOPES) {
            JsonNode body = body(mvc.perform(get("/api/" + scope + "/probe/application"))
                .andExpect(status().isNotFound()).andReturn().getResponse());

            assertEquals("sound_not_found", body.get("code").asText());
            assertEquals("Sound not found", body.get("message").asText());
            assertFalse(body.toString().contains("private cause"));
            assertFalse(body.has("retryAfter"));

            if (scope.equals("beebot")) {
                assertEquals(2, body.size());
            } else {
                assertEquals(404, body.get("status").asInt());
                assertTrue(body.has("metadata"));
                assertTrue(body.get("metadata").isNull());
            }
        }
    }

    @Test
    public void invalidParameterKeepsScopeSpecificMessages() throws java.lang.Exception {
        mvc.perform(get("/api/beebot/probe/parameter").param("page", "bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonValue("/code", "invalid_request"))
            .andExpect(jsonValue("/message", "Invalid request parameter"));
        mvc.perform(get("/api/lol/probe/parameter").param("page", "bad"))
            .andExpect(status().isBadRequest()).andExpect(jsonValue("/status", 400))
            .andExpect(jsonValue("/message", "Invalid value for parameter 'page'"));
    }

    @Test
    public void missingParameterUsesTheCommonMapper() throws java.lang.Exception {
        for (String scope : SCOPES)
            mvc.perform(get("/api/" + scope + "/probe/parameter"))
                .andExpect(status().isBadRequest()).andExpect(jsonValue("/code", "invalid_request"))
                .andExpect(jsonValue("/message", "Missing required query parameter 'page'"));
    }

    @Test
    public void malformedAndMissingBodiesAreSanitized() throws java.lang.Exception {
        for (String scope : SCOPES) {
            for (String value : List.of("", "{\"private-input\":"))
                mvc.perform(post("/api/" + scope + "/probe/body").contentType("application/json").content(value))
                    .andExpect(status().isBadRequest()).andExpect(jsonValue("/code", "invalid_request"))
                    .andExpect(jsonValue("/message", "Invalid JSON request body"));
        }
    }

    @Test
    public void missingRouteAndWrongMethodUseTheirOwnScope() throws java.lang.Exception {
        for (String scope : SCOPES) {
            JsonNode missing = body(mvc.perform(get("/api/" + scope + "/absent"))
                .andExpect(status().isNotFound()).andReturn().getResponse());
            JsonNode method = body(mvc.perform(get("/api/" + scope + "/probe/body"))
                .andExpect(status().isMethodNotAllowed()).andReturn().getResponse());

            assertEquals("not_found", missing.get("code").asText());
            assertEquals("method_not_allowed", method.get("code").asText());
            assertEquals(scope.equals("lol"), missing.has("status"));
            assertEquals(scope.equals("lol"), method.has("status"));
        }
    }

    @Test
    public void unexpectedJavaExceptionIsSanitizedInBothScopes() throws java.lang.Exception {
        for (String scope : SCOPES)
            mvc.perform(get("/api/" + scope + "/probe/unexpected"))
                .andExpect(status().isInternalServerError()).andExpect(jsonValue("/code", "internal_error"))
                .andExpect(jsonValue("/message", "Unexpected server error"));
    }

    @Test
    public void responseStatusExceptionKeepsTheExistingLolContract() throws java.lang.Exception {
        mvc.perform(get("/api/lol/probe/status"))
            .andExpect(status().isNotFound()).andExpect(jsonValue("/status", 404))
            .andExpect(jsonValue("/code", "not_found")).andExpect(jsonValue("/message", "Missing profile"));
    }

    @Test
    public void pendingAndPartialLolResponsesKeepMetadataAndPayload() throws java.lang.Exception {
        mvc.perform(get("/api/lol/probe/pending"))
            .andExpect(status().isAccepted()).andExpect(jsonValue("/status", 202))
            .andExpect(jsonValue("/code", "pending_profile"))
            .andExpect(jsonValue("/metadata/lastUpdate", 123))
            .andExpect(jsonValue("/metadata/refresh", true));

        ResponseEntity<?> partial = LolApiResponses.from(ApiResult.partial(Map.of("value", 42)), "pending", "Pending", "Missing");
        assertEquals(200, partial.getStatusCode().value());
        assertEquals(Map.of("value", 42), partial.getBody());
    }

    @Test
    public void filterRequiresSessionExceptForTheTwoPostLoginRoutes() throws java.lang.Exception {
        List<Boolean> required = new ArrayList<>();
        AuthFilter filter = new AuthFilter((key, bearer, session) -> {
            required.add(session);
            assertEquals("key", key);
            assertEquals("Bearer token", bearer);
            return null;
        });

        for (String path : List.of("/api/beebot/auth/discord/authorize", "/api/beebot/auth/session", "/api/beebot/auth/logout", "/api/beebot/me")) {
            MockHttpServletRequest request = request("POST", path);
            request.addHeader("X-API-Key", "key");
            request.addHeader("Authorization", "Bearer token");
            filter.doFilter(request, new MockHttpServletResponse(), (incoming, response) -> {});
        }

        assertEquals(List.of(false, false, true, true), required);
    }

    @Test
    public void filterDoesNotAuthenticateLolOrSimilarPrefixes() throws java.lang.Exception {
        AtomicInteger chained = new AtomicInteger();
        AuthFilter filter = new AuthFilter((key, bearer, session) -> { throw new AssertionError("Unexpected authentication"); });

        for (String path : List.of("/api/lol/profile", "/api/status", "/api/beebot-other"))
            filter.doFilter(request("GET", path), new MockHttpServletResponse(), (incoming, response) -> chained.incrementAndGet());

        assertEquals(3, chained.get());
    }

    @Test
    public void invalidPathsStopBeforeAuthenticationAndIncludesStayProtected() throws java.lang.Exception {
        AtomicInteger verified = new AtomicInteger();
        AuthFilter filter = new AuthFilter((key, bearer, session) -> {
            verified.incrementAndGet();
            throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "Login required");
        });
        MockHttpServletRequest malformed = request("GET", "/api/beebot/me;extra");
        MockHttpServletResponse rejected = new MockHttpServletResponse();

        filter.doFilter(malformed, rejected, (incoming, response) -> fail("Invalid path"));
        assertEquals(400, rejected.getStatus());
        assertEquals(0, verified.get());

        MockHttpServletRequest included = request("GET", "/public");
        included.setAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH, "/api/beebot/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(included, response, (incoming, result) -> fail("Included route requires authentication"));
        assertEquals(401, response.getStatus());
        assertEquals("invalid_session", body(response).get("code").asText());
        assertFalse(body(response).has("status"));
    }

    /* -------------------------------------------------------------------------- */

    private static ResultMatcher jsonValue(String pointer, Object expected) {
        return result -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(result.getResponse().getContentAsString());

            assertEquals(mapper.valueToTree(expected), body.at(pointer));
        };
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);

        return request;
    }

    private static JsonNode body(MockHttpServletResponse response) throws java.lang.Exception {
        return JSON.readTree(response.getContentAsString());
    }

    @RestController
    @RequestMapping({"/api/beebot/probe", "/api/lol/probe"})
    public static class ProbeController {
        private Exception failure;

        @GetMapping("/application")
        public void application() {
            throw failure;
        }

        @GetMapping("/parameter")
        public int parameter(@RequestParam("page") int page) {
            return page;
        }

        @PostMapping("/body")
        public JsonNode body(@RequestBody JsonNode body) {
            return body;
        }

        @GetMapping("/unexpected")
        public void unexpected() throws IOException {
            throw new IOException("Private implementation detail");
        }

        @GetMapping("/status")
        public void status() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Missing profile");
        }

        @GetMapping("/pending")
        public ResponseEntity<?> pending() {
            return LolApiResponses.from(ApiResult.pending(ResponseMetadata.pending(123L, null)), "pending_profile", "Queued", "Missing");
        }
    }
}
