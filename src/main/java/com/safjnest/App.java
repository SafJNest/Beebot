package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.safjnest.core.Bot;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.build.RecommendationService;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.SafJNest;  
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.twitch.TwitchClient;


@SpringBootApplication
public class App {

    private static Settings settings;
    private static Bot bot;

    public static void main(String args[]) {
        SafJNest.bee();
        
        new BotLogger("Beebot", null);

        settings = SettingsLoader.getSettings();

        if (isTesting()) {
            BotLogger.info("Beebot is in testing mode");
            //runSpring();
        }
        else {
            TwitchClient.init();
            //runSpring();
        }
        
        try (Connection conn = LeagueDB.get().getConnection()) {
            String role = "JUNGLE";
            var a = new RecommendationService().get(81, "BOT", role, RecommendationService.Strategy.MOST_USED, conn);
            if (a.build() != null) {
                System.out.println(a.games());
                System.out.println(a.winrate());
                System.out.println("role=" + role);
                System.out.println("starter=" + toItemNames(a.build().starterItems()));
                System.out.println("core=" + toItemNames(a.build().coreItems()));
                System.out.println("fullBuild=" + toItemNames(a.build().fullBuildItems()));
                System.out.println("suggestions=" + toItemNames(a.build().suggestionItems()));
                System.out.println("boots=" + itemName(a.build().boots()));

                System.out.println("runes=" + a.runes());
                System.out.println("runes=" + a.runes().primaryRuneItems());
                System.out.println("runes=" + a.runes().secondaryRuneItems());
                System.out.println("runes=" + a.runes().statShardItems());

                System.out.println("stats=" + a.build().spellOrder());
            } else {
                System.out.println("No build recommendation (no matching stats or below MIN_GAMES).");
            }
        } catch (Exception e) {
            e.printStackTrace();
    
        }
        //bot = new Bot();
        //bot.il_risveglio_della_bestia();
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

    public static boolean isTesting() {
        return settings.getConfig().isTesting();
    }

    private static String toItemNames(List<Integer> itemIds) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < itemIds.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            int id = itemIds.get(i);
            sb.append(id).append(": ").append(itemName(id));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String itemName(int itemId) {
        try {
            var item = LeagueHandler.getRiotApi().getDDragonAPI().getItem(itemId);
            if (item == null || item.getName() == null || item.getName().isBlank()) {
                return "Unknown item";
            }
            return item.getName();
        } catch (Exception ignored) {
            return "Unknown item";
        }
    }

}
