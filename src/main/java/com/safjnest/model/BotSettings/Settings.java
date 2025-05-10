package com.safjnest.model.BotSettings;

import java.util.Properties;

import com.safjnest.App;

public class Settings {
    private AppConfig config;
    private JsonSettings jsonSettings;
    private BotSettings botSettings;

    public Settings(AppConfig config, JsonSettings jsonSettings, BotSettings botSettings) {
        this.config = config;
        this.jsonSettings = jsonSettings;
        this.botSettings = botSettings;
    }

    public AppConfig getConfig() {
        return config;
    }
    public void setConfig(AppConfig config) {
        this.config = config;
    }
    public JsonSettings getJsonSettings() {
        return jsonSettings;
    }
    public void setJsonSettings(JsonSettings jsonSettings) {
        this.jsonSettings = jsonSettings;
    }
    public BotSettings getBotSettings() {
        return botSettings;
    }
    public void setBotSettings(BotSettings botSettings) {
        this.botSettings = botSettings;
    }

}
