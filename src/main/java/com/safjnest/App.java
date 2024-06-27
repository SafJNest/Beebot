package com.safjnest;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Properties;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.core.audio.tts.TTSHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.safjnest.util.LOL.RiotHandler;
import com.safjnest.util.Twitch.TwitchClient;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.impl.R4J;

@SpringBootApplication
public class App {
    private static TTSHandler tts;
    private static R4J riotApi;
    public static String key;

    private static Properties properties;

    private static Bot extreme_safj_beebot;

    /**
     * Insane beebot core
     */
    private static boolean EXTREME_TESTING;

    public static boolean isExtremeTesting() {
        return EXTREME_TESTING;
    }

    public static TTSHandler getTTS() {
        return tts;
    }

    public static R4J getRiotApi() {
        return riotApi;
    }


    public static void main(String args[]) {
        
        SafJNest.bee();
        new BotLogger("Beebot", null);


        EXTREME_TESTING = getPropertyAsBoolean("testing");
        if (EXTREME_TESTING) BotLogger.info("Beebot is in testing mode");
        else BotLogger.info("Beebot is in normal mode");

        if (!EXTREME_TESTING) {
            SpringApplication app = new SpringApplication(App.class);
            app.setDefaultProperties(Collections.singletonMap("server.port", "8096"));
            //app.run(args);
        }
        

        SecureRandom secureRandom = new SecureRandom();
        BotLogger.info("[System]: System Entropy: " + secureRandom.getProvider());
        
        JSONParser parser = new JSONParser();
        JSONObject settings = null, SQLSettings = null, riotSettings = null, twitchSettings = null;
        try (Reader reader = new FileReader("rsc" + File.separator + "settings.json")) {
            settings = (JSONObject) parser.parse(reader);
            settings = (JSONObject) settings.get("settings");
            SQLSettings = (JSONObject) settings.get("MariaDB");
            twitchSettings = (JSONObject) settings.get("Twitch");
            if (App.isExtremeTesting()) {
                SQLSettings = (JSONObject) settings.get("LocalHost");
            }
            riotSettings = (JSONObject) settings.get("Riot");
        } catch (Exception e) {
            e.printStackTrace();
        }
        tts = new TTSHandler(settings.get("ttsApiKey").toString());
        
        new DatabaseHandler(
            SQLSettings.get("HostName").toString(), 
            SQLSettings.get("database").toString(), 
            SQLSettings.get("user").toString(), 
            SQLSettings.get("password").toString()
        );

        new SoundHandler();

        
        new TwitchClient(
            twitchSettings.get("clientId").toString(), 
            twitchSettings.get("clientSecret").toString()
        );
        
        if (!App.isExtremeTesting()) TwitchClient.init();

        //TwitchConduit.registerSubEvent("126371014"); //Sunny314_
        //TwitchClient.registerSubEvent("164078841"); //leon4117

        
        riotApi = null;
        try {
            riotApi = new R4J(new APICredentials(riotSettings.get("riotKey").toString()));
            BotLogger.info("[R4J] Connection Successful!");
        } catch (Exception e) {
            BotLogger.error("[R4J] Annodam Not Successful!");
        }
        
        new RiotHandler(riotApi, riotSettings.get("lolVersion").toString());

        BotLogger.info("[CANNUCCIA] " + DatabaseHandler.getCannuccia());
        BotLogger.info("[EPRIA] ID " + PermissionHandler.getEpria());

        extreme_safj_beebot = new Bot();
        extreme_safj_beebot.il_risveglio_della_bestia();
    }

    public static void shutdown() {
        BotLogger.trace("Shutting down the bot");
        extreme_safj_beebot.distruzione_demoniaca();
    }

    public static void restart() {
        BotLogger.trace("Restarting the bot");
        extreme_safj_beebot.distruzione_demoniaca();
        extreme_safj_beebot.il_risveglio_della_bestia();
    }

    public static String getProperty(String key) {
        if (properties == null) propertiesLoader();
        return properties.getProperty(key);
    }

    public static boolean getPropertyAsBoolean(String key) {
        return Boolean.parseBoolean(getProperty(key));
    }

    private static void propertiesLoader() {
        properties = new Properties();
        try {
            properties.load(new FileReader("config.properties"));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}