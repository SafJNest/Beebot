package com.safjnest.model.BotSettings;

import lombok.Data;

@Data
public class OpenAISettings {
    private String key;
    private Integer maxTokens;
    private String model;
}
