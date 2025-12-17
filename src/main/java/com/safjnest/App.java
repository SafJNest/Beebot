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
import static com.mongodb.client.model.Filters.eq;
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

            // for (QueryRecord qr : summoners) {
            //     int summonerId = qr.getAsInt("id");

            //     String qRank = "SELECT queue, rank, lp, wins, losses FROM rank WHERE summoner_id = " + summonerId + ";";
            //     String qMastery = "SELECT champion_id, champion_points, champion_level FROM masteries WHERE summoner_id = " + summonerId + ";";

            //     QueryResult qrRank = LeagueDB.get().query(qRank);
            //     QueryResult qrMastery = LeagueDB.get().query(qMastery);

            //     Document summoner = new Document();
            //     summoner.append("id", summonerId);
            //     summoner.append("riot_id", qr.get("riot_id"));
            //     summoner.append("puuid", qr.get("puuid"));
            //     summoner.append("region", qr.get("region"));
            //     summoner.append("level", qr.getAsInt("level"));
            //     summoner.append("icon", qr.getAsInt("icon"));
            //     List<Document> rankedList = new ArrayList<>();
            //     for (QueryRecord r : qrRank) {
            //         Document doc = new Document()
            //             .append("queue", r.get("queue"))
            //             .append("tier", r.get("tier"))
            //             .append("rank", r.get("rank"))
            //             .append("lp", r.getAsInt("lp"))
            //             .append("wins", r.getAsInt("wins"))
            //             .append("losses", r.getAsInt("losses"));
            //         rankedList.add(doc);
            //     }
            //     summoner.append("ranked", rankedList);
            //     List<Document> masteryList = new ArrayList<>();
            //     for (QueryRecord m : qrMastery) {
            //         Document doc = new Document()
            //             .append("champion_id", m.getAsInt("champion_id"))
            //             .append("champion_points", m.getAsInt("champion_points"))
            //             .append("champion_level", m.getAsInt("champion_level"));
            //         masteryList.add(doc);
            //     }
            //     summoner.append("masteries", masteryList);


            //     // upsert: inserisce se non esiste, aggiorna se esiste
            //     collection.replaceOne(
            //         new Document("puuid", qr.get("puuid")),
            //         summoner,
            //         new ReplaceOptions().upsert(true)
            //     );

            //     System.out.println("✅ Imported: " + qr.get("riot_id"));
            // }
            MongoCollection<Document> summonerCollection = database.getCollection("summoner");
            query = "SELECT id, game_id, queue, region, rank, time_start, time_end, events, bans, patch FROM `match` ORDER by ID desc limit 10000";
            QueryResult matches = LeagueDB.get().query(query);
            collection = database.getCollection("match");
            for (QueryRecord qr : matches) {
                int matchId = qr.getAsInt("id");

                String qParticipants = "SELECT id, summoner_id, win, kda, champion, team, lane, subteam, subteam_placement, rank, lp, gain, damage, damage_building, healing, cs, gold_earned, ward, ward_killed, vision_score, pings, build FROM participant WHERE match_id = " + matchId + ";";
                QueryResult qrParticipants = LeagueDB.get().query(qParticipants);

                Document match = new Document("_id", qr.get("region") + "_" + qr.get("game_id"));
                match.append("game_id", qr.get("game_id"));
                match.append("queue", qr.get("queue"));
                match.append("region", qr.get("region"));
                match.append("rank", qr.get("rank"));
                match.append("time_start", qr.getAsLong("time_start"));
                match.append("time_end", qr.getAsLong("time_end"));
                match.append("events", Document.parse(qr.get("events")));
                match.append("bans", Document.parse(qr.get("bans")));
                match.append("patch", qr.get("patch"));
                List<Document> participantList = new ArrayList<>();
                for (QueryRecord p : qrParticipants) {
                    Document doc = new Document()
                        .append("puuid", "aa")
                        .append("win", p.getAsBoolean("win"))
                        .append("kda", p.get("kda"))
                        .append("champion", p.getAsInt("champion"))
                        .append("team", p.getAsInt("team"))
                        .append("lane", p.get("lane"))
                        .append("subteam", p.get("subteam"))
                        .append("subteam_placement", p.getAsInt("subteam_placement"))
                        .append("rank", p.get("rank"))
                        .append("lp", p.getAsInt("lp"))
                        .append("gain", p.getAsInt("gain"))
                        .append("damage", p.getAsInt("damage"))
                        .append("damage_building", p.getAsInt("damage_building"))
                        .append("healing", p.getAsInt("healing"))
                        .append("cs", p.getAsInt("cs"))
                        .append("gold_earned", p.getAsInt("gold_earned"))
                        .append("ward", p.getAsInt("ward"))
                        .append("ward_killed", p.getAsInt("ward_killed"))
                        .append("vision_score", p.getAsInt("vision_score"))
                        .append("pings", p.getAsInt("pings"))
                        .append("build", Document.parse(p.get("build")));
                    participantList.add(doc);
                }
                match.append("participants", participantList);

                collection.replaceOne(
                    new Document("_id", qr.get("region") + "_" + qr.get("game_id")),
                    match,
                    new ReplaceOptions().upsert(true)
                );

                System.out.println("✅ Imported: " + qr.get("game_id"));
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
