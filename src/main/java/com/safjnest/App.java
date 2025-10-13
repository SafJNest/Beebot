package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.bson.Document;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.safjnest.core.Bot;
import com.safjnest.model.BotSettings.Settings;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
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
        //bot = new Bot();
        //bot.il_risveglio_della_bestia();

        String uri = "enacoid";
        try (MongoClient mongoClient = MongoClients.create(uri)) {
            MongoDatabase database = mongoClient.getDatabase("league_of_legends");
            MongoCollection<Document> collection = database.getCollection("summoner");

            String query = "SELECT id, riot_id, puuid, region, level, icon FROM summoner ORDER BY id ASC LIMIT 100;";
            QueryResult summoners = LeagueDB.get().query(query);

            for (QueryRecord qr : summoners) {
                int summonerId = qr.getAsInt("id");

                String qRank = "SELECT queue, rank, lp, wins, losses FROM rank WHERE summoner_id = " + summonerId + ";";
                String qMastery = "SELECT champion_id, champion_points, champion_level FROM masteries WHERE summoner_id = " + summonerId + ";";

                QueryResult qrRank = LeagueDB.get().query(qRank);
                QueryResult qrMastery = LeagueDB.get().query(qMastery);

                Document summoner = new Document();
                summoner.append("id", summonerId);
                summoner.append("riot_id", qr.get("riot_id"));
                summoner.append("puuid", qr.get("puuid"));
                summoner.append("region", qr.get("region"));
                summoner.append("level", qr.getAsInt("level"));
                summoner.append("icon", qr.getAsInt("icon"));
                List<Document> rankedList = new ArrayList<>();
                for (QueryRecord r : qrRank) {
                    Document doc = new Document()
                        .append("queue", r.get("queue"))
                        .append("tier", r.get("tier"))
                        .append("rank", r.get("rank"))
                        .append("lp", r.getAsInt("lp"))
                        .append("wins", r.getAsInt("wins"))
                        .append("losses", r.getAsInt("losses"));
                    rankedList.add(doc);
                }
                summoner.append("ranked", rankedList);
                List<Document> masteryList = new ArrayList<>();
                for (QueryRecord m : qrMastery) {
                    Document doc = new Document()
                        .append("champion_id", m.getAsInt("champion_id"))
                        .append("champion_points", m.getAsInt("champion_points"))
                        .append("champion_level", m.getAsInt("champion_level"));
                    masteryList.add(doc);
                }
                summoner.append("masteries", masteryList);


                // upsert: inserisce se non esiste, aggiorna se esiste
                collection.replaceOne(
                    new Document("puuid", qr.get("puuid")),
                    summoner,
                    new ReplaceOptions().upsert(true)
                );

                System.out.println("✅ Imported: " + qr.get("riot_id"));
            }
        }
            
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

}
