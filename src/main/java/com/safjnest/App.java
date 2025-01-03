package com.safjnest;

import java.io.FileReader;
import java.security.SecureRandom;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.core.audio.tts.TTSHandler;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.util.PermissionHandler;
import com.safjnest.util.SafJNest;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.twitch.TwitchClient;

import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.impl.R4J;

@SpringBootApplication
public class App {
    private static TTSHandler tts;
    private static R4J riotApi;
    public static String key;

    private static Properties properties;

    private static Bot extreme_safj_beebot;

    private static String bot;
    
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

        EXTREME_TESTING = args.length > 0 ? Boolean.parseBoolean(args[0]) : getPropertyAsBoolean("testing");
        if (EXTREME_TESTING) BotLogger.info("Beebot is in testing mode");
        else BotLogger.info("Beebot is in normal mode");

        if (!EXTREME_TESTING) {
            SpringApplication app = new SpringApplication(App.class);
            try {
                Properties spring = new Properties();
                spring.load(new FileReader("spring.properties"));
                app.setDefaultProperties(spring);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //app.run(args);
        }

        SecureRandom secureRandom = new SecureRandom();
        BotLogger.info("[System]: System Entropy: " + secureRandom.getProvider().getInfo());

        bot = App.isExtremeTesting() ? (args.length > 1 ? args[1] : App.getProperty("bot")) : "beebot";
        SettingsLoader settingsLoader = new SettingsLoader(bot, App.isExtremeTesting());

        tts = new TTSHandler(settingsLoader.getTTSApiKey());
        
        new DatabaseHandler(
            settingsLoader.getDBHostname(),
            settingsLoader.getDBName(),
            settingsLoader.getDBUser(),
            settingsLoader.getDBPassword()
        );

        new SoundCache();
        
        new TwitchClient(
            settingsLoader.getTwitchClientId(),
            settingsLoader.getTwitchClientSecret()
        );
        
        if(isExtremeTesting()) {
            TwitchClient.init();
        }

        riotApi = null;
        try {
            riotApi = new R4J(new APICredentials(settingsLoader.getRiotKey()));
            BotLogger.info("[R4J] Connection Successful!");
        } catch (Exception e) {
            BotLogger.error("[R4J] Annodam Not Successful!");
        }
        
        new LeagueHandler(riotApi, settingsLoader.getLOLVersion());

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

    public static String getBot() {
        return bot;
    }
}