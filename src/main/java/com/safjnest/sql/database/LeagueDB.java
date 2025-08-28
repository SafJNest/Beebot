package com.safjnest.sql.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.json.simple.JSONObject;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

import com.safjnest.sql.AbstractDB;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.util.SettingsLoader;
import com.safjnest.util.lol.LeagueHandler;
import com.safjnest.util.lol.model.build.CustomBuildData;

public class LeagueDB extends AbstractDB {

    private static LeagueDB instance;
    static {
        instance = new LeagueDB();
    }

    @Override
	protected String getDatabase() {
        return SettingsLoader.getSettings().getJsonSettings().getLeagueDatabase().getDatabaseName();
	}

    public static LeagueDB get() {
        return instance;
    }

    public static QueryResult getLOLAccountsByUserId(String user_id){
        String query = "SELECT puuid, league_shard, tracking FROM summoner WHERE user_id = '" + user_id + "' order by id;";
        return instance.query(query);
    }

    public static String getUserIdByLOLAccountId(String puuid, LeagueShard shard) {
        return instance.lineQuery("SELECT user_id FROM summoner WHERE puuid = '" + puuid + "' AND league_shard = '" + shard.ordinal() + "';").get("user_id");
    }

    public static QueryResult getAdvancedLOLData(String summonerId) {
        return instance.query("SELECT `champion`, COUNT(*) AS `games`, SUM(`win`) AS `wins`, SUM(CASE WHEN `win` = 0 THEN 1 ELSE 0 END) AS `losses`, AVG(CAST(SUBSTRING_INDEX(`kda`, '/', 1) AS UNSIGNED)) AS avg_kills, AVG(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(`kda`, '/', -2), '/', 1) AS UNSIGNED)) AS avg_deaths, AVG(CAST(SUBSTRING_INDEX(`kda`, '/', -1) AS UNSIGNED)) AS avg_assists, SUM(`gain`) AS total_lp_gain FROM `participant` WHERE `summoner_id` = '" + summonerId + "' GROUP BY `champion` ORDER BY `games` DESC;");
    }

    public static QueryResult getAllGamesForAccount(int summonerId, long time_start, long time_end) {
        String timeFilter = "";
        if (time_start != 0) {
            timeFilter = "AND sm.`time_start` >= '" + new Timestamp(time_start) + "' " +
                        "AND sm.`time_end` <= '" + new Timestamp(time_end) + "' ";
        }
        return instance.query("SELECT sm.game_id, sm.game_type, st.win " +
                         "FROM participant st " +
                         "INNER JOIN `match` sm ON st.match_id = sm.id " +
                         "WHERE st.summoner_id = '" + summonerId + "' " + timeFilter);
    }
    
