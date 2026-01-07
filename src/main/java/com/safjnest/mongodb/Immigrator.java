package com.safjnest.mongodb;

import java.util.*;
import java.util.concurrent.*;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;

import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.util.log.BotLogger;

/**
 * Gestisce la migrazione SQL -> MongoDB
 * Tutta la logica di migration è centralizzata qui.
 */
public final class Immigrator {

    private Immigrator() {}

    // =========================================================
    // ENTRY POINT
    // =========================================================

    public static void runMigrations() {
        MongoDatabase db = MongoManager.LOLdb;

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CompletableFuture<Void> summoners =
            CompletableFuture.runAsync(() -> migrateSummoners(db), executor);

        CompletableFuture<Void> matches =
            CompletableFuture.runAsync(() -> migrateMatches(db), executor);

        CompletableFuture.allOf(summoners, matches).join();
        executor.shutdown();
    }

    private static void migrateSummoners(MongoDatabase database) {

        MongoCollection<Document> collection =
            database.getCollection("summoner");

        QueryResult summonerIds =
            LeagueDB.get().query("SELECT id FROM summoner ORDER BY id ASC");

        int count = 0;
        int errors = 0;
        int skipped = 0;

        for (QueryRecord idRow : summonerIds) {
            int summonerId = idRow.getAsInt("id");

            try {
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
                    .append("user_id", qr.get("user_id"))
                    .append("ranked", loadRanks(summonerId))
                    .append("masteries", loadMasteries(summonerId));

                collection.replaceOne(
                    new Document("_id", puuid),
                    summoner,
                    new ReplaceOptions().upsert(true)
                );

                count++;
                if (count % 1000 == 0) {
                    BotLogger.info("Summoners migrated: " + count);
                }

            } catch (Exception e) {
                errors++;
                if (errors % 50 == 0) {
                    BotLogger.error("Summoner migration error (" + errors + ")");
                }
            }
        }

        BotLogger.info(
            "Summoner migration completed | total=" + count +
            " skipped=" + skipped +
            " errors=" + errors
        );
    }

    private static List<Document> loadRanks(int summonerId) {
        QueryResult qr = LeagueDB.get().query(
            "SELECT queue, rank, lp, wins, losses, last_update " +
            "FROM rank WHERE summoner_id = " + summonerId
        );

        List<Document> ranked = new ArrayList<>();
        for (QueryRecord r : qr) {
            ranked.add(new Document()
                .append("queue", r.get("queue"))
                .append("rank", r.get("rank"))
                .append("lp", r.getAsInt("lp"))
                .append("wins", r.getAsInt("wins"))
                .append("losses", r.getAsInt("losses"))
                .append("updated_at", r.getAsDate("last_update"))
            );
        }
        return ranked;
    }

    private static List<Document> loadMasteries(int summonerId) {
        QueryResult qr = LeagueDB.get().query(
            "SELECT champion_id, champion_points, champion_level, last_play_time " +
            "FROM masteries WHERE summoner_id = " + summonerId +
            " ORDER BY champion_points DESC"
        );

        List<Document> masteries = new ArrayList<>();
        for (QueryRecord m : qr) {
            masteries.add(new Document()
                .append("champion_id", m.getAsInt("champion_id"))
                .append("champion_points", m.getAsInt("champion_points"))
                .append("champion_level", m.getAsInt("champion_level"))
                .append("last_play_time", m.getAsDate("last_play_time"))
            );
        }
        return masteries;
    }


    private static void migrateMatches(MongoDatabase database) {
        BotLogger.info("Starting match migration...");

        MongoCollection<Document> collection =
            database.getCollection("match");

        Map<Integer, String> summonerIdToPuuid = loadPuuidMap();

        QueryResult matchIds = LeagueDB.get().query("SELECT m.id FROM `match` m ORDER BY m.id DESC");

        int count = 0;
        int errors = 0;

        for (QueryRecord idRow : matchIds) {
            int matchId = idRow.getAsInt("id");

            try {
                QueryResult qr = LeagueDB.get().query(
                    "SELECT id, game_id, queue, region, rank, time_start, time_end, " +
                    "events, bans, patch FROM `match` WHERE id = " + matchId + " LIMIT 1"
                );

                if (!qr.iterator().hasNext()) continue;

                QueryRecord m = qr.iterator().next();

                String gameId = m.get("game_id");
                String region = m.get("region");

                if (gameId == null || region == null) continue;

                String mongoId = region + "_" + gameId;

                Document match = new Document("_id", mongoId)
                    .append("region", region)
                    .append("game_id", gameId)
                    .append("queue", m.get("queue"))
                    .append("rank", m.get("rank"))
                    .append("time_start", m.getAsDate("time_start"))
                    .append("time_end", m.getAsDate("time_end"))
                    .append("patch", m.get("patch"))
                    .append("bans", safeJson(m.get("bans")))
                    .append("events", safeJson(m.get("events")));



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
                        .append("kills", Integer.parseInt(p.get("kda").split("/")[0]))
                        .append("deaths", Integer.parseInt(p.get("kda").split("/")[1]))
                        .append("assists", Integer.parseInt(p.get("kda").split("/")[2]))
                        .append("doubles", p.getAsInt("doubles"))
                        .append("triples", p.getAsInt("triples"))
                        .append("quadruples", p.getAsInt("quadruples"))
                        .append("pentas", p.getAsInt("pentas"))
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
                    
                        if (buildDoc.containsKey("summoner_spells")) {
                            List<Integer> summonerSpells = buildDoc
                                .getList("summoner_spells", Object.class)
                                .stream()
                                .map(o -> Integer.parseInt(o.toString()))
                                .toList();

                            doc.append("summoner_spells", summonerSpells);
                        }

                        if (buildDoc.containsKey("skill_order")) {
                            List<Integer> skillOrder = buildDoc
                                .getList("skill_order", Object.class)
                                .stream()
                                .map(o -> Integer.parseInt(o.toString()))
                                .toList();

                            doc.append("skill_order", skillOrder);
                        }

                        if (buildDoc.containsKey("augments")) {
                            List<Integer> augments = buildDoc
                                .getList("augments", Object.class)
                                .stream()
                                .map(o -> Integer.parseInt(o.toString()))
                                .toList();

                            doc.append("augments", augments);
                        }
                    }
                    participantDocs.add(doc);
                    
                }
    
                match.append("participants", participantDocs);

                collection.replaceOne(
                    new Document("_id", mongoId),
                    match,
                    new ReplaceOptions().upsert(true)
                );

                count++;
                if (count % 1000 == 0) {
                    BotLogger.info("Matches migrated: " + count);
                }

            } catch (Exception e) {
                e.printStackTrace();
                errors++;
                if (errors % 50 == 0) {
                    BotLogger.error("Match migration error (" + errors + ")");
                }
            }
        }

        BotLogger.info(
            "Match migration completed | total=" + count +
            " errors=" + errors
        );
    }

    private static Map<Integer, String> loadPuuidMap() {
        Map<Integer, String> map = new HashMap<>();

        QueryResult qr = LeagueDB.get().query(
            "SELECT id, puuid FROM summoner " +
            "WHERE puuid IS NOT NULL AND puuid != ''"
        );

        for (QueryRecord r : qr) {
            map.put(r.getAsInt("id"), r.get("puuid"));
        }
        return map;
    }

    

    // =========================================================
    // UTILS
    // =========================================================

    private static Document safeJson(String json) {
        try {
            if (json != null && !json.isEmpty() && !json.equals("{}")) {
                return Document.parse(json);
            }
        } catch (Exception ignored) {}
        return new Document();
    }
}
