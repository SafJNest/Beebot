package com.safjnest.model.BotSettings;

import lombok.Data;

@Data
public class SpotifySettings {
    private String clientId;
    private String clientSecret;
    private String spdc;
    private String countryCode;
}
