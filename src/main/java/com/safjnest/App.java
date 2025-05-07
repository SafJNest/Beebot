package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
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

    private static String botName;
    private static Bot bot;
    
    public static final boolean TEST_MODE = getPropertyAsBoolean("testing");

    public static void main(String args[]) {
        
        SafJNest.bee();
        
        new BotLogger("Beebot", null);

        botName = TEST_MODE ? (args.length > 1 ? args[1] : App.getProperty("bot")) : "beebot";
        settingsLoader = new SettingsLoader(botName, TEST_MODE);

        if (TEST_MODE) {
            BotLogger.info("Beebot is in testing mode");
            runSpring();
        }
        else {
            TwitchClient.init();
            //runSpring();
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
        return botName;
    }

    public static SettingsLoader getSettingsLoader() {
        return settingsLoader;
    }
}
