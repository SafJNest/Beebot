package com.safjnest.discord.auth;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.safjnest.database.redis.RedisClient;
import com.safjnest.database.sql.database.AuthDB;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordApi.DiscordToken;
import com.safjnest.discord.auth.DiscordApi.Server;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.guild.DiscordGuildCache;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.spring.controller.beebot.DiscordAuthController;
import com.safjnest.spring.controller.beebot.GuildController;
import com.safjnest.spring.exception.ApiExceptionHandler;
import com.safjnest.utils.CryptoUtils;
import com.safjnest.utils.JsonCodec;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class DiscordAuthServiceTest {

    private static final String GENERIC_KEY = "a".repeat(43);
    private static final String PERSONAL_KEY = "b".repeat(43);
    private static final String SESSION = "c".repeat(43);
    private static final String REDIRECT = "https://dashboard.example/callback";
    private static final String ENCRYPTION = Base64.getEncoder().encodeToString(new byte[32]);
    private static final AuthUser USER = new AuthUser("2", "user", "User", null);

    private final AtomicBoolean unavailable = new AtomicBoolean();
    private final MemoryStore store = new MemoryStore();
    private final FakeDiscord discord = new FakeDiscord();
    private final DiscordGuildCache guildCache = DiscordGuildCache.memory();

    private String url;
    private AuthDB database;
    private DiscordAuthService auth;

    @Before
    public void setup() throws java.lang.Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1";

        try (Connection connection = connection()) {
            for (String sql : List.of(
                "CREATE TABLE api_key (id VARCHAR(36) PRIMARY KEY, name VARCHAR(100) UNIQUE, key_hash VARCHAR(43) UNIQUE, redirect_uris TEXT, type VARCHAR(10) DEFAULT 'generic', discord_user_id VARCHAR(32), user_json TEXT, active BOOLEAN DEFAULT TRUE, revoked_at TIMESTAMP)",
                "CREATE TABLE auth_session (token_hash VARCHAR(43) PRIMARY KEY, api_key_id VARCHAR(36) REFERENCES api_key(id), discord_user_id VARCHAR(32), encrypted_discord_token TEXT, user_json TEXT, expires_at BIGINT, revoked_at TIMESTAMP)",
                "CREATE TABLE auth_transaction (state_hash VARCHAR(43), api_key_id VARCHAR(36) REFERENCES api_key(id), redirect_uri VARCHAR(2048), expires_at BIGINT, PRIMARY KEY(state_hash, api_key_id))"
            ))
                connection.createStatement().execute(sql);

            connection.commit();
        }

        database = new AuthDB(() -> unavailable.get() ? null : connection());
        database.createClient("generic", "generic", CryptoUtils.sha256(GENERIC_KEY), REDIRECT);
        database.createPersonalKey("personal", "personal", CryptoUtils.sha256(PERSONAL_KEY), USER.id(), JsonCodec.toJson(USER));
        database.createSession(CryptoUtils.sha256(SESSION), new AuthDB.Session("generic", USER.id(),
            CryptoUtils.encrypt("discord-token", CryptoUtils.parseEncryptionKey(ENCRYPTION)), JsonCodec.toJson(USER), System.currentTimeMillis() + 600_000));

        auth = new DiscordAuthService(database, store, discord, guildCache, "test:", "discord-app", "test-secret", ENCRYPTION);
    }

    @Test
    public void genericKeyRequiresSessionOnProtectedRoutes() {
        AuthContext beforeLogin = auth.authenticate(GENERIC_KEY, null, false);
        assertNull(beforeLogin.user());
        assertFalse(beforeLogin.personal());
        assertEquals("invalid_session", assertThrows(Exception.class, () -> auth.authenticate(GENERIC_KEY, null, true)).getCode());

        AuthContext context = session();
        assertEquals(USER, context.user());
        assertFalse(context.personal());
    }

    @Test
    public void personalKeyNeedsNoOauthAndRejectsBearerAndLoginRoutes() {
        AuthContext context = auth.authenticate(PERSONAL_KEY, null, true);
        assertTrue(context.personal());
        assertEquals(USER, context.user());
        assertEquals(Long.MAX_VALUE, context.expiresAt());
        assertNull(context.encryptedToken());
        assertEquals("personal", context.sessionHash());

        assertEquals("invalid_personal_key", assertThrows(Exception.class,
            () -> auth.authenticate(PERSONAL_KEY, "Bearer " + SESSION, true)).getCode());
        assertEquals("personal_key_not_allowed", assertThrows(Exception.class,
            () -> auth.authenticate(PERSONAL_KEY, null, false)).getCode());
        assertEquals(0, discord.oauthCalls);
    }

    @Test
    public void cachedSessionStillChecksSqlRevocation() {
        session();
        assertFalse(store.values.isEmpty());
        assertTrue(database.revokeSession(CryptoUtils.sha256(SESSION), "generic"));

        assertEquals("invalid_session", assertThrows(Exception.class, this::session).getCode());
        assertFalse(store.values.isEmpty());
    }

    @Test
    public void cachedSessionStillChecksExpiryAndClientIdentity() throws java.lang.Exception {
        session();
        execute("UPDATE auth_session SET expires_at = 0");
        assertEquals(Type.UNAUTHENTICATED, assertThrows(Exception.class, this::session).getType());

        database.createClient("other", "other", CryptoUtils.sha256("d".repeat(43)), REDIRECT);
        assertEquals("invalid_session", assertThrows(Exception.class,
            () -> auth.authenticate("d".repeat(43), "Bearer " + SESSION, true)).getCode());
    }

    @Test
    public void sqlFallbackReadsSessionTextAndUsesTheSameExpiryBoundaryAsTheCacheCheck() {
        store.available = false;
        String hash = CryptoUtils.sha256(SESSION);
        AuthDB.Session stored = database.findSession(hash, "generic", 0);
        assertNotNull(stored);

        AuthContext context = session();
        assertEquals(USER, context.user());
        assertEquals(stored.expiresAt(), context.expiresAt());
        assertTrue(store.values.isEmpty());

        long expiresAt = stored.expiresAt();
        assertNotNull(database.findSession(hash, "generic", expiresAt - 1));
        assertTrue(database.sessionActive(hash, "generic", expiresAt - 1));
        assertNull(database.findSession(hash, "generic", expiresAt));
        assertFalse(database.sessionActive(hash, "generic", expiresAt));
    }

    @Test
    public void databaseOutageIsUnavailableEvenWithWarmCaches() {
        session();
        unavailable.set(true);

        Exception failure = assertThrows(Exception.class, this::session);
        assertEquals(Type.UNAVAILABLE, failure.getType());
        assertEquals("auth_unavailable", failure.getCode());
    }

    @Test
    public void authFilterDistinguishesInvalidCredentialsFromSqlOutage() throws java.lang.Exception {
        session();
        MockHttpServletResponse invalid = filteredRequest("z".repeat(43));
        assertEquals(401, invalid.getStatus());
        assertEquals("invalid_api_key", JsonCodec.fromJson(invalid.getContentAsString(), Map.class).get("code"));

        execute("DROP TABLE auth_session");
        MockHttpServletResponse failed = filteredRequest(GENERIC_KEY);
        assertEquals(503, failed.getStatus());
        assertEquals("auth_unavailable", JsonCodec.fromJson(failed.getContentAsString(), Map.class).get("code"));
    }

    @Test
    public void missingOrRevokedKeyIsUnauthorized() throws java.lang.Exception {
        assertEquals("invalid_api_key", assertThrows(Exception.class, () -> auth.authenticate("short", null, false)).getCode());
        assertEquals("invalid_api_key", assertThrows(Exception.class, () -> auth.authenticate("z".repeat(43), null, false)).getCode());
        execute("UPDATE api_key SET active = FALSE WHERE id = 'generic'");

        assertEquals(Type.UNAUTHENTICATED, assertThrows(Exception.class, this::session).getType());
    }

    @Test
    public void oauthDirectoryAndAdministrativeChecksReuseOneCachedLoad() {
        AuthContext context = session();
        assertEquals(discord.servers, auth.servers(context));
        auth.requireAdministrator(context, "1");
        assertEquals(discord.servers, auth.servers(context));
        assertEquals(1, discord.oauthCalls);
        assertEquals("discord-token", discord.lastToken);

        assertEquals(Type.FORBIDDEN, assertThrows(Exception.class, () -> auth.requireAdministrator(context, "99")).getType());
        assertEquals(1, discord.oauthCalls);
    }

    @Test
    public void personalDirectoryWorksWithoutOauthConfiguration() {
        DiscordAuthService personal = new DiscordAuthService(database, store, discord, guildCache, "test:", null, null, null);
        AuthContext context = personal.authenticate(PERSONAL_KEY, null, true);
        personal.requireAdministrator(context, "1");
        assertEquals(discord.servers, personal.servers(context));

        assertEquals(1, discord.personalCalls);
        assertEquals(0, discord.oauthCalls);
        assertEquals("2", discord.lastUser);
    }

    @Test
    public void authorizationCacheDoesNotMixApplicationsOrSessions() {
        AuthContext context = session();
        auth.servers(context);
        AuthContext otherSession = new AuthContext(context.client(), "other-session", context.encryptedToken(), context.expiresAt(), USER);
        AuthContext otherClient = new AuthContext(new ApiKey("other-client", "Other", REDIRECT), context.sessionHash(),
            context.encryptedToken(), context.expiresAt(), USER);

        auth.servers(otherSession);
        auth.servers(otherClient);

        assertEquals(3, discord.oauthCalls);
    }

    @Test
    public void logoutRevokesAndInvalidatesSessionAndGuildCaches() {
        AuthContext context = session();
        auth.servers(context);
        auth.logout(context);

        assertTrue(store.values.isEmpty());
        assertEquals(Type.UNAUTHENTICATED, assertThrows(Exception.class, this::session).getType());
        auth.servers(context);
        assertEquals(2, discord.oauthCalls);
    }

    @Test
    public void personalLogoutRevokesTheKey() {
        AuthContext context = auth.authenticate(PERSONAL_KEY, null, true);
        auth.logout(context);

        assertEquals("invalid_api_key", assertThrows(Exception.class, () -> auth.authenticate(PERSONAL_KEY, null, true)).getCode());
    }

    @Test
    public void oauthTransactionIsConsumedOnceAndTokenStaysEncrypted() {
        AuthContext beforeLogin = auth.authenticate(GENERIC_KEY, null, false);
        String state = "s".repeat(32);
        auth.authorize(beforeLogin, state, REDIRECT);
        DiscordAuthService.Session created = auth.exchangeSession(beforeLogin, "code", state, REDIRECT);
        AuthDB.Session stored = database.findSession(CryptoUtils.sha256(created.sessionToken()), "generic", System.currentTimeMillis());

        assertEquals(43, created.sessionToken().length());
        assertEquals(USER, created.user());
        assertNotNull(stored);
        assertNotEquals("discord-token", stored.encryptedToken());
        assertEquals("discord-token", CryptoUtils.decrypt(stored.encryptedToken(), CryptoUtils.parseEncryptionKey(ENCRYPTION)));
        assertEquals(1, discord.exchanges);
        assertEquals("invalid_state", assertThrows(Exception.class,
            () -> auth.exchangeSession(beforeLogin, "code", state, REDIRECT)).getCode());
        assertEquals(1, discord.exchanges);
    }

    @Test
    public void concurrentOAuthConsumptionSucceedsOnceForTheBoundClientAndCallback() throws java.lang.Exception {
        String hash = CryptoUtils.sha256("s".repeat(32));
        long now = System.currentTimeMillis();
        database.createTransaction(hash, "generic", REDIRECT, now + 60_000);

        assertFalse(database.consumeTransaction(hash, "other", REDIRECT, now));
        assertFalse(database.consumeTransaction(hash, "generic", REDIRECT + "/", now));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = workers.submit(() -> consumeState(ready, start, hash, now));
            Future<Boolean> second = workers.submit(() -> consumeState(ready, start, hash, now));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }

        assertFalse(database.consumeTransaction(hash, "generic", REDIRECT, now));
    }

    @Test
    public void expiredTransactionAndUnapprovedRedirectStopBeforeDiscord() {
        AuthContext context = auth.authenticate(GENERIC_KEY, null, false);
        String state = "s".repeat(32);
        database.createTransaction(CryptoUtils.sha256(state), "generic", REDIRECT, 0);

        assertEquals("invalid_state", assertThrows(Exception.class, () -> auth.exchangeSession(context, "code", state, REDIRECT)).getCode());
        assertEquals("redirect_not_allowed", assertThrows(Exception.class,
            () -> auth.authorize(context, state, "https://other.example/callback")).getCode());
        assertEquals(0, discord.exchanges);
    }

    @Test
    public void guildRoutesKeepUrlsAliasesAndPayloadAfterMovingController() throws java.lang.Exception {
        AuthContext context = session();
        GuildController guilds = new GuildController(null, id -> null, auth::requireAdministrator, auth::servers, id -> {});
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DiscordAuthController(auth), guilds)
            .setControllerAdvice(new ApiExceptionHandler()).build();

        for (String path : List.of("/api/beebot/guilds", "/api/beebot/servers"))
            mvc.perform(get(path).requestAttr(AuthFilter.AUTH_CONTEXT, context))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonValue("/0/id", "1")).andExpect(jsonValue("/0/name", "Guild"));

        mvc.perform(post("/api/beebot/auth/discord/authorize").requestAttr(AuthFilter.AUTH_CONTEXT, context))
            .andExpect(status().isBadRequest()).andExpect(jsonValue("/code", "invalid_request"));
        assertEquals(1, discord.oauthCalls);
    }

    /* -------------------------------------------------------------------------- */

    private static ResultMatcher jsonValue(String pointer, Object expected) {
        return result -> {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode body = mapper.readTree(result.getResponse().getContentAsString());

            assertEquals(mapper.valueToTree(expected), body.at(pointer));
        };
    }

    private AuthContext session() {
        return auth.authenticate(GENERIC_KEY, "Bearer " + SESSION, true);
    }

    private MockHttpServletResponse filteredRequest(String key) throws java.lang.Exception {
        String path = "/api/beebot/guilds";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.addHeader("X-API-Key", key);
        request.addHeader("Authorization", "Bearer " + SESSION);

        MockHttpServletResponse response = new MockHttpServletResponse();
        new AuthFilter(auth::authenticate).doFilter(request, response,
            (incoming, outgoing) -> fail("Request should have been rejected by authentication"));

        return response;
    }

    private boolean consumeState(CountDownLatch ready, CountDownLatch start, String hash, long now) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));

        return database.consumeTransaction(hash, "generic", REDIRECT, now);
    }

    private Connection connection() {
        try {
            Connection connection = DriverManager.getConnection(url);
            connection.setAutoCommit(false);

            return connection;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void execute(String sql) throws java.lang.Exception {
        try (Connection connection = connection()) {
            connection.createStatement().execute(sql);
            connection.commit();
        }
    }

    private static class MemoryStore implements DiscordAuthService.Store {
        private final Map<String, String> values = new HashMap<>();
        private boolean available = true;

        public RedisClient.AuthValue<String> get(String key) {
            return new RedisClient.AuthValue<>(available, available ? values.get(key) : null);
        }

        public boolean set(String key, String value, int ttl) {
            if (!available)
                return false;

            values.put(key, value);
            return true;
        }

        public boolean delete(String key) {
            values.remove(key);
            return true;
        }
    }

    private static class FakeDiscord extends DiscordApi {
        private final List<Server> servers = List.of(new Server("1", "Guild", null, false));
        private int oauthCalls;
        private int personalCalls;
        private int exchanges;
        private String lastToken;
        private String lastUser;

        @Override
        public List<Server> guilds(String token) {
            oauthCalls++;
            lastToken = token;
            return servers;
        }

        @Override
        public List<Server> guildsForUser(String userId) {
            personalCalls++;
            lastUser = userId;
            return servers;
        }

        @Override
        public DiscordToken exchange(String clientId, String secret, String code, String redirectUri) {
            exchanges++;
            return new DiscordToken("discord-token", "Bearer", 3600, null, "identify guilds");
        }

        @Override
        public AuthUser me(String token) {
            return USER;
        }
    }
}
