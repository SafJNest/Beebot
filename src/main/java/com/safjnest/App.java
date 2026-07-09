package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import com.safjnest.core.Bot;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.spring.SpringServer;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.utils.log.BotLogger;
import com.safjnest.utils.twitch.TwitchClient;

public class App {

    private static Settings settings;
    private static Bot bot;
    private static SpringServer springServer;

    public static void main(String args[]) {
        SafJNest.bee();
        
        new BotLogger("Beebot", null);

        settings = SettingsLoader.getSettings();

        if (isTesting()) {
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
        Properties springProperties = new Properties();
        try {
            springProperties.load(new FileReader("spring.properties"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int port = Integer.parseInt(springProperties.getProperty("server.port", "8080"));

        try {
            springServer = SpringServer.start(port);
            BotLogger.info("Spring API started on port " + port);
        } catch (Exception e) {
            BotLogger.error("Failed to start Spring API: " + e.getMessage());
        }
    }

    public static void shutdown() {
        BotLogger.trace("Shutting down the bot");
        if (springServer != null) {
            try {
                springServer.stop();
            } catch (Exception e) {
                BotLogger.error("Failed to stop Spring API: " + e.getMessage());
            }
        }
        bot.distruzione_demoniaca();
    }

    public static void restart() {
        BotLogger.trace("Restarting the bot");
        bot.distruzione_demoniaca();
        bot.il_risveglio_della_bestia();
    }

    public static boolean isTesting() {
        return settings.getConfig().isTesting();
    }

}
