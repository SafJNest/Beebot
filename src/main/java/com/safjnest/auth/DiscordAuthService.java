package com.safjnest.auth;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import com.safjnest.App;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.auth.DiscordApi.DiscordToken;
import com.safjnest.redis.RedisClient;
import com.safjnest.sql.database.AuthDB;
import com.safjnest.utils.CryptoUtils;
import com.safjnest.utils.JsonCodec;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DiscordAuthService {

    private static final long AUTH_TRANSACTION_TTL_SECONDS = 600;
    private static final long SESSION_TTL_SECONDS = Duration.ofHours(8).toSeconds();
    private static final int CACHE_TTL_SECONDS = 60;
    private final String discordClientId;
    private final String discordClientSecret;
    private final SecretKeySpec encryptionKey;
    private final AuthDB database;
    private final Store store;
    private final DiscordApi discord;
    private final com.safjnest.guild.DiscordGuildCache guildCache;
    private final String prefixOverride;

    interface Store {
        RedisClient.AuthValue<String> get(String key);
        boolean set(String key, String value, int ttl);
        boolean delete(String key);
    }
    public record AuthContext(ApiKey client, String sessionHash, String encryptedToken, long expiresAt, AuthUser user) {
        public boolean personal() { return client.type() == ApiKey.Type.PERSONAL; }
        public String cacheIdentity() {
            return user.id() + ":" + CryptoUtils.sha256(client.id() + ":" + sessionHash + ":" + encryptedToken);
        }
        @Override public String toString() { return "AuthContext[user=" + user + ", expiresAt=" + expiresAt + "]"; }
    }

    public record Authorization(String authorizationUrl, long expiresIn) {}

    public record Session(String sessionToken, long expiresAt, AuthUser user) {
        @Override public String toString() { return "Session[sessionToken=<redacted>, expiresAt=" + expiresAt + ", user=" + user + "]"; }
    }

    public DiscordAuthService() {
        this(null, null, null);
    }

    public DiscordAuthService(String discordClientId, String discordClientSecret, String encryptionKey) {
        this.database = AuthDB.get();
        this.store = new Store() {
            public RedisClient.AuthValue<String> get(String key) { return RedisClient.authGet(key); }
            public boolean set(String key, String value, int ttl) { return RedisClient.authSet(key, value, ttl); }
            public boolean delete(String key) { return RedisClient.authDelete(key); }
        };
        this.discord = new DiscordApi();
        this.guildCache = com.safjnest.guild.DiscordGuildCache.getInstance();
        this.prefixOverride = null;
        this.discordClientId = discordClientId == null || discordClientId.isBlank() ? null : discordClientId.trim();
        this.discordClientSecret = discordClientSecret == null || discordClientSecret.isBlank() ? null : discordClientSecret.trim();
        this.encryptionKey = CryptoUtils.parseEncryptionKey(encryptionKey);
    }

    DiscordAuthService(AuthDB database, Store store, DiscordApi.Transport transport, String prefix,
            String clientId, String clientSecret, String encryptionKey) {
        this(database, store, transport, prefix, clientId, clientSecret, encryptionKey, com.safjnest.guild.DiscordGuildCache.memory());
    }

    DiscordAuthService(AuthDB database, Store store, DiscordApi.Transport transport, String prefix,
            String clientId, String clientSecret, String encryptionKey, com.safjnest.guild.DiscordGuildCache guildCache) {
        this.database = database;
        this.store = store;
        this.discord = new DiscordApi(transport, com.safjnest.core.Bot::getJDA);
        this.guildCache = guildCache;
        this.prefixOverride = prefix;
        this.discordClientId = clientId;
        this.discordClientSecret = clientSecret;
        this.encryptionKey = CryptoUtils.parseEncryptionKey(encryptionKey);
    }

    public Authorization authorize(AuthContext context, String state, String redirectUri) {
        ApiKey client = context.client();
        requireConfigured();
        if (state == null || state.length() < 32 || state.length() > 256 || !state.matches("[A-Za-z0-9_-]+"))
            throw failure(HttpStatus.BAD_REQUEST, "invalid_state", "A valid random state value is required");
        if (redirectUri == null || redirectUri.length() > 2048 || !parseRedirectUris(client.redirectUris()).contains(redirectUri) || !validRedirectUri(redirectUri))
            throw failure(HttpStatus.FORBIDDEN, "redirect_not_allowed", "Callback URL is not allowed");

        try {
            database.createTransaction(CryptoUtils.sha256(state), client.id(), redirectUri,
                System.currentTimeMillis() + AUTH_TRANSACTION_TTL_SECONDS * 1000L);
        } catch (IllegalStateException exception) { throw unavailable("Authentication storage is unavailable"); }

        return discord.authorize(discordClientId, state, redirectUri, AUTH_TRANSACTION_TTL_SECONDS);
    }

    public Session exchangeSession(AuthContext context, String code, String state, String redirectUri) {
        ApiKey client = context.client();
        requireConfigured();
        if (state == null || !state.matches("[A-Za-z0-9_-]{32,256}") || code == null || code.isBlank() || code.length() > 512
                || redirectUri == null || redirectUri.length() > 2048)
            throw failure(HttpStatus.BAD_REQUEST, "invalid_request", "Code, state and redirect URI are required");
        if (!parseRedirectUris(client.redirectUris()).contains(redirectUri) || !validRedirectUri(redirectUri))
            throw failure(HttpStatus.FORBIDDEN, "redirect_not_allowed", "Callback URL is not allowed");
        try {
            if (!database.consumeTransaction(CryptoUtils.sha256(state), client.id(), redirectUri, System.currentTimeMillis()))
                throw failure(HttpStatus.UNAUTHORIZED, "invalid_state", "Login transaction is missing, expired or already used");
        } catch (IllegalStateException exception) { throw unavailable("Authentication storage is unavailable"); }

        DiscordToken token = discord.exchange(discordClientId, discordClientSecret, code, redirectUri);
        if (token.accessToken() == null || token.accessToken().isBlank() || token.expiresIn() <= 0
                || !"Bearer".equalsIgnoreCase(token.tokenType()) || token.scope() == null
                || !java.util.Arrays.asList(token.scope().split("\\s+")).containsAll(List.of("identify", "guilds")))
            throw discordFailure(HttpStatus.BAD_GATEWAY, "Discord returned an invalid OAuth token");

        AuthUser user = discord.me(token.accessToken());
        long now = System.currentTimeMillis();
        long discordExpiresAt = now + Math.min(token.expiresIn(), SESSION_TTL_SECONDS) * 1000L;
        String sessionToken = CryptoUtils.randomToken();
        long expiresAt = Math.min(now + SESSION_TTL_SECONDS * 1000L, discordExpiresAt);
        String encrypted = encrypt(token.accessToken());
        try {
            database.createSession(CryptoUtils.sha256(sessionToken), new AuthDB.Session(client.id(), user.id(), encrypted, JsonCodec.toJson(user), expiresAt));
        } catch (IllegalStateException exception) {
            throw unavailable("Authentication storage is unavailable");
        }
        return new Session(sessionToken, expiresAt, user);
    }

    public void logout(AuthContext context) {
        if (context.personal()) {
            try {
                if (!database.revokePersonalSession(context.sessionHash()))
                    throw failure(HttpStatus.UNAUTHORIZED, "invalid_session", "Personal session is revoked");
            } catch (IllegalStateException exception) { throw unavailable("Authentication storage is unavailable"); }
            guildCache.invalidate(context);
            return;
        }
        ApiKey client = context.client();
        requireConfigured();
        try {
            if (!database.revokeSession(context.sessionHash(), client.id()))
                throw failure(HttpStatus.UNAUTHORIZED, "invalid_session", "Session is missing, expired or revoked");
        } catch (IllegalStateException exception) { throw unavailable("Authentication storage is unavailable"); }
        store.delete(cachePrefix() + "session:" + context.sessionHash());
        guildCache.invalidate(context);
    }

    public AuthContext authenticate(String apiKey, String authorization, boolean sessionRequired) {
        ApiKey key = requireApiKey(apiKey);
        return switch (key.type()) {
            case PERSONAL -> {
                if (!sessionRequired)
                    throw failure(HttpStatus.FORBIDDEN, "personal_key_not_allowed", "Personal keys cannot create OAuth sessions");
                yield requirePersonalSession(key, authorization);
            }
            case GENERIC -> sessionRequired ? requireSession(key, authorization) : new AuthContext(key, null, null, 0, null);
        };
    }

    //====

    private AuthContext requirePersonalSession(ApiKey key, String authorization) {
        if (authorization != null)
            throw failure(HttpStatus.UNAUTHORIZED, "invalid_personal_key", "Use the personal key in X-API-Key without a bearer token");
        AuthUser user = JsonCodec.fromJson(key.userJson(), AuthUser.class);
        if (user == null || key.toUserId() == null || !key.toUserId().equals(user.id()))
            throw unavailable("Authentication storage is unavailable");
        return new AuthContext(key, key.id(), null, Long.MAX_VALUE, user);
    }

    private AuthContext requireSession(ApiKey client, String authorization) {
        String token = bearerToken(authorization);
        requireConfigured();
        String hash = CryptoUtils.sha256(token);
        long now = System.currentTimeMillis();
        RedisClient.AuthValue<String> cached = store.get(sessionKey(token));
        AuthDB.Session stored = cached.available() && cached.value() != null
            ? JsonCodec.fromJson(cached.value(), AuthDB.Session.class) : null;
        try {
            if (stored != null) {
                if (!database.sessionActive(hash, client.id(), now)) stored = null;
            } else {
                stored = database.findSession(hash, client.id(), now);
                if (stored != null) {
                    int ttl = (int) Math.min(CACHE_TTL_SECONDS, (stored.expiresAt() - now) / 1000);
                    if (ttl > 0) store.set(sessionKey(token), JsonCodec.toJson(stored), ttl);
                }
            }
        }
        catch (IllegalStateException exception) { throw unavailable("Authentication storage is unavailable"); }
        if (stored == null)
            throw failure(HttpStatus.UNAUTHORIZED, "invalid_session", "Session is missing, expired or revoked");
        AuthUser user = JsonCodec.fromJson(stored.userJson(), AuthUser.class);
        if (user == null) throw unavailable("Authentication storage is unavailable");
        return new AuthContext(client, hash, stored.encryptedToken(), stored.expiresAt(), user);
    }

    private ApiKey requireApiKey(String supplied) {
        ApiKey key;
        try { key = ApiKey.fromValue(database, supplied); }
        catch (IllegalArgumentException exception) { throw failure(HttpStatus.UNAUTHORIZED, "invalid_api_key", "API credentials are missing or invalid"); }
        catch (IllegalStateException exception) { throw unavailable("Authentication storage is unavailable"); }
        if (key == null)
            throw failure(HttpStatus.UNAUTHORIZED, "invalid_api_key", "API credentials are missing or invalid");
        return key;
    }

    private void requireConfigured() {
        if (discordClientId == null || discordClientSecret == null || encryptionKey == null)
            throw unavailable("Authentication is not configured");
    }

    private String encrypt(String plaintext) {
        try {
            return CryptoUtils.encrypt(plaintext, encryptionKey);
        } catch (IllegalStateException exception) {
            throw unavailable("Authentication encryption is unavailable");
        }
    }

    private static Set<String> parseRedirectUris(String configured) {
        if (configured == null || configured.isBlank()) return Set.of();
        Set<String> values = new LinkedHashSet<>();
        for (String value : configured.split(",")) {
            String uri = value.trim();
            if (!uri.isEmpty()) values.add(uri);
        }
        return Set.copyOf(values);
    }

    static boolean validRedirectUri(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null || uri.getHost() == null) return false;
            if ("https".equalsIgnoreCase(uri.getScheme())) return true;
            return "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String sessionKey(String token) {
        return keyFor("session:", token);
    }

    private String keyFor(String prefix, String token) {
        return cachePrefix() + prefix + CryptoUtils.sha256(token);
    }

    private String cachePrefix() { return prefixOverride != null ? prefixOverride : App.isTesting() ? "beebot_test:auth:" : "beebot:auth:"; }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || !authorization.substring(7).matches("[A-Za-z0-9_-]{43}"))
            throw failure(HttpStatus.UNAUTHORIZED, "invalid_session", "Bearer session token is required");
        return authorization.substring(7).trim();
    }

    private static ResponseStatusException unavailable(String message) {
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "auth_unavailable", message);
    }

    private static ResponseStatusException discordFailure(HttpStatus status, String message) {
        return failure(status, "discord_unavailable", message);
    }

    private static ResponseStatusException failure(HttpStatus status, String code, String message) {
        return new AuthException(status, code, message);
    }

}
