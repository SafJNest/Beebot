package com.safjnest.model.BotSettings;

import java.awt.Color;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.safjnest.model.util.ColorDeserializer;

import lombok.Data;

@Data
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
}
