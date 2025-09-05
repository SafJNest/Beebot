package com.safjnest.model.BotSettings;
import lombok.Data;

@Data
public class DatabaseSettings {
    private String host;
    private String port;
    private String databaseName;
    private String username;
    private String password;
}
