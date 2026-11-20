package com.safjnest.config;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceSettings {
    private AWSSettings amazonAWS;
    private R2Settings r2;
    private DatabaseSettings postgreSQL;
    private DatabaseSettings mariaDB;
    private DatabaseSettings localHost;
    private DatabaseSettings database;
    private DatabaseSettings testDatabase;
    private DatabaseSettings testWebsiteDatabase;
    private DatabaseSettings leagueDatabase;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String mongo;
    private DatabaseSettings spotifyDatabase;
    private RiotSettings riot;
    private TwitchSettings twitch;
    private LavalinkSettings lavalink;
    private SpotifySettings spotify;
    private SpotifyApiSettings spotifyApi;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String youtubeApiKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String ttsApiKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String weatherApiKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String nasaApiKey;
}
