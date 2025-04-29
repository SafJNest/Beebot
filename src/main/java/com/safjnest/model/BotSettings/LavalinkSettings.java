package com.safjnest.model.BotSettings;

import java.util.Map;

public class LavalinkSettings {
    private Map<String, PoTokenSettings> tokens;

    private String ipv6block;
    private boolean potokenEnabled;
    private boolean rotorEnabled;

    public Map<String, PoTokenSettings> getTokens() {
        return tokens;
    }
    public void setTokens(Map<String, PoTokenSettings> tokens) {
        this.tokens = tokens;
    }
    public String getIpv6block() {
        return ipv6block;
    }
    public void setIpv6block(String ipv6block) {
        this.ipv6block = ipv6block;
    }
    public boolean isPotokenEnabled() {
        return potokenEnabled;
    }
    public void setPotokenEnabled(boolean potokenEnabled) {
        this.potokenEnabled = potokenEnabled;
    }
    public boolean isRotorEnabled() {
        return rotorEnabled;
    }
    public void setRotorEnabled(boolean rotorEnabled) {
        this.rotorEnabled = rotorEnabled;
    }
}