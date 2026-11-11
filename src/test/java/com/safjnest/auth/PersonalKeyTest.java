package com.safjnest.auth;

import com.safjnest.auth.DiscordApi.Server;
import java.util.List;
import com.safjnest.utils.CryptoUtils;
import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;

import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.redis.RedisClient;
import com.safjnest.sql.database.AuthDB;
import org.junit.Before;
import org.junit.Test;

public class PersonalKeyTest {

    private AuthDB database;
    private AuthDB keys;
    private DiscordAuthService auth;
    private com.safjnest.guild.DiscordGuildCache guildCache;
    private String url;
    private final AuthUser user = new AuthUser("42", "owner", "Owner", null);

    @Before
    public void setup() throws Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE api_key(id VARCHAR(36) PRIMARY KEY,name VARCHAR(100) UNIQUE,key_hash VARCHAR(43) UNIQUE,redirect_uris CLOB,type VARCHAR(8) DEFAULT 'generic',discord_user_id VARCHAR(32),user_json CLOB,active BOOLEAN DEFAULT TRUE,revoked_at TIMESTAMP)");
            sql.execute("CREATE TABLE auth_session(token_hash VARCHAR(43) PRIMARY KEY,api_key_id VARCHAR(36),discord_user_id VARCHAR(32),encrypted_discord_token CLOB,user_json CLOB,expires_at BIGINT,revoked_at TIMESTAMP)");
        }
        database = new AuthDB(() -> {
            try {
                Connection connection = DriverManager.getConnection(url);
                connection.setAutoCommit(false);
                return connection;
            } catch (Exception exception) { return null; }
        });
        keys = database;
        guildCache = com.safjnest.guild.DiscordGuildCache.memory();
        auth = auth(database);
    }

    @Test
    public void keyResolvesStablePermanentSessionAndDiscordIdentityWithoutOAuthOrCache() {
        String key = keys.generatePersonal("personal", user);
        assertTrue(key.matches("[A-Za-z0-9_-]{43}"));
        assertNull(database.findClient(key));
        ApiKey stored = database.findClient(CryptoUtils.sha256(key));
        assertEquals(ApiKey.Type.PERSONAL, stored.type());
        assertEquals(user.id(), stored.toUserId());
        assertNull(ApiKey.fromHash(database, CryptoUtils.sha256(key)).value());
        assertEquals(key, ApiKey.fromValue(database, key).value());
        assertEquals(user.id(), ApiKey.fromValue(database, key).toUserId());
        AuthContext context = auth.authenticate(key, null, true);
        assertTrue(context.personal());
        assertEquals(stored.id(), context.sessionHash());
        assertEquals(Long.MAX_VALUE, context.expiresAt());
        assertNull(context.encryptedToken());
        assertEquals(user, context.user());
        assertEquals(context.sessionHash(), auth(database).authenticate(key, null, true).sessionHash());
        assertFalse(context.toString().contains(key));
        assertEquals(0, database.cleanupSessions(Long.MAX_VALUE));
        assertNotNull(auth.authenticate(key, null, true));
    }

    @Test
    public void revocationIsImmediateAndScopedToOneKey() {
        String first = keys.generatePersonal("first", user);
        String second = keys.generatePersonal("second", user);
        auth.authenticate(first, null, true);
        assertTrue(keys.revokePersonalKey("first"));
        assertFalse(keys.revokePersonalKey("first"));
        assertStatus(401, () -> auth.authenticate(first, null, true));
        assertNotNull(auth.authenticate(second, null, true));
    }

    @Test
    public void logoutRevokesPermanentSessionAndKey() {
        String key = keys.generatePersonal("logout", user);
        AuthContext context = auth.authenticate(key, null, true);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Supplier<java.util.List<com.safjnest.auth.DiscordApi.Server>> load = () -> {
            calls.incrementAndGet(); return java.util.List.of();
        };
        guildCache.get(context, load);
        auth.logout(context);
        guildCache.get(context, load);
        assertEquals(2, calls.get());
        assertStatus(401, () -> auth.authenticate(key, null, true));
        assertStatus(401, () -> auth.logout(context));
    }

    @Test
    public void rejectsBearerMixingOAuthEndpointsAndInvalidKeys() {
        String key = keys.generatePersonal("personal", user);
        assertStatus(401, () -> auth.authenticate(key, "Bearer something", true));
        assertStatus(403, () -> auth.authenticate(key, null, false));
        assertStatus(401, () -> auth.authenticate("bbp_invalid", null, true));
        assertStatus(401, () -> auth.authenticate("a".repeat(43), null, true));
        assertStatus(401, () -> auth.authenticate(null, null, true));
    }

    @Test
    public void validatesIdentityAndKeepsNamesReservedAfterRevocation() {
        try { keys.generatePersonal("bad name", user); fail("Expected invalid name"); }
        catch (IllegalArgumentException expected) { }
        try { keys.generatePersonal("valid", new AuthUser("invalid", "user", null, null)); fail("Expected invalid user"); }
        catch (IllegalArgumentException expected) { }
        keys.generatePersonal("reserved", user);
        keys.revokePersonalKey("reserved");
        try { keys.generatePersonal("reserved", user); fail("Expected duplicate name"); }
        catch (IllegalStateException expected) { }
    }

    @Test
    public void unavailableStorageFailsClosed() {
        String key = keys.generatePersonal("personal", user);
        DiscordAuthService unavailable = auth(new AuthDB(() -> null));
        assertStatus(503, () -> unavailable.authenticate(key, null, true));
    }

    @Test
    public void personalKeyAuthenticatesProtectedHttpRoutesWithOnlyApiKeyHeader() throws Exception {
        String key = keys.generatePersonal("http", user);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
            .standaloneSetup(new com.safjnest.spring.controller.DiscordAuthController(auth, new com.safjnest.guild.DiscordGuildService(
                new DiscordApi(request -> { throw new AssertionError("No OAuth expected"); }, () -> (net.dv8tion.jda.api.JDA) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{net.dv8tion.jda.api.JDA.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getStatus" -> net.dv8tion.jda.api.JDA.Status.CONNECTED;
                        case "getGuilds" -> java.util.List.of();
                        default -> null;
                    })), com.safjnest.guild.DiscordGuildCache.memory(), null)))
            .setControllerAdvice(new com.safjnest.spring.controller.AuthExceptionHandler())
            .addFilters(new com.safjnest.spring.config.BeebotAuthFilter(auth::authenticate)).build();
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/beebot/me").servletPath("/beebot/me").header("X-API-Key", key);
        var response = mvc.perform(request)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn().getResponse();
        assertTrue(response.getContentAsString().contains("\"id\":\"42\""));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/beebot/auth/session").servletPath("/beebot/auth/session").header("X-API-Key", key))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        keys.revokePersonalKey("http");
        mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    public void databaseTypeAloneDeterminesWhetherBearerIsRequired() throws Exception {
        String key = keys.generatePersonal("typed", user);
        assertTrue(auth.authenticate(key, null, true).personal());
        try (Connection connection = DriverManager.getConnection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE api_key SET type='generic' WHERE name='typed'");
        }
        AuthContext generic = auth.authenticate(key, null, false);
        assertEquals(ApiKey.Type.GENERIC, generic.client().type());
        assertFalse(generic.personal());
        assertNull(generic.user());
        assertStatus(401, () -> auth.authenticate(key, null, true));
        try (Connection connection = DriverManager.getConnection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE api_key SET type='personal' WHERE name='typed'");
        }
        assertEquals(user, auth.authenticate(key, null, true).user());
        try (Connection connection = DriverManager.getConnection(url); Statement sql = connection.createStatement()) {
            sql.executeUpdate("UPDATE api_key SET type='invalid' WHERE name='typed'");
        }
        assertStatus(401, () -> auth.authenticate(key, null, true));
    }

    @Test
    public void bothTypesShareOneTableOneNameNamespaceAndOneKeyFormat() throws Exception {
        AuthDB configured = database;
        String genericKey = configured.generate("generic", "https://safjnest.com/auth/callback/");
        String personalKey = configured.generatePersonal("personal", user);
        assertEquals(genericKey.length(), personalKey.length());
        assertEquals(ApiKey.Type.GENERIC, database.findClient(CryptoUtils.sha256(genericKey)).type());
        assertEquals(ApiKey.Type.PERSONAL, database.findClient(CryptoUtils.sha256(personalKey)).type());
        assertFalse(configured.revokePersonalKey("generic"));
        assertNull(configured.revokeClient("personal"));
        assertNotNull(database.findClient(CryptoUtils.sha256(genericKey)));
        assertNotNull(database.findClient(CryptoUtils.sha256(personalKey)));
        try { configured.generatePersonal("generic", user); fail("Expected shared name uniqueness"); }
        catch (IllegalStateException expected) { }
        try (Connection connection = DriverManager.getConnection(url); Statement sql = connection.createStatement();
                var rows = sql.executeQuery("SELECT COUNT(*) FROM api_key")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    private DiscordAuthService auth(AuthDB database) {
        DiscordAuthService.Store cache = new DiscordAuthService.Store() {
            public RedisClient.AuthValue<String> get(String key) { throw new AssertionError("Personal keys do not use cache"); }
            public boolean set(String key, String value, int ttl) { throw new AssertionError("Personal keys do not use cache"); }
            public boolean delete(String key) { throw new AssertionError("Personal keys do not use cache"); }
        };
        return new DiscordAuthService(database, cache, request -> { throw new AssertionError("No OAuth request expected"); },
            "test:", null, null, null, guildCache);
    }

    private void assertStatus(int status, Runnable action) {
        try { action.run(); fail("Expected status " + status); }
        catch (AuthException exception) { assertEquals(status, exception.getStatusCode().value()); }
    }
}
