package com.safjnest.util;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.awt.Color;
import java.text.MessageFormat;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import net.dv8tion.jda.api.entities.Activity;

public class SettingsLoader {
    private static final String path = "rsc" + File.separator + "settings.json";

    private final String bot;
    private final String database;

    private final JSONObject settings;

    public SettingsLoader(String bot, String database) {
        this.bot = bot;
        this.database = database;

        JSONObject settings = null;

        JSONParser parser = new JSONParser();
        try (Reader reader = new FileReader(path)) {
            settings = (JSONObject) parser.parse(reader);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            this.settings = settings;
        }
        
    }

    private static String[] toStringArray(JSONArray array) {
        if(array==null)
            return new String[0];
        
        String[] arr = new String[array.size()];
        for(int i = 0; i < arr.length; i++)
            arr[i] = (String) array.get(i);
        return arr;
    }

    private JSONObject getDiscordSettings() {
        return (JSONObject) settings.get(bot);
    }

    private JSONObject getBotSettings() {
        return (JSONObject) settings.get("settings");
    }

    private JSONObject getDatabaseSettings() {
        return (JSONObject) getBotSettings().get(database);
    }

    private JSONObject getTwitchSettings() {
        return (JSONObject) getBotSettings().get("Twitch");
    }

    private JSONObject getRiotSettings() {
        return (JSONObject) getBotSettings().get("Riot");
    }

    private JSONObject getLavalinkSettings() {
        return (JSONObject) getBotSettings().get("Lavalink");
    }

    public String getPrefix() {
        return getDiscordSettings().get("prefix").toString();
    }

    public Activity getActivity() {
        return Activity.playing(MessageFormat.format(getDiscordSettings().get("activity").toString().replace("{0}", getPrefix()), getPrefix()));
    }

    public String getDiscordToken() {
        return getDiscordSettings().get("discordToken").toString();
    }

    public Color getEmbedColor() {
        return Color.decode(getDiscordSettings().get("embedColor").toString());
    }

    public String getOwnerID() {
        return getDiscordSettings().get("ownerID").toString();
    }

    public String[] getCoOwnerIDs() {
        return toStringArray((JSONArray) getDiscordSettings().get("coOwnersIDs"));
    }

    public String getHelpWord() {
        return getDiscordSettings().get("helpWord").toString();
    }

    public Integer getMaxPrime() {
        return Integer.valueOf(getDiscordSettings().get("maxPrime").toString());
    }

    public String getWeatherAPIKey() {
        return getBotSettings().get("weatherApiKey").toString();
    }

    public String getNasaApiKey() {
        return getBotSettings().get("nasaApiKey").toString();
    }

    public String getInfo() {
        return getDiscordSettings().get("info").toString();
    }

    public String getTTSApiKey() {
        return getBotSettings().get("ttsApiKey").toString();
    }

    public String getDBHostname() {
        return getDatabaseSettings().get("HostName").toString();
    }

    public String getDBName() {
        return getDatabaseSettings().get("database").toString();
    }

    public String getDBUser() {
        return getDatabaseSettings().get("user").toString();
    }

    public String getDBPassword() {
        return getDatabaseSettings().get("password").toString();
    }

    public String getTwitchClientId() {
        return getTwitchSettings().get("clientId").toString();
    }

    public String getTwitchClientSecret() {
        return getTwitchSettings().get("clientSecret").toString();
    }

    public String getRiotKey() {
        return getRiotSettings().get("riotKey").toString();
    }

    public String getLOLVersion() {
        return getRiotSettings().get("lolVersion").toString();
    }

    public String getLavalinkHost() {
        return getLavalinkSettings().get("host").toString();
    }

    public String getPoToken() {
        return getLavalinkSettings().get("potoken").toString();
    }

    public String getVisitorData() {
        return getLavalinkSettings().get("visitordata").toString();
    }

    public String getLavalinkPassword() {
        return getLavalinkSettings().get("password").toString();
    }
}