    public static QueryResult getAdvancedLOLData(int summonerId, long time_start, long time_end, GameQueueType queue) {
        String timeFilter = "";
        String queueFilter = "";
        if (time_start != 0) {
            timeFilter = "AND sm.`time_start` >= '" + new Timestamp(time_start) + "' " +
                        "AND sm.`time_end` <= '" + new Timestamp(time_end) + "' ";
        }
        if (queue != null) {
            queueFilter = "AND sm.game_type = " + queue.ordinal() + " ";
        }


        String overallQuery =
            "SELECT " +
            "  t.`champion`, " +
            "  COUNT(*) AS `games`, " +
            "  SUM(t.`win`) AS `wins`, " +
            "  SUM(CASE WHEN t.`win` = 0 THEN 1 ELSE 0 END) AS `losses`, " +
            "  AVG(CAST(SUBSTRING_INDEX(t.`kda`, '/', 1) AS UNSIGNED)) AS avg_kills, " +
            "  AVG(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(t.`kda`, '/', -2), '/', 1) AS UNSIGNED)) AS avg_deaths, " +
            "  AVG(CAST(SUBSTRING_INDEX(t.`kda`, '/', -1) AS UNSIGNED)) AS avg_assists, " +
            "  SUM(t.`gain`) AS total_lp_gain " +
            "FROM `participant` t " +
            "JOIN `match` sm ON t.`match_id` = sm.`id` " +
            "WHERE t.`summoner_id` = '" + summonerId + "' " +
            timeFilter + 
            queueFilter +
            "GROUP BY t.`champion`";

        String laneQuery =
            "SELECT " +
            "  t.`champion`, " +
            "  t.`lane`, " +
            "  COUNT(*) AS `lane_games`, " +
            "  SUM(t.`win`) AS `lane_wins`, " +
            "  SUM(CASE WHEN t.`win` = 0 THEN 1 ELSE 0 END) AS `lane_losses` " +
            "FROM `participant` t " +
            "JOIN `match` sm ON t.`match_id` = sm.`id` " +
            "WHERE t.`summoner_id` = '" + summonerId + "' " +
            timeFilter + 
            queueFilter +
            "GROUP BY t.`champion`, t.`lane`";

        String combinedQuery =
            "SELECT " +
            "  overall.`champion`, " +
            "  overall.`games`, " +
            "  overall.`wins`, " +
            "  overall.`losses`, " +
            "  overall.`avg_kills`, " +
            "  overall.`avg_deaths`, " +
            "  overall.`avg_assists`, " +
            "  overall.`total_lp_gain`, " +
            "  GROUP_CONCAT( " +
            "    CONCAT(lane.`lane`, '-', lane.`lane_wins`, '-', lane.`lane_losses`) " +
            "    ORDER BY lane.`lane` SEPARATOR ', ' " +
            "  ) AS lanes_played " +
            "FROM (" + overallQuery + ") AS overall " +
            "LEFT JOIN (" + laneQuery + ") AS lane " +
            "ON overall.`champion` = lane.`champion` " +
            "GROUP BY overall.`champion` " +
            "ORDER BY `games` DESC;";

        return instance.query(combinedQuery);
    }

    public static int addLOLAccount(Summoner summoner) {
        return addLOLAccount(null, summoner);
    }

