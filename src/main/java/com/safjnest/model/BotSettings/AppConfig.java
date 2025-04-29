package com.safjnest.model.BotSettings;

public class AppConfig {
    private boolean testing;
    private String bot;
    private String host;

    public boolean isTesting() {
        return testing;
    }
    public void setTesting(boolean testing) {
        this.testing = testing;
    }
    public String getBot() {
        return bot;
    }
    public void setBot(String bot) {
        this.bot = bot;
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
}