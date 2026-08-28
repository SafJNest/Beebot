package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import com.safjnest.core.Bot;
import com.safjnest.lol.queue.QueueHandler;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.lol.queue.scheduler.RiotScheduler;
import com.safjnest.lol.queue.scheduler.SyncScheduler;
import com.safjnest.lol.tracker.TrackerScheduler;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.nosql.MongoDB;
import com.safjnest.spring.SpringServer;
import com.safjnest.status.SystemMetricsSampler;
import com.safjnest.utils.SafJNest;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.utils.log.BotLogger;
import com.safjnest.utils.twitch.TwitchClient;

public class App {

    private static Settings settings;
    private static Bot bot;
    private static SpringServer springServer;
    private static Boolean trackingEnabled;

    public static void main(String args[]) {
        SafJNest.bee();
        
        new BotLogger("Beebot", null);

        settings = SettingsLoader.getSettings();

        runSpring();
        
        TwitchClient.init();
        SystemMetricsSampler.start();
        QueueHandler.start();
        TrackerScheduler.start();

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
        SystemMetricsSampler.stop();
        if (springServer != null) {
            try {
                springServer.stop();
            } catch (Exception e) {
                BotLogger.error("Failed to stop Spring API: " + e.getMessage());
            }
        }
        bot.distruzione_demoniaca();
        QueueHandler.shutdown();
        SyncScheduler.shutdown();
        ComputeScheduler.shutdown();
        RiotScheduler.shutdown();
        MongoDB.close();
    }

    public static void restart() {
        BotLogger.trace("Restarting the bot");
        bot.distruzione_demoniaca();
        bot.il_risveglio_della_bestia();
    }

    public static boolean isTesting() {
        if (settings == null) {
            settings = SettingsLoader.getSettings();
        }
        return settings.getConfig().isTesting();
    }

    public static boolean tracking() {
        if (trackingEnabled != null) return trackingEnabled;
        return !isTesting();
    }

    public static boolean toggleTracking() {
        trackingEnabled = !tracking();
        return trackingEnabled;
    }

}
