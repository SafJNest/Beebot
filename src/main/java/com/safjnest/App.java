package com.safjnest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.util.SafJNest;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.twitch.TwitchClient;

@SpringBootApplication
public class App {
    private static final String settingsPath = "rsc" + File.separator + "settings.json";
    public static String key;

    private static Settings settings;

    private static String botName;
    private static Bot bot;
    
    public static boolean testing;

    public static void main(String args[]) {
        
        SafJNest.bee();
        
        new BotLogger("Beebot", null);

        try {
            SettingsLoader.loadSettings(settingsPath, "config.properties");
        } catch (IOException e) {
            BotLogger.error("Error loading settings files");
            e.printStackTrace();
        }
        settings = SettingsLoader.getSettings();

        testing = settings.getConfig().isTesting();
        botName = settings.getConfig().getBot();

        if (testing) {
            BotLogger.info("Beebot is in testing mode");
            runSpring();
        }
        else {
            TwitchClient.init();
            runSpring();
        }
                        
        bot = new Bot();
        bot.il_risveglio_della_bestia();
    }

    public static void runSpring() {
        SpringApplication springApplication = new SpringApplication(App.class);
            
        Properties springProperties = new Properties();
        try {
            springProperties.load(new FileReader("spring.properties"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        springApplication.setDefaultProperties(springProperties);
        springApplication.run();
    }

    public static void shutdown() {
        BotLogger.trace("Shutting down the bot");
        bot.distruzione_demoniaca();
    }

    public static void restart() {
        BotLogger.trace("Restarting the bot");
        bot.distruzione_demoniaca();
        bot.il_risveglio_della_bestia();
    }

    public static String getBot() {
        return botName;
    }

}
