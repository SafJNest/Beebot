package com.safjnest.config;

import java.nio.file.Path;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Settings(Runtime runtime, Api api, BotSettings bot, ServiceSettings services) {
    public Settings {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(bot, "bot");
        Objects.requireNonNull(services, "services");

        if (api == null)
            api = new Api(null, null, null);
    }

    public static Settings get() {
        return Loaded.instance;
    }

    public String botDatabaseName() {
        DatabaseSettings database = runtime.testing() ? services.getTestDatabase() : null;
        if (database == null)
            database = services.getDatabase();

        return database.getDatabaseName();
    }

    public record Runtime(boolean testing, String host, String bot) {}

    public record Api(Integer port, String cdnUrl, Discord discord) {
        public Api {
            if (port == null)
                port = 8080;
            if (port < 0 || port > 65535)
                throw new IllegalArgumentException("Invalid API port");
        }
    }

    public record Discord(String clientId,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String clientSecret,
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String encryptionKey,
            String allowedRedirectUris) {
        @Override
        public String toString() {
            return "Discord[clientId=" + clientId + "]";
        }
    }

    private static final class Loaded {
        private static final Settings instance = SettingsLoader.load(Path.of("."), System.getenv());
    }
}
