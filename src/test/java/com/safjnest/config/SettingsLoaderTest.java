package com.safjnest.config;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SettingsLoaderTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void loadsOneConfigurationWithServiceCredentialsAndRedactsSecrets() throws java.lang.Exception {
        Path directory = temporary.getRoot().toPath();
        Path credentials = temporary.newFolder("credentials").toPath();
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        Files.writeString(credentials.resolve("bot-token"), "sample-bot-token\r\n");
        Files.writeString(credentials.resolve("db-password"), "sample-password\n");
        Files.writeString(credentials.resolve("aes-key"), key);
        Files.writeString(directory.resolve("beebot.yml"), "unused: true\n");
        Path canary = directory.resolve("canary.yml");
        Files.writeString(canary, """
            runtime:
              testing: true
              host: canary
              bot: canary
            api:
              port: 8096
              discord:
                clientId: "123"
                clientSecret: sample-oauth
                encryptionKey: "credential:aes-key"
            bot:
              beebot:
                discordToken: "credential:unused-token"
              canary:
                discordToken: "credential:bot-token"
                embedColor: "#FFC645"
                coOwnersIds:
                  - "123456789012345678"
            services:
              database:
                host: localhost
                username: local
                databaseName: canary
                password: "credential:db-password"
              r2:
                enabled: false
            """);

        Settings settings = SettingsLoader.load(directory, Map.of("BEEBOT_CONFIG", canary.toString(),
            "CREDENTIALS_DIRECTORY", "credentials", "BEEBOT_CREDENTIALS_DIRECTORY", "unused"));

        assertTrue(settings.runtime().testing());
        assertEquals("canary", settings.runtime().bot());
        assertEquals("canary", settings.botDatabaseName());
        assertEquals(Integer.valueOf(8096), settings.api().port());
        assertEquals(key, settings.api().discord().encryptionKey());
        assertEquals("sample-bot-token", settings.bot().getDiscordToken());
        assertEquals("sample-password", settings.services().getDatabase().getPassword());
        assertEquals(0xffc645, settings.bot().getEmbedColor().getRGB() & 0xffffff);
        assertEquals("123456789012345678", settings.bot().getCoOwnersIds().getFirst());

        String serialized = new ObjectMapper().writeValueAsString(settings);
        for (String secret : new String[] {key, "sample-oauth", "sample-bot-token", "sample-password"}) {
            assertFalse(serialized.contains(secret));
            assertFalse(settings.toString().contains(secret));
        }
    }

    @Test
    public void readsExistingDeployFilesAndUsesEnvironmentForAbsentApiProperties() throws java.lang.Exception {
        Path directory = temporary.getRoot().toPath();
        Files.createDirectory(directory.resolve("rsc"));
        Files.writeString(directory.resolve("config.properties"), "testing=true\nbot=canary\nhost=home\n");
        Files.writeString(directory.resolve("spring.properties"), "server.port=8096\nCDN_URL=\nDISCORD_CLIENT_ID=file-client\n");
        Files.writeString(directory.resolve("rsc/settings.json"), """
            {
              "settings": {
                "database": { "databaseName": "prod" }, "testDatabase": { "databaseName": "canary" },
                "waitingTime": "1", "riot": { "lolVersion": "old" }, "openAI": { "key": "credential:unused" }
              },
              "bots": { "prod": { "discordToken": "prod-token" }, "canary": { "discordToken": "canary-token" } }
            }
            """);

        Settings settings = SettingsLoader.load(directory, Map.of("CDN_URL", "https://cdn.example", "DISCORD_CLIENT_ID", "env-client"));

        assertEquals("canary", settings.botDatabaseName());
        assertEquals("home", settings.runtime().host());
        assertEquals("canary", settings.runtime().bot());
        assertEquals("canary-token", settings.bot().getDiscordToken());
        assertEquals("https://cdn.example", settings.api().cdnUrl());
        assertEquals("file-client", settings.api().discord().clientId());
    }

    @Test
    public void rejectsMissingAndInvalidCredentialsWithoutPrintingTheirValue() throws java.lang.Exception {
        Path directory = temporary.getRoot().toPath();
        Path file = directory.resolve("beebot.yml");
        Map<String, String> environment = Map.of("BEEBOT_CREDENTIALS_DIRECTORY", "credentials");
        Files.writeString(file, """
            runtime:
              bot: beebot
            bot:
              beebot:
                discordToken: "credential:missing-token"
            services: {}
            """);
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, environment));
        assertEquals("Could not read credential: missing-token", missing.getMessage());

        Files.writeString(file, """
            runtime:
              bot: beebot
            bot:
              beebot:
                discordToken: "credential:../private-token"
            services: {}
            """);
        IllegalStateException invalid = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, environment));
        assertEquals("Invalid credential reference", invalid.getMessage());

        Files.writeString(file, """
            runtime:
              bot: beebot
            api:
              port: private-value
            bot:
              beebot:
                discordToken: private-value
            services: {}
            """);
        IllegalStateException malformed = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, environment));
        assertEquals("Invalid configuration section: Beebot configuration (api.port)", malformed.getMessage());
        assertNull(malformed.getCause());
    }

    @Test
    public void prefersTheRootYamlAndRejectsInvalidDocuments() throws java.lang.Exception {
        Path directory = temporary.getRoot().toPath();
        Path file = directory.resolve("beebot.yml");
        String configuration = """
            runtime:
              bot: beebot
            bot:
              beebot:
                discordToken: sample-token
              canary:
                discordToken: sample-canary-token
            services:
              database:
                host: localhost
                username: local
                databaseName: canary
            """;

        Files.writeString(file, configuration);
        assertEquals("canary", SettingsLoader.load(directory, Map.of()).botDatabaseName());
        assertEquals("sample-token", SettingsLoader.load(directory, Map.of()).bot().getDiscordToken());

        Files.writeString(file, configuration.replace("bot: beebot", "bot: canary"));
        assertEquals("sample-canary-token", SettingsLoader.load(directory, Map.of()).bot().getDiscordToken());

        Files.writeString(file, configuration.replace("bot: beebot", "bot: absent"));
        IllegalStateException absent = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, Map.of()));
        assertEquals("Missing configuration section: bot.absent", absent.getMessage());

        Files.writeString(file, configuration.replace("runtime:\n  bot: beebot", "runtime: {}"));
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, Map.of()));
        assertEquals("Missing or invalid runtime.bot", missing.getMessage());

        Files.writeString(file, configuration + "bogus: private-value\n");
        IllegalStateException unknown = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, Map.of()));
        assertEquals("Invalid configuration section: Beebot configuration (bogus)", unknown.getMessage());

        Files.writeString(file, configuration + "bot: {}\n");
        IllegalStateException duplicate = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, Map.of()));
        assertEquals("Could not read configuration from " + file, duplicate.getMessage());

        Files.writeString(file, configuration + "---\napi: {}\n");
        IllegalStateException trailing = assertThrows(IllegalStateException.class, () -> SettingsLoader.load(directory, Map.of()));
        assertEquals("Could not read configuration from " + file, trailing.getMessage());
    }
}