    public static int addLOLAccount(String user_id, Summoner summoner) {
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
        String query = "INSERT INTO summoner(user_id, summoner_id, account_id, puuid, riot_id, league_shard) " +
                "VALUES(?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "id = LAST_INSERT_ID(id), " +
                "user_id = IF(VALUES(user_id) IS NOT NULL, VALUES(user_id), user_id), " +
                "summoner_id = VALUES(summoner_id), " +
                "account_id = VALUES(account_id), " +
                "puuid = VALUES(puuid), " +
                "riot_id = VALUES(riot_id), " +
                "league_shard = VALUES(league_shard);";

        try (Connection conn = instance.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            if (user_id != null) {
                pstmt.setString(1, user_id);
            } else {
                pstmt.setNull(1, java.sql.Types.VARCHAR);
            }
            pstmt.setString(2, summoner.getSummonerId());
            pstmt.setString(3, summoner.getAccountId());
            pstmt.setString(4, summoner.getPUUID());
            pstmt.setString(5, account.getName() + "#" + account.getTag());
            pstmt.setInt(6, summoner.getPlatform().ordinal());

            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            int id = 0;
            if (rs.next()) {
                id = rs.getInt(1);
            }
            
            conn.commit();
            return id;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static boolean addLOLAccount(SpectatorGameInfo info) {
        String query = "INSERT INTO summoner(summoner_id, puuid, riot_id, league_shard) " +
                       "VALUES(?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "summoner_id = VALUES(summoner_id), " +
                       "puuid = VALUES(puuid), " +
                       "riot_id = VALUES(riot_id), " +
                       "league_shard = VALUES(league_shard);";

        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (SpectatorParticipant summoner : info.getParticipants()) {
                pstmt.setString(1, summoner.getSummonerId());
                pstmt.setString(2, summoner.getPuuid());
                pstmt.setString(3, summoner.getRiotId());
                pstmt.setInt(4, info.getPlatform().ordinal());
                pstmt.addBatch();
            }

            int[] affectedRows = pstmt.executeBatch();
            conn.commit();
            return affectedRows.length == info.getParticipants().size();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addLOLAccountFromMatch(LOLMatch match) {
        String query = "INSERT INTO summoner(summoner_id, puuid, riot_id, league_shard) " +
                       "VALUES(?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "summoner_id = VALUES(summoner_id), " +
                       "puuid = VALUES(puuid), " +
                       "riot_id = VALUES(riot_id), " +
                       "league_shard = VALUES(league_shard);";

        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (MatchParticipant summoner : match.getParticipants()) {
                pstmt.setString(1, summoner.getSummonerId());
                pstmt.setString(2, summoner.getPuuid());
                pstmt.setString(3, summoner.getRiotIdName() + "#" + summoner.getRiotIdTagline());
                pstmt.setInt(4, match.getPlatform().ordinal());
                pstmt.addBatch();
            }

            int[] affectedRows = pstmt.executeBatch();
            conn.commit();
            return affectedRows.length == match.getParticipants().size();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteLOLaccount(String user_id, String puuid){
        String query = "UPDATE summoner SET tracking = 0, user_id = NULL WHERE user_id = '" + user_id + "' AND puuid = '" + puuid + "';";
        return instance.defaultQuery(query);
    }

     public static QueryResult getRegistredLolAccount(long time_start) {
        return instance.query(
            "SELECT s.puuid, s.league_shard, st.game_id, st.rank, st.lp, st.time_start "
            + "FROM summoner s "
            + "LEFT JOIN ("
            + "    SELECT t.summoner_id, t.game_id, t.rank, t.lp, t.time_start "
            + "    FROM ("
            + "        SELECT st.summoner_id, sm.game_id, st.rank, st.lp, sm.time_start, "
            + "        ROW_NUMBER() OVER (PARTITION BY st.summoner_id ORDER BY sm.time_start DESC) AS rn "
            + "        FROM participant st "
            + "        JOIN `match` sm ON st.match_id = sm.id "
            + "        WHERE sm.time_start >= '" + new Timestamp(time_start) + "' "
            + "        AND sm.game_type = 43"
            + "    ) t "
            + "    WHERE t.rn = 1"
            + ") st ON s.id = st.summoner_id "
            + "WHERE s.tracking = 1;"
        );
    }





    public static QueryRecord getRegistredLolAccount(int summonerId, long time_start) {
        return instance.lineQuery("SELECT s.puuid, s.league_shard, st.game_id, st.rank, st.lp, st.time_start "
                + "FROM summoner s "
                + "LEFT JOIN (SELECT t.summoner_id, t.game_id, t.rank, t.lp, t.time_start "
                + "           FROM (SELECT st.summoner_id, sm.game_id, st.rank, st.lp, sm.time_start, "
                + "                        ROW_NUMBER() OVER (PARTITION BY st.summoner_id ORDER BY sm.time_start DESC) AS rn "
                + "                 FROM participant st "
                + "                 JOIN `match` sm ON st.match_id = sm.id "
                + "                 WHERE sm.time_start >= '" + new Timestamp(time_start) + "' "
                + "                   AND sm.game_type = 43 "
                + "                   AND st.summoner_id = '" + summonerId + "') t "
                + "    WHERE t.rn = 1) st "
                + "ON s.id = st.summoner_id "
                + "WHERE s.tracking = 1 AND s.id = '" + summonerId + "';");
    }




    public static boolean setSummonerData(int summonerId, int summonerMatchId, MatchParticipant participant, int rank, int lp, int gain, String build) {
        boolean win = participant.didWin();
        int champion = participant.getChampionId();
        String kda = participant.getKills() + "/" + participant.getDeaths() + "/" + participant.getAssists();
        LaneType lane = participant.getChampionSelectLane() != null ? participant.getChampionSelectLane() : participant.getLane();
        TeamType side = participant.getTeam();
        int totalDamage = participant.getTotalDamageDealtToChampions();
        int shield = participant.getTotalHealsOnTeammates() + participant.getTotalDamageShieldedOnTeammates();
        int cs = participant.getTotalMinionsKilled() + participant.getNeutralMinionsKilled();
        int tower = participant.getDamageDealtToBuildings();
        int vision = participant.getVisionScore();
        int ward = participant.getWardsPlaced();
        participant.getGoldEarned();
        participant.getWardsKilled();
        
        HashMap<String, Integer> pings = new HashMap<>();        
        pings.put("push", participant.getPushPings());
        pings.put("bait", participant.getBaitPings());
        pings.put("danger", participant.getDangerPings());
        pings.put("hold", participant.getHoldPings());
        pings.put("all_in", participant.getAllInPings());
        pings.put("basic", participant.getBasicPings());
        pings.put("command", participant.getCommandPings());
        pings.put("get_back", participant.getGetBackPings());
        pings.put("on_my_way", participant.getOnMyWayPings());
        pings.put("assist_me", participant.getAssistMePings());
        pings.put("need_vision", participant.getNeedVisionPings());
        pings.put("enemy_vision", participant.getEnemyVisionPings());
        pings.put("enemy_missing", participant.getEnemyMissingPings());
        pings.put("vision_cleared", participant.getVisionClearedPings());



        return instance.defaultQuery("INSERT IGNORE INTO participant(summoner_id, match_id, win, kda, rank, lp, gain, champion, lane, team, build, damage, damage_building, healing, vision_score, cs, ward, pings, ward_killed, gold_earned, subteam, subteam_placement) VALUES('" + summonerId + "', '" + summonerMatchId + "', '" + (win ? 1 : 0) + "', '" + kda + "', '" + rank + "', '" + lp + "', '" + gain + "', '" + champion + "', '" + lane.ordinal() + "', '" + side.ordinal() + "', '" + build + "', '" + totalDamage + "', '" + tower + "', '" + shield + "', '" + vision + "', '" + cs + "', '" + ward + "', '" + new JSONObject(pings).toString() + "', '" + participant.getWardsKilled() + "', '" + participant.getGoldEarned() + "', '" + participant.getPlayerSubteamId() + "', '" + participant.getSubteamPlacement() + "');");
    }


    public static QueryResult getFocusedSummoners(String query, LeagueShard shard) {
        return instance.query("SELECT riot_id FROM summoner WHERE MATCH(riot_id) AGAINST('+" + query + "*' IN BOOLEAN MODE) AND league_shard = '" + shard.ordinal() + "' LIMIT 25;");
    }


    public static QueryResult getSummonerData(int summoner_id, long game_id) {
        return instance.query("SELECT summoner_id, game_id, rank, lp, gain, win time_start, patch FROM participant WHERE summoner_id = '" + summoner_id + "' AND game_id = '" + game_id + "';");
    }

    public static QueryResult getSummonerData(int summoner_id, LeagueShard shard, long time_start, long time_end) {
        return instance.query("SELECT summoner_id, game_id, rank, lp, gain, win, time_start, time_end, patch FROM participant WHERE summoner_id = '" + summoner_id + "' AND league_shard = '" + shard.ordinal() + "' AND time_start >= '" + new Timestamp(time_start) + "' AND time_end <= '" + new Timestamp(time_end) + "';");
    }

    public static QueryResult getSummonerData(int summoner_id) {
        return instance.query(
            "SELECT st.summoner_id, sm.game_id, st.rank, st.lp, st.gain, st.win, sm.time_start, sm.time_end, sm.patch " +
            "FROM participant st " +
            "JOIN `match` sm ON st.match_id = sm.id " +
            "WHERE st.summoner_id = '" + summoner_id + "' AND sm.game_type = " + GameQueueType.TEAM_BUILDER_RANKED_SOLO.ordinal() + " " +
            "ORDER BY sm.game_id"
        );
    }

    public static boolean hasSummonerData(int sumonerId) {
        return !instance.lineQuery("SELECT 1 from participant where summoner_id = '" + sumonerId + "';").isEmpty();
    }

    public static boolean trackSummoner(String user_id, String account_id, boolean track) {
        return instance.defaultQuery("UPDATE summoner SET tracking = '" + (track ? 1 : 0) + "' WHERE user_id = '" + user_id + "' AND puuid = '" + account_id + "';");
    }

    public static QueryRecord getSummonerData(String user_id, String account_id) {
        return instance.lineQuery("SELECT account_id, summoner_id, league_shard, tracking FROM summoner WHERE user_id = '" + user_id + "' AND puuid = '" + account_id + "';");
    }

    public static QueryResult getSummonersBuPuuid(String puuid) {
        return instance.query("SELECT account_id, league_shard FROM summoner WHERE puuid = '" + puuid + "';");
    }

    public static boolean setChampionData(LOLMatch match, HashMap<String, HashMap<String, String>> matchData) {
       String query = "INSERT INTO summoner_build(game_id, shard, game_type, champion, win, lane, starter, build, first_root, second_root, shard_root, summoner_spells, skill_order, patch, boots) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (String account_id : matchData.keySet()) {
                HashMap<String, String> data = matchData.get(account_id);
                if (data.get("items") == null || data.get("starter") == null || data.get("starter").isBlank()) {
                    continue;
                }
                pstmt.setLong(1, match.getGameId());
                pstmt.setInt(2, match.getPlatform().ordinal());
                pstmt.setInt(3, match.getQueue().ordinal());
                pstmt.setInt(4, Integer.parseInt(data.get("champion")));
                pstmt.setInt(5, data.get("win").equals("1") ? 1 : 0);
                pstmt.setInt(6, Integer.parseInt(data.get("lane")));
                pstmt.setString(7, normalize(data.get("starter")));
                pstmt.setString(8, data.get("items"));
                pstmt.setString(9, data.get("perks-0"));
                pstmt.setString(10, data.get("perks-1"));
                pstmt.setString(11, data.get("stats"));
                pstmt.setString(12, normalize(data.get("summoner_spells")));
                pstmt.setString(13, data.get("skill_order"));
                pstmt.setString(14, match.getGameVersion());
                pstmt.setString(15, data.get("boots"));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int setMatchData(LOLMatch match) {
        return setMatchData(match, false);
    }

    public static boolean setMatchEvent(int matchId, String json) {
        return instance.defaultQuery("UPDATE `match` SET events = '" + json + "' WHERE id = " + matchId + ";");
    }

    @SuppressWarnings("unchecked")
    public static int setMatchData(LOLMatch match, boolean emptyIfExist) {
        int id = 0;

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            PreparedStatement ps = c.prepareStatement("SELECT id FROM `match` WHERE game_id = ? AND league_shard = ?;");
            ps.setString(1, String.valueOf(match.getGameId()));
            ps.setInt(2, match.getPlatform().ordinal());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                id = emptyIfExist ? 0 : rs.getInt("id");
            } else{
                ps = c.prepareStatement("INSERT INTO `match`(game_id, league_shard, game_type, bans, time_start, time_end, patch) VALUES (?,?,?,?,?,?,?);");
                ps.setString(1, String.valueOf(match.getGameId()));
                ps.setInt(2, match.getPlatform().ordinal());
                ps.setInt(3, match.getQueue().ordinal());

                JSONObject bans = new JSONObject();
                for (MatchTeam team : match.getTeams()) {
                    String teamID = team.getTeamId().ordinal() + "";
                    List<Integer> list = new ArrayList<>();
                    for (ChampionBan champion : team.getBans()) {
                        if (champion.getChampionId() != -1) list.add(champion.getChampionId());
                    }
                    bans.put(teamID, list);
                }

                ps.setString(4, bans.toString());
                ps.setTimestamp(5, new Timestamp(match.getGameCreation()));
                ps.setTimestamp(6, new Timestamp(match.getGameEndTimestamp()));
                ps.setString(7, match.getGameVersion());

                ps.executeUpdate();
                id = instance.lineQuery(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
                c.commit();
            }
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
        return id;
    }

    public static QueryResult getMatchData() {
        return instance.query("SELECT sm.id, sm.game_id, sm.league_shard, sm.game_type, sm.bans, sm.time_start, sm.time_end, sm.patch, st.account_id, st.win, st.kda, st.rank, st.lp, st.gain, st.champion, st.lane, st.side, st.build FROM participant st JOIN `match` sm ON st.match_id = sm.id where sm.id > 10353;");
    }

    public static String normalize(String string) {
        String[] parts = string.split(",");

        List<Integer> list = new ArrayList<>();
        for (String part : parts) {
            list.add(Integer.parseInt(part.trim()));
        }

        Collections.sort(list);

        StringBuilder sortedString = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sortedString.append(list.get(i));
            if (i < list.size() - 1) {
                sortedString.append(",");
            }
        }

        return sortedString.toString();
    }

    public static QueryResult getFocusedCustomBuild(String name){
        return instance.query("SELECT name, id FROM custom_build WHERE name LIKE '%" + name + "%' ORDER BY RAND() LIMIT 25;");
    }

    public static CustomBuildData getCustomBuild(String id){
        return new CustomBuildData(instance.lineQuery("SELECT id, name, skin, description, user_id, build, champion, lane, created_at FROM custom_build WHERE id = " + id + ""));
    }

    public static QueryResult getCustomBuildByUser(String user_id){
        return instance.query("SELECT id, name, user_id, build, champion, lane, created_at FROM custom_build WHERE user_id = '" + user_id + "'");
    }

    public static boolean updateSummonerMasteries(int summonerId, List<ChampionMastery> masteries) {
        String query = "INSERT INTO masteries (summoner_id, champion_id, champion_level, champion_points, last_play_time) " +
                       "VALUES (?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "champion_level = VALUES(champion_level), " +
                       "champion_points = VALUES(champion_points), " +
                       "last_play_time = VALUES(last_play_time);";

        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (ChampionMastery mastery : masteries) {
                pstmt.setInt(1, summonerId);
                pstmt.setLong(2, mastery.getChampionId());
                pstmt.setInt(3, mastery.getChampionLevel());
                pstmt.setInt(4, mastery.getChampionPoints());
                pstmt.setTimestamp(5, new Timestamp(mastery.getLastPlayTime()));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int getSummonerIdByPuuid(String puuid, LeagueShard shard) {
        try {
            return instance.lineQuery("select id from summoner where puuid = '"+puuid+"' and league_shard = " + shard.ordinal() + "").getAsInt("id");  
        } catch (Exception e) {
           return 0;
        }
    }

    public static boolean updateSummonerEntries(int summonerId, List<LeagueEntry> entries) {
        String query = "INSERT INTO `rank` (summoner_id, game_type, rank, lp, wins, losses) " +
                       "VALUES (?, ?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "rank = VALUES(rank), " +
                       "lp = VALUES(lp), " +
                       "wins = VALUES(wins), " +
                       "losses = VALUES(losses);";
        Connection conn = null;
        try {
            conn = instance.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                for (LeagueEntry entry : entries) {
                    pstmt.setInt(1, summonerId);
                    pstmt.setInt(2, entry.getQueueType().ordinal());
                    pstmt.setInt(3, entry.getTierDivisionType().ordinal());
                    pstmt.setInt(4, entry.getLeaguePoints());
                    pstmt.setInt(5, entry.getWins());
                    pstmt.setInt(6, entry.getLosses());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
