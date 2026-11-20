package com.safjnest.config;

import java.awt.Color;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.safjnest.utils.ColorDeserializer;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BotSettings {
    private String name;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
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
}
