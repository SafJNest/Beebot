package com.safjnest.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpotifySettings {
    private String clientId;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String clientSecret;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String spdc;
    private String countryCode;
}
