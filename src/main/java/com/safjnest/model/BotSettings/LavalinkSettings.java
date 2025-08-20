package com.safjnest.model.BotSettings;

import java.util.Map;
import lombok.Data;

@Data
public class LavalinkSettings {
    private Map<String, PoTokenSettings> tokens;

    private String ipv6block;
    private boolean potokenEnabled;
    private boolean rotorEnabled;
}