package com.safjnest;

import java.io.FileReader;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.core.audio.SoundHandler;
import com.safjnest.core.audio.tts.TTSHandler;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.safjnest.util.SettingsLoader;
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

        SettingsLoader settingsLoader = new SettingsLoader(
            App.isExtremeTesting() ? App.getProperty("bot") : "beebot",
            App.isExtremeTesting() ? "LocalHost" : "MariaDB"
        );

        tts = new TTSHandler(settingsLoader.getTTSApiKey());
        
        new DatabaseHandler(
            settingsLoader.getDBHostname(),
            settingsLoader.getDBName(),
            settingsLoader.getDBUser(),
            settingsLoader.getDBPassword()
        );

        new SoundHandler();
        
        new TwitchClient(
            settingsLoader.getTwitchClientId(),
            settingsLoader.getTwitchClientSecret()
        );
        
        if(!isExtremeTesting()) {
            TwitchClient.init();
        }

        riotApi = null;
        try {
            riotApi = new R4J(new APICredentials(settingsLoader.getRiotKey()));
            BotLogger.info("[R4J] Connection Successful!");
        } catch (Exception e) {
            BotLogger.error("[R4J] Annodam Not Successful!");
        }
        
        new RiotHandler(riotApi, settingsLoader.getLOLVersion());

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