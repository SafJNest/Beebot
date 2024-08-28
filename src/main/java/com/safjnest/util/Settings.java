package com.safjnest.util;

import java.awt.Color;

import net.dv8tion.jda.api.entities.Activity;

public class Settings {

    public String prefix;
    public Activity activity;
    public String token;
    public Color color;
    public String ownerID;
    public String[] coOwnersIDs;
    public String helpWord;
    public Integer maxPrime;
    public String weatherApiKey;
    public String nasaApiKey;
    public Integer maxFreePlaylists;
    public Integer maxFreePlaylistSize;
    public Integer maxPremiumPlaylists;
    public Integer maxPremiumPlaylistSize;

    public Settings(SettingsLoader settingsLoader) {
        this.prefix = settingsLoader.getPrefix();
        this.activity = settingsLoader.getActivity();
        this.token = settingsLoader.getDiscordToken();
        this.color = settingsLoader.getEmbedColor();
        this.ownerID = settingsLoader.getOwnerID();
        this.coOwnersIDs = settingsLoader.getCoOwnerIDs();
        this.helpWord = settingsLoader.getHelpWord();
        this.maxPrime = settingsLoader.getMaxPrime();
        this.weatherApiKey = settingsLoader.getWeatherAPIKey();
        this.nasaApiKey = settingsLoader.getNasaApiKey();
        this.maxFreePlaylists = settingsLoader.getMaxFreePlaylists();
        this.maxFreePlaylistSize = settingsLoader.getMaxFreePlaylistSize();
        this.maxPremiumPlaylists = settingsLoader.getMaxPremiumPlaylists();
        this.maxPremiumPlaylistSize = settingsLoader.getMaxPremiumPlaylistSize();
    }

}
