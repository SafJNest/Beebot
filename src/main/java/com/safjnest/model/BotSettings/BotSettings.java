package com.safjnest.model.BotSettings;

import java.awt.Color;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.safjnest.model.util.ColorDeserializer;

public class BotSettings {
    private String name;
    private String discordToken;
    @JsonDeserialize(using = ColorDeserializer.class)
    private Color embedColor;
    private String prefix;
    private String activity;
    private String ownerId;
    private List<String> coOwnersIds;
    private String helpWord;
    private Integer maxPrime;
    private Integer maxFreePlaylists;
    private Integer maxFreePlaylistSize;
    private Integer maxPremiumPlaylists;
    private Integer maxPremiumPlaylistSize;
    private String info;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getDiscordToken() {
        return discordToken;
    }
    public void setDiscordToken(String discordToken) {
        this.discordToken = discordToken;
    }
    public Color getEmbedColor() {
        return embedColor;
    }
    public void setEmbedColor(Color embedColor) {
        this.embedColor = embedColor;
    }
    public String getPrefix() {
        return prefix;
    }
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    public String getActivity() {
        return activity;
    }
    public void setActivity(String activity) {
        this.activity = activity;
    }
    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    public List<String> getCoOwnersIds() {
        return coOwnersIds;
    }
    public void setCoOwnersIds(List<String> coOwnersIds) {
        this.coOwnersIds = coOwnersIds;
    }
    public String getHelpWord() {
        return helpWord;
    }
    public void setHelpWord(String helpWord) {
        this.helpWord = helpWord;
    }
    public Integer getMaxPrime() {
        return maxPrime;
    }
    public void setMaxPrime(Integer maxPrime) {
        this.maxPrime = maxPrime;
    }
    public Integer getMaxFreePlaylists() {
        return maxFreePlaylists;
    }
    public void setMaxFreePlaylists(Integer maxFreePlaylists) {
        this.maxFreePlaylists = maxFreePlaylists;
    }
    public Integer getMaxFreePlaylistSize() {
        return maxFreePlaylistSize;
    }
    public void setMaxFreePlaylistSize(Integer maxFreePlaylistSize) {
        this.maxFreePlaylistSize = maxFreePlaylistSize;
    }
    public Integer getMaxPremiumPlaylists() {
        return maxPremiumPlaylists;
    }
    public void setMaxPremiumPlaylists(Integer maxPremiumPlaylists) {
        this.maxPremiumPlaylists = maxPremiumPlaylists;
    }
    public Integer getMaxPremiumPlaylistSize() {
        return maxPremiumPlaylistSize;
    }
    public void setMaxPremiumPlaylistSize(Integer maxPremiumPlaylistSize) {
        this.maxPremiumPlaylistSize = maxPremiumPlaylistSize;
    }
    public String getInfo() {
        return info;
    }
    public void setInfo(String info) {
        this.info = info;
    }
}
