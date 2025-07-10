package com.safjnest.model.BotSettings;
import lombok.Data;

@Data
public class JsonSettings {
    private AWSSettings amazonAWS;
    private DatabaseSettings postgreSQL;
    private DatabaseSettings mariaDB;
    private DatabaseSettings localHost;
    private DatabaseSettings database;
    private DatabaseSettings testDatabase;
    private DatabaseSettings testWebsiteDatabase;
    private DatabaseSettings leagueDatabase;
    private OpenAISettings openAI;
    private RiotSettings riot;
    private TwitchSettings twitch;
    private LavalinkSettings lavalink;
    private SpotifySettings spotify;

    private String youtubeApiKey;
    private String ttsApiKey;
    private String weatherApiKey;
    private String nasaApiKey;
    private String waitingTime;
}
