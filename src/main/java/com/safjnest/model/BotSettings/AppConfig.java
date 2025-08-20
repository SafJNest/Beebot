package com.safjnest.model.BotSettings;

import lombok.Data;

@Data
public class AppConfig {
    private boolean testing;
    private String bot;
    private String host;
}