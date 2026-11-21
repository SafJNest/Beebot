package com.safjnest.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringJoiner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.safjnest.utils.CryptoUtils;

final class SettingsLoader {
    private static final String DEFAULT_CONFIG = "beebot.yml";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final String CREDENTIAL_REFERENCE = "credential:";

    private SettingsLoader() {}

    static Settings load(Path directory, Map<String, String> environment) {
        String config = environment.get("BEEBOT_CONFIG");
        Path source = directory.resolve(blank(config) ? DEFAULT_CONFIG : config);
        if (blank(config) && Files.notExists(source))
            return loadLegacy(directory, environment);

        ObjectNode document = readObject(source, YAML);
        JsonNode profile = document.path("runtime").path("bot");
        if (!profile.isTextual() || blank(profile.textValue()))
            throw new IllegalStateException("Missing or invalid runtime.bot");

        String botPath = "bot." + profile.textValue();
        JsonNode bot = document.path("bot").path(profile.textValue());
        if (!bot.isObject())
            throw new IllegalStateException("Missing configuration section: " + botPath);

        document.set("bot", bot);
        Path credentials = credentialDirectory(directory, environment);
        Settings settings = bind(resolveCredentials(document, credentials), Settings.class, "Beebot configuration");

        if (blank(settings.bot().getDiscordToken()))
            throw new IllegalStateException("Missing " + botPath + ".discordToken");

        DatabaseSettings database = settings.services().getDatabase();
        if (database == null || blank(database.getHost()) || blank(database.getUsername()) || blank(settings.botDatabaseName()))
            throw new IllegalStateException("services.database requires host, username and the active databaseName");

        Settings.Discord discord = settings.api().discord();
        if (discord != null && (blank(discord.clientId()) || blank(discord.clientSecret())
                || CryptoUtils.parseEncryptionKey(discord.encryptionKey()) == null))
            throw new IllegalStateException("api.discord requires clientId, clientSecret and encryptionKey (32 Base64-encoded bytes)");

        return settings;
    }

    private static Settings loadLegacy(Path directory, Map<String, String> environment) {
        JsonNode document = readObject(directory.resolve("rsc/settings.json"), JSON);
        Path credentials = credentialDirectory(directory, environment);
        Properties runtime = readProperties(directory.resolve("config.properties"), true);
        Properties api = readProperties(directory.resolve("spring.properties"), false);

        String botName = runtime.getProperty("bot");
        if (blank(botName))
            throw new IllegalStateException("Missing bot in config.properties");

        JsonNode serviceData = document.path("settings");
        if (serviceData instanceof ObjectNode fields) {
            fields.remove(List.of("waitingTime", "openAI"));
            if (fields.get("riot") instanceof ObjectNode riot)
                riot.remove("lolVersion");
        }

        ServiceSettings services = bind(resolveCredentials(serviceData, credentials), ServiceSettings.class, "settings");
        BotSettings bot = bind(resolveCredentials(document.path("bots").path(botName), credentials), BotSettings.class, "selected bot");
        Settings.Runtime app = new Settings.Runtime(Boolean.parseBoolean(runtime.getProperty("testing")), runtime.getProperty("host"), botName);
        if (app.testing() && (services.getTestDatabase() == null || blank(services.getTestDatabase().getDatabaseName())))
            throw new IllegalStateException("Missing settings.testDatabase.databaseName for legacy test mode");

        Settings.Discord discord = new Settings.Discord(
            property(api, environment, "DISCORD_CLIENT_ID", credentials),
            property(api, environment, "DISCORD_CLIENT_SECRET", credentials),
            property(api, environment, "AUTH_ENCRYPTION_KEY", credentials),
            property(api, environment, "AUTH_ALLOWED_REDIRECT_URIS", credentials));

        return new Settings(app, new Settings.Api(port(api), property(api, environment, "CDN_URL", credentials), discord), bot, services);
    }

    private static ObjectNode readObject(Path source, ObjectMapper mapper) {
        try (InputStream input = Files.newInputStream(source)) {
            if (!(mapper.readTree(input) instanceof ObjectNode document))
                throw new IllegalStateException("Expected a configuration object in " + source);

            return document;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read configuration from " + source);
        }
    }

    private static Properties readProperties(Path source, boolean required) {
        Properties properties = new Properties();
        if (!required && Files.notExists(source))
            return properties;

        try (InputStream input = Files.newInputStream(source)) {
            properties.load(input);

            return properties;
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalStateException("Could not read configuration from " + source);
        }
    }

    private static <T> T bind(JsonNode value, Class<T> type, String section) {
        if (!value.isObject())
            throw new IllegalStateException("Missing configuration section: " + section);

        try {
            return JSON.treeToValue(value, type);
        } catch (JsonMappingException failure) {
            StringJoiner path = new StringJoiner(".");
            for (JsonMappingException.Reference field : failure.getPath())
                path.add(field.getFieldName() == null ? Integer.toString(field.getIndex()) : field.getFieldName());

            String location = path.length() == 0 ? section : section + " (" + path + ")";
            throw new IllegalStateException("Invalid configuration section: " + location);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid configuration section: " + section);
        }
    }

    private static Integer port(Properties properties) {
        try {
            return Integer.valueOf(properties.getProperty("server.port", "8080"));
        } catch (NumberFormatException failure) {
            throw new IllegalStateException("Invalid server.port in spring.properties");
        }
    }

    private static String property(Properties properties, Map<String, String> environment, String name, Path credentials) {
        String value = properties.getProperty(name);

        return resolveCredential(blank(value) ? environment.get(name) : value, credentials);
    }

    private static Path credentialDirectory(Path directory, Map<String, String> environment) {
        for (String name : List.of("CREDENTIALS_DIRECTORY", "BEEBOT_CREDENTIALS_DIRECTORY")) {
            String path = environment.get(name);
            if (!blank(path))
                return directory.resolve(path);
        }

        return null;
    }

    private static JsonNode resolveCredentials(JsonNode node, Path directory) {
        if (node instanceof ObjectNode object) {
            for (Map.Entry<String, JsonNode> field : object.properties())
                field.setValue(resolveCredentials(field.getValue(), directory));
        } else if (node instanceof ArrayNode array) {
            for (int index = 0; index < array.size(); index++)
                array.set(index, resolveCredentials(array.get(index), directory));
        } else if (node.isTextual()) {
            String value = node.asText();
            if (value.startsWith(CREDENTIAL_REFERENCE))
                return TextNode.valueOf(resolveCredential(value, directory));
        }

        return node;
    }

    private static String resolveCredential(String value, Path directory) {
        if (value == null || !value.startsWith(CREDENTIAL_REFERENCE))
            return value;

        String name = value.substring(CREDENTIAL_REFERENCE.length());
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]*"))
            throw new IllegalStateException("Invalid credential reference");
        if (directory == null)
            throw new IllegalStateException("Missing credentials directory for " + name);

        try {
            String secret = Files.readString(directory.resolve(name)).replaceFirst("\\r?\\n\\z", "");
            if (secret.isBlank())
                throw new IllegalStateException("Empty credential: " + name);

            return secret;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read credential: " + name);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
