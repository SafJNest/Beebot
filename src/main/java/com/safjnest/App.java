package com.safjnest;

import java.io.FileReader;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.util.SafJNest;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.twitch.TwitchClient;

@SpringBootApplication
public class App {
    public static String key;

    private static Properties properties;
    private static SettingsLoader settingsLoader;

    private static Bot extreme_safj_beebot;

    private static String bot;
    
    /**
     * Insane beebot core
     */
    private static boolean EXTREME_TESTING;

    public static boolean isExtremeTesting() {
        return EXTREME_TESTING;
    }

    public static SettingsLoader getSettingsLoader() {
        return settingsLoader;
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

        bot = App.isExtremeTesting() ? (args.length > 1 ? args[1] : App.getProperty("bot")) : "beebot";
        settingsLoader = new SettingsLoader(bot, App.isExtremeTesting());
                
        if(!isExtremeTesting()) TwitchClient.init();
        
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