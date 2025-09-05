package com.safjnest.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.model.BotSettings.AppConfig;
import com.safjnest.model.BotSettings.BotSettings;
import com.safjnest.model.BotSettings.JsonSettings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class SettingsLoader {
    private static final String settingsPath = "rsc" + File.separator + "settings.json";
    private static final String configPath = "config.properties";

    private static com.safjnest.model.BotSettings.Settings settings;

    public static AppConfig loadConfig(String filePath) throws IOException {
        Properties properties = new Properties();
        properties.load(new FileInputStream(filePath));

        AppConfig loadedConfig = new AppConfig();
        loadedConfig.setTesting(Boolean.parseBoolean(properties.getProperty("testing")));
        loadedConfig.setBot(properties.getProperty("bot"));
        loadedConfig.setHost(properties.getProperty("host"));

        return loadedConfig;
    }

    public static JsonSettings loadJsonSettings(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File(filePath));
        
        JsonNode settingsNode = rootNode.get("settings");
        if (settingsNode == null) {
            throw new IllegalStateException("Missing 'settings' object in settings file");
        }
        return mapper.treeToValue(settingsNode, JsonSettings.class);
    }

    public static BotSettings loadBotSettings(String filePath, String botName) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File(filePath));
        
        JsonNode botsNode = rootNode.get("bots");
        if (botsNode == null) {
            throw new IllegalStateException("Missing 'bots' object in settings file");
        }
        JsonNode botNode = botsNode.get(botName);
        return mapper.treeToValue(botNode, BotSettings.class);
    }

    public static void loadSettings(String settingsPath, String configPath) throws IOException {
        AppConfig config = loadConfig(configPath);
        JsonSettings jsonSettings = loadJsonSettings(settingsPath);
        BotSettings botSettings = loadBotSettings(settingsPath, config.getBot());
        settings = new com.safjnest.model.BotSettings.Settings(config, jsonSettings, botSettings);
    }

    public static com.safjnest.model.BotSettings.Settings getSettings() {
        if (settings == null) {
            try {
                loadSettings(settingsPath, configPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load settings", e);
            }
        }
        return settings;
    }
}
