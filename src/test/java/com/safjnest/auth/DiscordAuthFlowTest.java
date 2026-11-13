package com.safjnest.auth;

import com.safjnest.utils.CryptoUtils;
import com.safjnest.sql.database.AuthDB;

import static org.junit.Assert.*;
import java.lang.reflect.Proxy;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;
import com.safjnest.redis.RedisClient;

public class DiscordAuthFlowTest {
    private static final String REDIRECT = "https://safjnest.com/auth/callback/";
    private static final String STATE = "sssssssssssssssssssssssssssssssssssssssssss";
    private static final String KEY = "bbp_" + "a".repeat(39);
    private static final String OTHER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    public void loginReplayFreshGuildsRevocationAndStorageFailures() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(url); Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE api_key(id VARCHAR(36) PRIMARY KEY,name VARCHAR(100) UNIQUE,key_hash VARCHAR(43) UNIQUE,redirect_uris CLOB,type VARCHAR(8) DEFAULT 'generic',discord_user_id VARCHAR(32),user_json CLOB,active BOOLEAN DEFAULT TRUE,revoked_at TIMESTAMP)");
            sql.execute("CREATE TABLE auth_session(token_hash VARCHAR(43) PRIMARY KEY,api_key_id VARCHAR(36),discord_user_id VARCHAR(32),encrypted_discord_token CLOB,user_json CLOB,expires_at BIGINT,revoked_at TIMESTAMP)");
            sql.execute("CREATE TABLE auth_transaction(state_hash VARCHAR(43),api_key_id VARCHAR(36),redirect_uri VARCHAR(2048),expires_at BIGINT,PRIMARY KEY(state_hash,api_key_id))");
        }
        AuthDB repository = new AuthDB(() -> {
            try { Connection connection = DriverManager.getConnection(url); connection.setAutoCommit(false); return connection; }
            catch (Exception failure) { return null; }
        });
        repository.createClient("one", "one", CryptoUtils.sha256(KEY), REDIRECT);
        repository.createClient("two", "two", CryptoUtils.sha256(OTHER), REDIRECT);
        MemoryStore cache = new MemoryStore();
        cache.available = false;
        FakeDiscord discord = new FakeDiscord();
        DiscordAuthService service = new DiscordAuthService(repository, cache, discord, "test:", "client", "secret",
            Base64.getEncoder().encodeToString(new byte[32]));
        var app = service.authenticate(KEY, null, false);
        assertStatus(400, () -> service.exchangeSession(app, "code", "bad", REDIRECT));
        service.authorize(app, STATE, REDIRECT);
        var other = service.authenticate(OTHER, null, false);
        assertStatus(401, () -> service.exchangeSession(other, "code", STATE, REDIRECT));
        var session = service.exchangeSession(app, "code", STATE, REDIRECT);
        assertStatus(401, () -> service.exchangeSession(app, "code", STATE, REDIRECT));
        String secondState = "ttttttttttttttttttttttttttttttttttttttttttt";
        service.authorize(app, secondState, REDIRECT);
        discord.tokenStatus = 401;
        assertStatus(502, () -> service.exchangeSession(app, "bad-code", secondState, REDIRECT));
        String thirdState = "uuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu";
        service.authorize(app, thirdState, REDIRECT);
        discord.tokenStatus = 503;
        assertStatus(503, () -> service.exchangeSession(app, "code", thirdState, REDIRECT));
        discord.tokenStatus = 200;
        cache.available = true;
        String bearer = "Bearer " + session.sessionToken();
        assertStatus(401, () -> service.authenticate(OTHER, bearer, true));
        var context = service.authenticate(KEY, bearer, true);
        var guildCache = com.safjnest.guild.DiscordGuildCache.memory();
        var directory = new com.safjnest.guild.DiscordGuildService(new DiscordApi(discord, () -> null), guildCache,
            Base64.getEncoder().encodeToString(new byte[32]));
        var servers = directory.servers(context);
        assertEquals(3, servers.size());
        assertTrue(servers.get(0).owner());
        assertFalse(servers.get(1).owner());
        assertEquals(1, discord.guildCalls);
        guildCache.invalidate(context);
        discord.status = 401;
        assertStatus(401, () -> directory.servers(context));
        discord.status = 503;
        assertStatus(503, () -> directory.servers(context));
        assertTrue(repository.revokeSession(context.sessionHash(), "one"));
        assertStatus(401, () -> service.authenticate(KEY, bearer, true));
        repository.revokeClient("one");
        assertStatus(401, () -> service.authenticate(KEY, null, false));
        cache.available = false;
        assertNotNull(service.authenticate(OTHER, null, false));
        var unavailable = new DiscordAuthService(new AuthDB(() -> null), cache, discord, "test:", "client", "secret",
            Base64.getEncoder().encodeToString(new byte[32]));
        assertStatus(503, () -> unavailable.authenticate(OTHER, null, false));
    }

    private static void assertStatus(int status, Runnable action) {
        try { action.run(); fail("Expected status " + status); }
        catch (AuthException exception) { assertEquals(status, exception.getStatusCode().value()); }
    }

    private static final class MemoryStore implements DiscordAuthService.Store {
        final Map<String, String> values = new ConcurrentHashMap<>();
        boolean available = true;
        public RedisClient.AuthValue<String> get(String key) { return new RedisClient.AuthValue<>(available, available ? values.get(key) : null); }
        public boolean set(String key, String value, int ttl) { if (!available) return false; values.put(key, value); return true; }
        public boolean delete(String key) { if (!available) return false; values.remove(key); return true; }
    }

    private static final class FakeDiscord implements DiscordApi.Transport {
        int guildCalls;
        int status = 200;
        int tokenStatus = 200;
        public HttpResponse<String> send(HttpRequest request) {
            String path = request.uri().getPath();
            String body;
            int result = 200;
            if (path.endsWith("/oauth2/token")) { result = tokenStatus; body = "{\"access_token\":\"discord-token\",\"token_type\":\"Bearer\",\"expires_in\":3600,\"scope\":\"identify guilds\"}"; }
            else if (path.endsWith("/users/@me")) body = "{\"id\":\"42\",\"username\":\"tester\"}";
            else {
                result = status;
                guildCalls++;
                if (request.uri().getQuery().contains("after=")) body = "[{\"id\":\"201\",\"name\":\"admin\",\"owner\":false,\"permissions\":\"8\"}]";
                else {
                    StringBuilder list = new StringBuilder("[");
                    for (int i = 1; i <= 200; i++) {
                        if (i > 1) list.append(',');
                        list.append("{\"id\":\"").append(i).append("\",\"name\":\"guild\",\"owner\":").append(i == 1)
                            .append(i == 2 ? ",\"permissions\":\"32\"}" : i == 3 ? ",\"permissions\":\"8\"}" : ",\"permissions\":\"0\"}");
                    }
                    body = list.append(']').toString();
                }
            }
            int finalStatus = result;
            String finalBody = body;
            return (HttpResponse<String>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpResponse.class},
                (proxy, method, args) -> switch (method.getName()) { case "statusCode" -> finalStatus; case "body" -> finalBody; default -> null; });
        }
    }
}
