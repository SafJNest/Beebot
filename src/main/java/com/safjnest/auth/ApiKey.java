package com.safjnest.auth;

import java.util.Objects;
import java.util.UUID;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.sql.database.AuthDB;
import com.safjnest.utils.CryptoUtils;
import com.safjnest.utils.JsonCodec;

public final class ApiKey {
    public enum Type { GENERIC, PERSONAL }

    private final String id;
    private final String name;
    private final String redirectUris;
    private final Type type;
    private final String discordId;
    private final String userJson;
    private final String value;
    private final String hash;

    public ApiKey(String id, String name, String redirectUris) {
        this(id, name, redirectUris, Type.GENERIC, null, null, null, null);
    }

    public ApiKey(String id, String name, String redirectUris, Type type, String discordId, String userJson, String value, String hash) {
        this.id = id;
        this.name = name;
        this.redirectUris = redirectUris;
        this.type = Objects.requireNonNull(type);
        this.discordId = discordId;
        this.userJson = userJson;
        this.value = value;
        this.hash = hash;
    }

    public static ApiKey generate(String name, String redirectUris, AuthUser user) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid API key name");
        if (user == null && (redirectUris == null || redirectUris.isBlank())) throw new IllegalStateException("Callback URLs are not configured");
        if (user != null && (user.id() == null || !user.id().matches("[1-9][0-9]{0,19}") || user.username() == null || user.username().isBlank()))
            throw new IllegalArgumentException("A verified Discord user is required");
        String value = CryptoUtils.randomToken();
        return new ApiKey(UUID.randomUUID().toString(), name, user == null ? redirectUris : "", user == null ? Type.GENERIC : Type.PERSONAL,
            user == null ? null : user.id(), user == null ? null : JsonCodec.toJson(user), value, CryptoUtils.sha256(value));
    }

    public static ApiKey fromHash(AuthDB database, String hash) { return database.findClient(hash); }

    public static ApiKey fromValue(AuthDB database, String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid API key");
        ApiKey stored = fromHash(database, CryptoUtils.sha256(value));
        return stored == null ? null : new ApiKey(stored.id, stored.name, stored.redirectUris, stored.type,
            stored.discordId, stored.userJson, value, stored.hash);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String redirectUris() { return redirectUris; }
    public Type type() { return type; }
    public String toUserId() { return discordId; }
    public String userJson() { return userJson; }
    public String value() { return value; }
    public String toHash() { return hash; }
    @Override public String toString() { return "ApiKey[id=" + id + ", type=" + type + ", value=<redacted>]"; }
}
