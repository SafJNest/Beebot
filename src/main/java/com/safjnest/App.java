package com.safjnest;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.bson.Document;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.safjnest.core.Bot;
import com.safjnest.core.Chronos.ChronoTask;
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
        } else {
            TwitchClient.init();
        }

        try (MongoClient mongoClient = MongoClients.create(settings.getJsonSettings().getMonbodb())) {
            MongoDatabase database = mongoClient.getDatabase("league_of_legends");

            
            //migrateSummoners(database);  
            migrateMatches(database);
            
            BotLogger.info("Migration completed successfully!");
            
        } catch (Exception e) {
            BotLogger.error("Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void migrateSummoners(MongoDatabase database) {
        BotLogger.info("Starting summoner migration (per-summoner mode)...");
    
        MongoCollection<Document> collection = database.getCollection("summoner");
    
        QueryResult summonerIds = LeagueDB.get().query(
            "SELECT id FROM summoner ORDER BY id ASC"
        );
    
        int count = 0;
        int errors = 0;
        int skipped = 0;
    
        for (QueryRecord idRow : summonerIds) {
            int summonerId = idRow.getAsInt("id");
    
            try {
                // 🔹 summoner base
                QueryResult qrSummoner = LeagueDB.get().query(
                    "SELECT riot_id, puuid, region, level, icon, tracking, user_id " +
                    "FROM summoner WHERE id = " + summonerId + " LIMIT 1"
                );
    
                if (!qrSummoner.iterator().hasNext()) {
                    skipped++;
                    continue;
                }
    
                QueryRecord qr = qrSummoner.iterator().next();
                String puuid = qr.get("puuid");
    
                if (puuid == null || puuid.isEmpty()) {
                    skipped++;
                    continue;
                }
    
                Document summoner = new Document("_id", puuid)
                    .append("riot_id", qr.get("riot_id"))
                    .append("puuid", puuid)
                    .append("region", qr.get("region"))
                    .append("level", qr.getAsInt("level"))
                    .append("icon", qr.getAsInt("icon"))
                    .append("tracking", qr.getAsBoolean("tracking"))
                    .append("user_id", qr.get("user_id"));
    
                // 🔹 ranked
                QueryResult qrRank = LeagueDB.get().query(
                    "SELECT queue, rank, lp, wins, losses, last_update " +
                    "FROM rank WHERE summoner_id = " + summonerId
                );
    
                List<Document> ranked = new ArrayList<>();
                for (QueryRecord r : qrRank) {
                    ranked.add(new Document()
                        .append("queue", r.get("queue"))
                        .append("rank", r.get("rank"))
                        .append("lp", r.getAsInt("lp"))
                        .append("wins", r.getAsInt("wins"))
                        .append("losses", r.getAsInt("losses"))
                        .append("updated_at", r.getAsDate("last_update"))
                    );
                }
                summoner.append("ranked", ranked);
    
                // 🔹 masteries (top 50)
                QueryResult qrMastery = LeagueDB.get().query(
                    "SELECT champion_id, champion_points, champion_level, last_play_time " +
                    "FROM masteries WHERE summoner_id = " + summonerId +
                    " ORDER BY champion_points DESC"
                );
    
                List<Document> masteries = new ArrayList<>();
                for (QueryRecord m : qrMastery) {
                    masteries.add(new Document()
                        .append("champion_id", m.getAsInt("champion_id"))
                        .append("champion_points", m.getAsInt("champion_points"))
                        .append("champion_level", m.getAsInt("champion_level"))
                        .append("last_play_time", m.getAsDate("last_play_time"))
                    );
                }
                summoner.append("masteries", masteries);
    
                // 🔹 upsert
                collection.replaceOne(
                    new Document("_id", puuid),
                    summoner,
                    new ReplaceOptions().upsert(true)
                );
    
                count++;
                if (count % 1000 == 0) {
                    BotLogger.info(
                        "Summoners migrated: " + count +
                        " | errors: " + errors +
                        " | skipped: " + skipped
                    );
                }
    
            } catch (Exception e) {
                errors++;
                e.printStackTrace();
                if (errors % 50 == 0) {
                    BotLogger.error(
                        "Summoner error (" + errors + "): " + e.getMessage()
                    );
                }
            }
        }
    
        BotLogger.info(
            "Summoner migration completed. Total: " + count +
            ", Errors: " + errors +
            ", Skipped: " + skipped
        );
    }

    private static void migrateMatches(MongoDatabase database) {
        BotLogger.info("Starting match migration (per-match mode)...");
    
        MongoCollection<Document> matchCollection = database.getCollection("match");
    
        // summoner_id -> puuid
        Map<Integer, String> summonerIdToPuuid = new HashMap<>();
        QueryResult summoners = LeagueDB.get().query(
            "SELECT id, puuid FROM summoner WHERE puuid IS NOT NULL AND puuid != ''"
        );
        for (QueryRecord s : summoners) {
            summonerIdToPuuid.put(s.getAsInt("id"), s.get("puuid"));
        }
    
        QueryResult matchIds = LeagueDB.get().query(
            "SELECT id FROM `match` ORDER BY id DESC"
        );
    
        int count = 0;
        int errors = 0;
    
        for (QueryRecord idRow : matchIds) {
            int matchId = idRow.getAsInt("id");
    
            try {
                // 🔹 match
                QueryResult matchQR = LeagueDB.get().query(
                    "SELECT id, game_id, queue, region, rank, time_start, time_end, events, bans, patch " +
                    "FROM `match` WHERE id = " + matchId + " LIMIT 1"
                );
    
                if (!matchQR.iterator().hasNext()) continue;
                QueryRecord m = matchQR.iterator().next();
    
                String gameId = m.get("game_id");
                String region = m.get("region");
    
                if (gameId == null || region == null) continue;
    
                Document match = new Document("_id", region + "_" + gameId)
                    .append("game_id", gameId)
                    .append("queue", m.get("queue"))
                    .append("region", region)
                    .append("rank", m.get("rank"))
                    .append("time_start", m.getAsDate("time_start"))
                    .append("time_end", m.getAsDate("time_end"))
                    .append("patch", m.get("patch"));
    
                match.append("events", safeJson(m.get("events")));
                match.append("bans", safeJson(m.get("bans")));
    
                // 🔹 participants (NUOVA STRUTTURA)
                QueryResult participants = LeagueDB.get().query(
                    "SELECT summoner_id, win, kda, champion, level, team, lane, subteam, subteam_placement, " +
                    "rank, lp, gain, damage, doubles, triples, quadruples, pentas, damage_building, healing, " +
                    "cs, gold_earned, ward, ward_killed, vision_score, pings, build " +
                    "FROM participant WHERE match_id = " + matchId
                );
    
                List<Document> participantDocs = new ArrayList<>();
    
                for (QueryRecord p : participants) {
                    int summonerId = p.getAsInt("summoner_id");
                    String puuid = summonerIdToPuuid.getOrDefault(
                        summonerId, "unknown_" + summonerId
                    );
    
                    Document doc = new Document()
                        .append("puuid", puuid)
                        .append("win", p.getAsBoolean("win"))
                        .append("kda", p.get("kda"))
                        .append("champion", p.getAsInt("champion"))
                        .append("level", p.getAsInt("level"))
                        .append("team", p.get("team"))
                        .append("lane", p.get("lane"))
                        .append("subteam", p.getAsInt("subteam"))
                        .append("subteam_placement", p.getAsInt("subteam_placement"))
                        .append("rank", p.get("rank"))
                        .append("lp", p.getAsInt("lp"))
                        .append("gain", p.getAsInt("gain"))
                        .append("damage", p.getAsInt("damage"))
                        .append("doubles", p.getAsInt("doubles"))
                        .append("triples", p.getAsInt("triples"))
                        .append("quadruples", p.getAsInt("quadruples"))
                        .append("pentas", p.getAsInt("pentas"))
                        .append("damage_building", p.getAsInt("damage_building"))
                        .append("healing", p.getAsInt("healing"))
                        .append("cs", p.getAsInt("cs"))
                        .append("gold_earned", p.getAsInt("gold_earned"))
                        .append("ward", p.getAsInt("ward"))
                        .append("ward_killed", p.getAsInt("ward_killed"))
                        .append("vision_score", p.getAsInt("vision_score"));
    
                    doc.append("pings", safeJson(p.get("pings")));
                    // Parsing JSON del build
                    Document buildDoc = safeJson(p.get("build"));

                    if (!buildDoc.isEmpty()) {
                        // Runes
                        Document runes = buildDoc.get("runes", Document.class);
                        if (runes != null) doc.append("runes", runes);
                    
                        // Build / starter / boots
                        Document buildItems = buildDoc.get("build", Document.class);
                        if (buildItems != null) doc.append("build_items", buildItems);
                    
                        // Summoner spells
                        List<String> summonerSpells = buildDoc.getList("summoner_spells", String.class);
                        if (summonerSpells != null) doc.append("summoner_spells", summonerSpells);
                    
                        // Skill order
                        List<String> skillOrder = buildDoc.getList("skill_order", String.class);
                        if (skillOrder != null) doc.append("skill_order", skillOrder);
                    
                        // Augments (opzionale)
                        List<String> augments = buildDoc.getList("augments", String.class);
                        if (augments != null) doc.append("augments", augments);
                    }
                    
    
                match.append("participants", participantDocs);
    
                matchCollection.replaceOne(
                    new Document("_id", region + "_" + gameId),
                    match,
                    new ReplaceOptions().upsert(true)
                );
    
                count++;
                if (count % 1000 == 0) {
                    BotLogger.info("Matches migrated: " + count);
                }
            }
    
            } catch (Exception e) {
                errors++;
                if (errors % 50 == 0) {
                    BotLogger.error("Match error (" + errors + "): " + e.getMessage());
                }
            }
        }
    
        BotLogger.info("Match migration completed. Total: " + count + ", Errors: " + errors);
    }

    private static Document safeJson(String json) {
        try {
            if (json != null && !json.isEmpty() && !json.equals("{}")) {
                return Document.parse(json);
            }
        } catch (Exception ignored) {}
        return new Document();
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