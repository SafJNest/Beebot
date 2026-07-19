package com.safjnest.sql.database;

import java.sql.Connection;
import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
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

import com.safjnest.lol.message.LeagueMessageParameter;
import com.safjnest.lol.message.LeagueMessageType;
import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.leaderboard.LeaderboardRow;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ProfileStatisticsRow;
import com.safjnest.lol.model.summoner.Rank;
import com.safjnest.lol.utils.ParticipantBuildCodec;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.lol.service.LeagueService;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.TierDivisionUtils;
import com.safjnest.mongo.MongoDB;
import com.safjnest.sql.AbstractDB;
import com.safjnest.sql.QueryResult;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.utils.log.BotLogger;
import com.safjnest.sql.QueryRecord;

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

    public record LeaderboardData(long total, List<LeaderboardRow> rows, boolean success) {}

    @FunctionalInterface
    private interface LeaderboardQuery {
        LeaderboardData execute(Connection conn) throws SQLException;
    }

    public static QueryResult getLOLAccountsByUserId(String user_id){
        String query = "SELECT puuid, region, tracking FROM summoner WHERE user_id = '" + user_id + "' order by id;";
        return instance.query(query);
    }

    public static String getSummonerNameById(String puuid, LeagueShard shard) {
        return instance.lineQuery("SELECT riot_id FROM summoner WHERE puuid = '" + puuid + "' AND region = '" + shard + "';").get("riot_id");
    }

    public static String getUserIdByLOLAccountId(String puuid, LeagueShard shard) {
        return instance.lineQuery("SELECT user_id FROM summoner WHERE puuid = '" + puuid + "' AND region = '" + shard + "';").get("user_id");
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
        return instance.query("SELECT sm.game_id, sm.queue, st.win " +
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
            queueFilter = "AND sm.queue = '" + queue + "' ";
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

    public static int addLOLAccount(LeagueEntry entry, LeagueShard shard) {
        int id = getSummonerIdByPuuid(entry.getPuuid(), shard);
        if (id != 0) {
            return id;
        }
        try {
            Thread.sleep(350);
        } catch (Exception e) { }
        Summoner summoner = LeagueService.getSummonerByPuuid(entry.getPuuid(), shard);
        if (summoner == null) {
            return 0;
        }
        return addLOLAccount(summoner);
    }

    public static int addLOLAccount(String user_id, Summoner summoner) {
        RiotAccount account = LeagueService.getRiotAccountFromSummoner(summoner);
        String query = "INSERT INTO summoner(user_id, puuid, riot_id, region, icon, level) " +
                "VALUES(?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "id = LAST_INSERT_ID(id), " +
                "user_id = IF(VALUES(user_id) IS NOT NULL, VALUES(user_id), user_id), " +
                "puuid = VALUES(puuid), " +
                "riot_id = VALUES(riot_id), " +
                "region = VALUES(region), " +
                "icon = VALUES(icon), " +
                "level = VALUES(level);";

        try (Connection conn = instance.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            if (user_id != null) 
                pstmt.setString(1, user_id);
            else 
                pstmt.setNull(1, java.sql.Types.VARCHAR);
            
            pstmt.setString(2, summoner.getPUUID());
            if (account != null) 
                pstmt.setString(3, account.getName() + "#" + account.getTag());
            else
                pstmt.setNull(3, java.sql.Types.VARCHAR);
            pstmt.setString(4, summoner.getPlatform().name());
            pstmt.setInt(5, summoner.getProfileIconId());
            pstmt.setInt(6, summoner.getSummonerLevel());

            pstmt.executeUpdate();
            
            ResultSet rs = pstmt.getGeneratedKeys();
            int id = 0;
            if (rs.next()) {
                id = rs.getInt(1);
            }
            
            conn.commit();
            MongoDB.mirrorSummoner(summoner.getPUUID(), summoner.getPlatform(), account == null ? null : account.getName() + "#" + account.getTag(), summoner.getSummonerLevel(), summoner.getProfileIconId());
            return id;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static boolean addLOLAccount(SpectatorGameInfo info) {
        String query = "INSERT INTO summoner(puuid, riot_id, region, icon) " +
                       "VALUES(?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "puuid = VALUES(puuid), " +
                       "riot_id = VALUES(riot_id), " +
                       "region = VALUES(region), " +
                       "icon = VALUES(icon);";

        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (SpectatorParticipant summoner : info.getParticipants()) {
                if (summoner.getPuuid() == null) continue;
                pstmt.setString(1, summoner.getPuuid());
                pstmt.setString(2, summoner.getRiotId());
                pstmt.setString(3, info.getPlatform().name());
                pstmt.setLong(4, summoner.getProfileIconId());
                pstmt.addBatch();
            }

            int[] affectedRows = pstmt.executeBatch();
            conn.commit();
            for (SpectatorParticipant summoner : info.getParticipants())
                MongoDB.mirrorSummoner(summoner.getPuuid(), info.getPlatform(), summoner.getRiotId(), 0, (int) summoner.getProfileIconId());
            return affectedRows.length == info.getParticipants().size();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addLOLAccountFromMatch(LOLMatch match) {
        String query = "INSERT INTO summoner(puuid, riot_id, region, icon, level) " +
                       "VALUES(?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "puuid = VALUES(puuid), " +
                       "riot_id = VALUES(riot_id), " +
                       "region = VALUES(region), " +
                       "icon = VALUES(icon), " +
                       "level = VALUES(level);";

        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            for (MatchParticipant summoner : match.getParticipants()) {
                pstmt.setString(1, summoner.getPuuid());
                pstmt.setString(2, summoner.getRiotIdName() + "#" + summoner.getRiotIdTagline());
                pstmt.setString(3, match.getPlatform().name());
                pstmt.setInt(4, summoner.getProfileIcon());
                pstmt.setInt(5, summoner.getSummonerLevel());
                pstmt.addBatch();
            }

            int[] affectedRows = pstmt.executeBatch();
            conn.commit();
            for (MatchParticipant summoner : match.getParticipants())
                MongoDB.mirrorSummoner(summoner.getPuuid(), match.getPlatform(), summoner.getRiotIdName() + "#" + summoner.getRiotIdTagline(), summoner.getSummonerLevel(), summoner.getProfileIcon());
            return affectedRows.length == match.getParticipants().size();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteLOLaccount(String user_id, String puuid){
        String query = "UPDATE summoner SET tracking = 0, user_id = NULL WHERE user_id = '" + user_id + "' AND puuid = '" + puuid + "';";
        boolean saved = instance.defaultQuery(query);
        if (saved) MongoDB.detachSummoner(user_id, puuid);
        return saved;
    }

     public static QueryResult getRegistredLolAccount(long time_start) {
        return instance.query(
            "SELECT s.puuid, s.region, st.game_id, st.rank, st.lp, st.time_start "
            + "FROM summoner s "
            + "LEFT JOIN ("
            + "    SELECT t.summoner_id, t.game_id, t.rank, t.lp, t.time_start "
            + "    FROM ("
            + "        SELECT st.summoner_id, sm.game_id, st.rank, st.lp, sm.time_start, "
            + "        ROW_NUMBER() OVER (PARTITION BY st.summoner_id ORDER BY sm.time_start DESC) AS rn "
            + "        FROM participant st "
            + "        JOIN `match` sm ON st.match_id = sm.id "
            + "        WHERE sm.time_start >= '" + new Timestamp(time_start) + "' "
            + "        AND sm.queue = 'TEAM_BUILDER_RANKED_SOLO' "
            + "    ) t "
            + "    WHERE t.rn = 1"
            + ") st ON s.id = st.summoner_id "
            + "WHERE s.tracking = 1;"
        );
    }





    public static QueryRecord getRegistredLolAccount(int summonerId, long time_start) {
        return instance.lineQuery("SELECT s.puuid, s.region, st.game_id, st.rank, st.lp, st.time_start "
                + "FROM summoner s "
                + "LEFT JOIN (SELECT t.summoner_id, t.game_id, t.rank, t.lp, t.time_start "
                + "           FROM (SELECT st.summoner_id, sm.game_id, st.rank, st.lp, sm.time_start, "
                + "                        ROW_NUMBER() OVER (PARTITION BY st.summoner_id ORDER BY sm.time_start DESC) AS rn "
                + "                 FROM participant st "
                + "                 JOIN `match` sm ON st.match_id = sm.id "
                + "                 WHERE sm.time_start >= '" + new Timestamp(time_start) + "' "
                + "                   AND sm.queue = 'TEAM_BUILDER_RANKED_SOLO' "
                + "                   AND st.summoner_id = '" + summonerId + "') t "
                + "    WHERE t.rn = 1) st "
                + "ON s.id = st.summoner_id "
                + "WHERE s.tracking = 1 AND s.id = '" + summonerId + "';");
    }




    public static boolean setSummonerData(String puuid, int summonerId, int summonerMatchId, MatchParticipant participant, TierDivisionType rank, int lp, int gain, String build) {
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

        int q = participant.getSpell1Casts();
        int w = participant.getSpell2Casts();
        int e = participant.getSpell3Casts();
        int r = participant.getSpell4Casts();
        int d = participant.getSummoner1Casts();
        int f = participant.getSummoner2Casts();
        
        
        String insert = "INSERT IGNORE INTO participant(summoner_id, match_id, win, kda, rank, lp, gain, champion, lane, team, build, damage, damage_building, healing, vision_score, cs, ward, pings, ward_killed, gold_earned, subteam, subteam_placement, level, doubles, triples, quadruples, pentas, role_quest_id, q, w, e, r, d, f) VALUES('" + summonerId + "', '" + summonerMatchId + "', '" + (win ? 1 : 0) + "', '" + kda + "', '" + rank + "', '" + lp + "', '" + gain + "', '" + champion + "', '" + lane + "', '" + side + "', '" + build + "', '" + totalDamage + "', '" + tower + "', '" + shield + "', '" + vision + "', '" + cs + "', '" + ward + "', '" + new JSONObject(pings).toString() + "', '" + participant.getWardsKilled() + "', '" + participant.getGoldEarned() + "', '" + participant.getPlayerSubteamId() + "', '" + participant.getSubteamPlacement() + "', " + participant.getChampionLevel() + ", " + participant.getDoubleKills() + ", " + participant.getTripleKills() + ", " + participant.getQuadraKills() + ", " + participant.getPentaKills() + ", " + participant.getRoleBoundItem() + ", " + q + ", " + w + ", " + e + ", " + r + ", " + d + ", " + f + ");";
        String update = "UPDATE participant SET rank = '" + rank + "', lp = '" + lp + "', gain = '" + gain + "' WHERE summoner_id = '" + summonerId + "' AND match_id = '" + summonerMatchId + "';";
        boolean saved = instance.defaultQuery(insert, update);
        if (saved) MongoDB.mirrorParticipant(puuid, summonerMatchId);
        return saved;
    }


    public static QueryResult getFocusedSummoners(String query, LeagueShard shard) {
        String sql =
            "SELECT riot_id, puuid FROM summoner " +
            "WHERE riot_search LIKE CONCAT(?, '%') AND region = ? " +
            "LIMIT 25";

        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, query);
                pstmt.setString(2, shard.name());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static QueryResult searchSummoners(String query, LeagueShard shard) {
        String sql =
            "SELECT s.id AS summoner_id, s.puuid, s.riot_id, s.region, s.level, s.icon " +
            "FROM summoner s " +
            "WHERE s.region = ? AND s.riot_search LIKE CONCAT(?, '%') " +
            "ORDER BY s.riot_id " +
            "LIMIT 25";

        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, shard.name());
                pstmt.setString(2, query);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static LeaderboardData getLeaderboardData(
        String rankTier, String queue, String region, long offset, int limit
    ) {
        return executeLeaderboardQuery(conn -> {
            long total = leaderboardDistributionTotal(conn, queue, rankTier, region);
            List<LeaderboardRow> rows = total > offset
                ? getLeaderboard(conn, queue, rankTier, region, offset, limit)
                : List.of();
            return new LeaderboardData(total, rows, true);
        });
    }

    public static LeaderboardData getLeaderboardTotal(
        String rankTier, String queue, String region
    ) {
        return executeLeaderboardQuery(conn -> new LeaderboardData(
            leaderboardDistributionTotal(conn, queue, rankTier, region),
            List.of(),
            true
        ));
    }

    public static LeaderboardData getLeaderboardRows(
        String rankTier, String queue, String region, long offset, int limit
    ) {
        return executeLeaderboardQuery(conn -> new LeaderboardData(
            0, getLeaderboard(conn, queue, rankTier, region, offset, limit), true
        ));
    }

    private static LeaderboardData executeLeaderboardQuery(LeaderboardQuery query) {
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return new LeaderboardData(0, List.of(), false);
            conn.setAutoCommit(false);
            try {
                LeaderboardData result = query.execute(conn);
                conn.commit();
                return result;
            } catch (SQLException exception) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                exception.printStackTrace();
                return new LeaderboardData(0, List.of(), false);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
            return new LeaderboardData(0, List.of(), false);
        }
    }

    private static List<LeaderboardRow> getLeaderboard(
        Connection conn, String queue, String rankTier, String region, long offset, int limit
    ) throws SQLException {
        String sql = "SELECT r.summoner_id, r.region, r.`rank`, r.lp, r.wins, r.losses "
            + "FROM `rank` r "
            + "WHERE r.queue = ? ";
        List<String> ranks = rankTier == null ? List.of() : rankValues(rankTier);
        if (rankTier != null) {
            sql += "AND r.`rank` IN (" + placeholders(ranks.size()) + ")";
        }
        if (!"GLOBAL".equals(region)) sql += " AND r.region = ?";
        sql += " ORDER BY r.mmr DESC LIMIT ? OFFSET ?";

        List<QueryRecord> rankRows = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int parameter = 1;
            pstmt.setString(parameter++, queue);
            if (rankTier != null) parameter = bindValues(pstmt, ranks, parameter);
            if (!"GLOBAL".equals(region)) pstmt.setString(parameter++, region);
            pstmt.setInt(parameter++, limit);
            pstmt.setLong(parameter, offset);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) rankRows.add(toRecord(rs));
            }
        }

        if (rankRows.isEmpty()) return List.of();

        List<Integer> summonerIds = new ArrayList<>(rankRows.size());
        for (QueryRecord rankRow : rankRows) summonerIds.add(rankRow.getAsInt("summoner_id"));
        String summonerSql = "SELECT id AS summoner_id, puuid, riot_id, region, level, icon "
            + "FROM summoner WHERE id IN (" + placeholders(summonerIds.size()) + ")";
        Map<Integer, QueryRecord> summonerRows = new HashMap<>();
        try (PreparedStatement pstmt = conn.prepareStatement(summonerSql)) {
            int parameter = 1;
            for (int summonerId : summonerIds) pstmt.setInt(parameter++, summonerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    QueryRecord row = toRecord(rs);
                    summonerRows.put(row.getAsInt("summoner_id"), row);
                }
            }
        }

        List<LeaderboardRow> result = new ArrayList<>(rankRows.size());
        for (QueryRecord rankRow : rankRows) {
            QueryRecord summonerRow = summonerRows.get(rankRow.getAsInt("summoner_id"));
            if (summonerRow == null) continue;
            result.add(toLeaderboardRow(rankRow, summonerRow, queue));
        }
        return result;
    }

    private static LeaderboardRow toLeaderboardRow(QueryRecord rankRow, QueryRecord summonerRow, String queue) {
        com.safjnest.lol.model.summoner.Summoner summoner = new com.safjnest.lol.model.summoner.Summoner(
            summonerRow.getAsInt("summoner_id"), summonerRow.get("puuid"), summonerRow.get("riot_id"),
            summonerRow.get("region"), summonerRow.getAsInt("level"), summonerRow.getAsInt("icon")
        );
        Rank rank = new Rank(
            enumValue(GameQueueType.class, queue), enumValue(TierDivisionType.class, rankRow.get("rank")),
            rankRow.getAsInt("lp"), rankRow.getAsInt("wins"), rankRow.getAsInt("losses")
        );
        return new LeaderboardRow(summoner, rank);
    }

    private static long leaderboardDistributionTotal(Connection conn, String queue, String rankTier, String region) throws SQLException {
        String sql = "SELECT COALESCE(SUM(players), 0) AS total "
            + "FROM leaderboard_distribution WHERE queue = ?";
        if (rankTier != null) sql += " AND `rank` = ?";
        if (!"GLOBAL".equals(region)) sql += " AND region = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int parameter = 1;
            pstmt.setString(parameter++, queue);
            if (rankTier != null) pstmt.setString(parameter++, rankTier);
            if (!"GLOBAL".equals(region)) pstmt.setString(parameter, region);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong("total") : 0;
            }
        }
    }

    public static QueryResult getLeaderboardDistribution(String queue, String region) {
        String sql = "SELECT `rank`, SUM(players) AS players "
            + "FROM leaderboard_distribution WHERE queue = ?";
        if (!"GLOBAL".equals(region)) sql += " AND region = ?";
        sql += " GROUP BY `rank` ORDER BY FIELD(`rank`, 'CHALLENGER', 'GRANDMASTER', 'MASTER', 'DIAMOND', "
            + "'EMERALD', 'PLATINUM', 'GOLD', 'SILVER', 'BRONZE', 'IRON')";

        return leaderboardDistributionQuery(sql, queue, "GLOBAL".equals(region) ? null : region);
    }

    public static QueryResult getLeaderboardTopRegions(String queue, String rankTier) {
        String sql = "SELECT region, SUM(players) AS players FROM leaderboard_distribution "
            + "WHERE queue = ? AND `rank` = ? GROUP BY region ORDER BY players DESC, region ASC";
        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, queue);
                pstmt.setString(2, rankTier);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static boolean rebuildLeaderboardDistribution() {
        String deleteSql = "DELETE FROM leaderboard_distribution";

        try (Connection conn = instance.getConnection()) {
            if (conn == null) return false;
            conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            conn.setAutoCommit(false);
            try (PreparedStatement delete = conn.prepareStatement(deleteSql)) {
                List<GameQueueType> queues = leaderboardQueues(conn);
                if (queues.isEmpty()) return false;
                List<TierType> tiers = competitiveTiers();
                delete.executeUpdate();
                for (GameQueueType queue : queues) {
                    for (TierType tier : tiers) {
                        for (LeagueShard region : LeagueShard.values()) {
                            if (region != LeagueShard.UNKNOWN) {
                                buildLeaderboardDistributionCombination(conn, queue, tier, region);
                            }
                        }
                    }
                }
                conn.commit();
                try {
                    MongoDB.rebuildLeaderboardDistribution();
                } catch (RuntimeException exception) {
                    BotLogger.error("Mongo mirror failed operation=leaderboard.rebuild collection=leaderboard_distribution id=all error=" + exception.getMessage());
                }
                return true;
            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static List<GameQueueType> leaderboardQueues(Connection conn) throws SQLException {
        List<GameQueueType> queues = new ArrayList<>();
        String queueSql = "SELECT DISTINCT queue FROM `rank` WHERE queue IS NOT NULL";
        try (PreparedStatement queuesQuery = conn.prepareStatement(queueSql);
             ResultSet result = queuesQuery.executeQuery()) {
            while (result.next()) {
                String value = result.getString("queue");
                GameQueueType queue = enumValue(GameQueueType.class, value);
                queue = GameQueueTypeUtils.canonicalQueue(queue);
                if (queue != null && !queues.contains(queue)) queues.add(queue);
            }
        }
        return queues;
    }

    private static void buildLeaderboardDistributionCombination(
        Connection conn, GameQueueType queue, TierType tier, LeagueShard region
    ) throws SQLException {
        List<String> ranks = tierDivisionRanks(tier);
        String sql = "INSERT INTO leaderboard_distribution (queue, `rank`, region, players, updated_at) "
            + "SELECT ?, ?, ?, COUNT(DISTINCT r.summoner_id), CURRENT_TIMESTAMP(3) "
            + "FROM `rank` r "
            + "WHERE r.region = ? AND r.queue = ? "
            + "AND r.`rank` IN (" + placeholders(ranks.size()) + ") "
            + "ON DUPLICATE KEY UPDATE players = VALUES(players), updated_at = VALUES(updated_at)";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            int parameter = 1;
            statement.setString(parameter++, queue.name());
            statement.setString(parameter++, tier.name());
            statement.setString(parameter++, region.name());
            statement.setString(parameter++, region.name());
            statement.setString(parameter++, queue.name());
            for (String rank : ranks) statement.setString(parameter++, rank);
            statement.executeUpdate();
        }
    }

    private static List<String> tierDivisionRanks(TierType tier) {
        List<String> ranks = new ArrayList<>();
        for (TierDivisionType division : TierDivisionType.values()) {
            if (tier.name().equals(division.getTier())) ranks.add(division.name());
        }
        return ranks;
    }

    private static List<TierType> competitiveTiers() {
        List<TierType> tiers = new ArrayList<>();
        for (TierType tier : TierType.values()) {
            if (tier != TierType.UNRANKED) tiers.add(tier);
        }
        return tiers;
    }

    private static QueryResult leaderboardDistributionQuery(String sql, String queue, String region) {
        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, queue);
                if (region != null) pstmt.setString(2, region);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static int bindValues(PreparedStatement pstmt, List<String> values, int parameter) throws SQLException {
        for (String value : values) pstmt.setString(parameter++, value);
        return parameter;
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static List<String> rankValues(String rankTier) {
        List<String> values = new ArrayList<>();
        values.add(rankTier);
        for (TierDivisionType division : TierDivisionType.values()) {
            if (rankTier.equals(division.getTier())) values.add(division.name());
        }
        return values;
    }

    public static QueryRecord getProfileBase(String puuid, LeagueShard shard) {
        String sql =
            "SELECT s.id AS summoner_id, s.puuid, s.riot_id, s.region, s.level, s.icon " +
            "FROM summoner s WHERE s.puuid = ? AND s.region = ? " +
            "LIMIT 1";

        try (Connection conn = instance.getConnection()) {
            if (conn == null) return new QueryRecord();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, puuid);
                pstmt.setString(2, shard.name());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return toRecord(rs);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new QueryRecord();
    }

    public static QueryRecord getProfileRank(int summonerId) {
        String sql =
            "SELECT r.queue, COALESCE(r.`rank`, 'UNRANKED') AS rank, COALESCE(r.lp, 0) AS lp, " +
            "COALESCE(r.wins, 0) AS wins, COALESCE(r.losses, 0) AS losses " +
            "FROM `rank` r " +
            "WHERE r.summoner_id = ? AND r.queue = ? " +
            "LIMIT 1";

        try (Connection conn = instance.getConnection()) {
            if (conn == null) return new QueryRecord();
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, summonerId);
                pstmt.setString(2, GameQueueType.RANKED_SOLO_5X5.name());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) return toRecord(rs);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new QueryRecord();
    }

    public static QueryResult getProfileRanks(int summonerId) {
        String sql = "SELECT r.queue, COALESCE(r.`rank`, 'UNRANKED') AS rank, COALESCE(r.lp, 0) AS lp, " +
            "COALESCE(r.wins, 0) AS wins, COALESCE(r.losses, 0) AS losses " +
            "FROM `rank` r WHERE r.summoner_id = ? ORDER BY r.queue";
        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, summonerId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static Map<Integer, QueryRecord> getProfileRanks(List<Integer> summonerIds) {
        Map<Integer, QueryRecord> result = new HashMap<>();
        if (summonerIds == null || summonerIds.isEmpty()) return result;

        String sql = "SELECT r.summoner_id, r.queue, COALESCE(r.`rank`, 'UNRANKED') AS rank, " +
            "COALESCE(r.lp, 0) AS lp, COALESCE(r.wins, 0) AS wins, COALESCE(r.losses, 0) AS losses " +
            "FROM `rank` r WHERE r.summoner_id IN (" + placeholders(summonerIds.size()) + ") " +
            "AND r.queue = ?";

        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                int parameter = 1;
                for (int summonerId : summonerIds) pstmt.setInt(parameter++, summonerId);
                pstmt.setString(parameter, GameQueueType.RANKED_SOLO_5X5.name());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        QueryRecord row = toRecord(rs);
                        result.putIfAbsent(row.getAsInt("summoner_id"), row);
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static QueryResult getProfileMasteries(int summonerId) {
        String sql = "SELECT champion_id, champion_level, champion_points FROM masteries WHERE summoner_id = ?";
        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, summonerId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static ProfileStatisticsRow getProfileStatistics(String key) {
        String sql = "SELECT time_start, time_end, data FROM profile_statistics WHERE `key` = ?";
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return null;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, key);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        QueryRecord row = toRecord(rs);
                        return new ProfileStatisticsRow(
                            timeMs(row.get("time_start")), timeMs(row.get("time_end")), row.get("data")
                        );
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Map<String, ProfileStatisticsRow> getProfileStatistics(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Map.of();

        String sql = "SELECT `key`, time_start, time_end, data FROM profile_statistics WHERE `key` IN ("
            + placeholders(keys.size()) + ")";
        Map<String, ProfileStatisticsRow> result = new HashMap<>();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                bindValues(pstmt, keys, 1);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        QueryRecord row = toRecord(rs);
                        result.put(row.get("key"), new ProfileStatisticsRow(
                            timeMs(row.get("time_start")), timeMs(row.get("time_end")), row.get("data")
                        ));
                    }
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void deleteProfileStatistics(String key) {
        if (key == null || key.isBlank()) return;

        String sql = "DELETE FROM profile_statistics WHERE `key` = ?";
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return;
            QueryRecord profile = instance.lineQuery("SELECT s.puuid, p.time_start FROM profile_statistics p JOIN summoner s ON s.id = p.summoner_id WHERE p.`key` = '" + key.replace("'", "''") + "' LIMIT 1");
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, key);
                pstmt.executeUpdate();
            }
            conn.commit();
            if (profile != null && profile.get("puuid") != null) MongoDB.deleteProfileStatistics(profile.get("puuid"), timeMs(profile.get("time_start")));
        } catch (SQLException ignored) {}
    }

    public static boolean saveProfileStatistics(String key, String puuid, int summonerId, long timeStart, long timeEnd, byte[] data) {
        String sql = "INSERT INTO profile_statistics (`key`, summoner_id, time_start, time_end, data) VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE time_end = VALUES(time_end), data = VALUES(data)";
        try (Connection conn = instance.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) return false;
            pstmt.setString(1, key);
            pstmt.setInt(2, summonerId);
            pstmt.setTimestamp(3, new Timestamp(timeStart));
            pstmt.setTimestamp(4, new Timestamp(timeEnd));
            pstmt.setBytes(5, data);
            pstmt.executeUpdate();
            conn.commit();
            MongoDB.saveProfileStatistics(key, puuid, timeStart, timeEnd, data);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** One flat query for tracked matches, roster details, and kill participation. */
    public static QueryResult getProfileMatchesAfter(int summonerId, long afterTimeEnd, long untilTimeEnd) {
        String sql =
            "SELECT m.game_id, m.queue, m.time_start, m.time_end, " +
            "p.win, p.kda, p.champion, p.lane, p.damage, p.cs, p.gold_earned, p.vision_score, p.build, " +
            "p.team AS player_team, p.subteam AS player_subteam, " +
            "roster_member.champion AS participant_champion, roster_summoner.puuid AS participant_puuid, " +
            "roster_member.kda AS participant_kda, roster_member.team AS participant_team, roster_member.subteam AS participant_subteam " +
            "FROM participant p " +
            "JOIN `match` m ON m.id = p.match_id " +
            "JOIN participant roster_member ON roster_member.match_id = p.match_id " +
            "LEFT JOIN summoner roster_summoner ON roster_summoner.id = roster_member.summoner_id " +
            "WHERE p.summoner_id = ? AND m.time_end > ? AND m.time_end <= ? " +
            "ORDER BY m.time_end ASC, roster_member.id ASC";
        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, summonerId);
                pstmt.setTimestamp(2, new Timestamp(afterTimeEnd));
                pstmt.setTimestamp(3, new Timestamp(untilTimeEnd));
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static QueryResult getProfileSeasonSummoners(LeagueShard shard, long seasonStart, long seasonEnd) {
        String sql = "SELECT DISTINCT s.puuid FROM summoner s JOIN participant p ON p.summoner_id = s.id " +
            "JOIN `match` m ON m.id = p.match_id WHERE s.region = ? AND m.time_start >= ? AND m.time_start <= ?";
        QueryResult result = new QueryResult();
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, shard.name());
                pstmt.setTimestamp(2, new Timestamp(seasonStart));
                pstmt.setTimestamp(3, new Timestamp(seasonEnd));
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) result.add(toRecord(rs));
                }
            }
            conn.commit();
            result.setSuccess(true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }


    public static QueryResult getSummonerData(int summoner_id, long game_id) {
        return instance.query("SELECT summoner_id, game_id, rank, lp, gain, win time_start, patch FROM participant WHERE summoner_id = '" + summoner_id + "' AND game_id = '" + game_id + "';");
    }

    public static QueryResult getSummonerData(int summoner_id, LeagueShard shard, long time_start, long time_end) {
        return instance.query("SELECT summoner_id, game_id, rank, lp, gain, win, time_start, time_end, patch FROM participant WHERE summoner_id = '" + summoner_id + "' AND region = '" + shard + "' AND time_start >= '" + new Timestamp(time_start) + "' AND time_end <= '" + new Timestamp(time_end) + "';");
    }

    public static QueryResult getSummonerData(String puuid, LeagueShard shard) {
        return instance.query(
            "SELECT st.summoner_id, sm.game_id, st.rank, st.lp, st.gain, st.win, sm.time_start, sm.time_end, sm.patch " +
            "FROM participant st " +
            "JOIN `match` sm ON st.match_id = sm.id " +
            "JOIN summoner s ON st.summoner_id = s.id " +
            "WHERE s.puuid = '" + puuid + "' AND s.region = '" + shard + "' AND sm.queue = 'TEAM_BUILDER_RANKED_SOLO' " +
            "ORDER BY sm.game_id"
        );
    }

    public static boolean hasSummonerData(int sumonerId) {
        return !instance.lineQuery("SELECT 1 from participant where summoner_id = '" + sumonerId + "';").isEmpty();
    }

    public static boolean trackSummoner(String user_id, String puuid, boolean track) {
        boolean saved = instance.defaultQuery("UPDATE summoner SET tracking = '" + (track ? 1 : 0) + "' WHERE user_id = '" + user_id + "' AND puuid = '" + puuid + "';");
        if (saved) MongoDB.mirrorTracking(user_id, puuid, track);
        return saved;
    }

    public static int saveMatch(LOLMatch match) {
        return saveMatch(match, false);
    }

    public static boolean setMatchEvent(int matchId, String json) {
        boolean saved = instance.defaultQuery("UPDATE `match` SET events = '" + json + "' WHERE id = " + matchId + ";");
        if (saved) MongoDB.mirrorMatchEvents(matchId, json);
        return saved;
    }

    public static boolean setMatchRank(int matchId, TierType rank) {
        boolean saved = instance.defaultQuery("UPDATE `match` SET rank = '" + rank + "' WHERE id = " + matchId + ";");
        if (saved) MongoDB.mirrorMatchRank(matchId, rank);
        return saved;
    }

    public static int saveMatch(LOLMatch match, boolean emptyIfExist) {
        int id = 0;

        Connection c = instance.getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            PreparedStatement ps = c.prepareStatement("SELECT id FROM `match` WHERE game_id = ? AND region = ?;");
            ps.setString(1, String.valueOf(match.getGameId()));
            ps.setString(2, match.getPlatform().name());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                id = emptyIfExist ? 0 : rs.getInt("id");
            } else{
                ps = c.prepareStatement("INSERT INTO `match`(game_id, region, queue, bans, time_start, time_end, patch) VALUES (?,?,?,?,?,?,?);");
                ps.setString(1, String.valueOf(match.getGameId()));
                ps.setString(2, match.getPlatform().name());
                ps.setString(3, match.getQueue().name());

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
        if (id != 0) MongoDB.mirrorMatch(id);
        return id;
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
        return instance.query("SELECT name, id FROM custom_build WHERE LOCATE('" + name + "', name) > 0 ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getCustomBuildByUser(String user_id){
        return instance.query("SELECT id, name, user_id, build, champion, lane, created_at FROM custom_build WHERE user_id = '" + user_id + "'");
    }

    public static boolean updateSummonerMasteries(String puuid, int summonerId, List<ChampionMastery> masteries) {
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
            List<com.safjnest.lol.model.summoner.Mastery> mirroredMasteries = new ArrayList<>();
            for (ChampionMastery mastery : masteries) mirroredMasteries.add(new com.safjnest.lol.model.summoner.Mastery(
                    mastery.getChampionId(), mastery.getChampionLevel(), mastery.getChampionPoints()));
            QueryRecord summoner = instance.lineQuery("SELECT region FROM summoner WHERE id = " + summonerId);
            if (summoner != null) MongoDB.mirrorMasteries(puuid, summoner.getAsLeagueShard("region"), mirroredMasteries);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static int getSummonerIdByPuuid(String puuid, LeagueShard shard) {
        try {
            return instance.lineQuery("select id from summoner where puuid = '"+ puuid +"' and region = '" + shard + "'").getAsInt("id");  
        } catch (Exception e) {
           return 0;
        }
    }

    public static boolean updateSummonerEntries(String puuid, int summonerId, List<LeagueEntry> entries, LeagueShard shard) {
        String query = "INSERT INTO `rank` (summoner_id, region, queue, rank, lp, mmr, wins, losses) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "region = VALUES(region), " +
                       "rank = VALUES(rank), " +
                       "queue = VALUES(queue), " +
                       "lp = VALUES(lp), " +
                       "mmr = VALUES(mmr), " +
                       "wins = VALUES(wins), " +
                       "losses = VALUES(losses);";
        Connection conn = null;
        try {
            conn = instance.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                for (LeagueEntry entry : entries) {
                    pstmt.setInt(1, summonerId);
                    pstmt.setString(2, shard.name());
                    pstmt.setString(3, GameQueueTypeUtils.canonicalQueue(entry.getQueueType()).name());
                    pstmt.setString(4, entry.getTierDivisionType().name());
                    pstmt.setInt(5, entry.getLeaguePoints());
                    pstmt.setInt(6, TierDivisionUtils.getMmr(entry.getTierDivisionType(), entry.getLeaguePoints()));
                    pstmt.setInt(7, entry.getWins());
                    pstmt.setInt(8, entry.getLosses());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
                conn.commit();
                List<Rank> ranks = new ArrayList<>();
                for (LeagueEntry entry : entries) ranks.add(new Rank(entry.getQueueType(), entry.getTierDivisionType(), entry.getLeaguePoints(), entry.getWins(), entry.getLosses()));
                MongoDB.mirrorRanks(puuid, shard, ranks);
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

    public static boolean updateSummonerEntries(List<LeagueEntry> entries, LeagueShard shard) {
        String query = "INSERT INTO `rank` (summoner_id, region, queue, rank, lp, mmr, wins, losses) " +
                       "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE " +
                       "region = VALUES(region), " +
                       "rank = VALUES(rank), " +
                       "queue = VALUES(queue), " +
                       "lp = VALUES(lp), " +
                       "mmr = VALUES(mmr), " +
                       "wins = VALUES(wins), " +
                       "losses = VALUES(losses);";
        Connection conn = null;
        try {
            conn = instance.getConnection();
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                for (LeagueEntry entry : entries) {
                    int summonerId = addLOLAccount(entry, shard);
                    pstmt.setInt(1, summonerId);
                    pstmt.setString(2, shard.name());
                    pstmt.setString(3, GameQueueTypeUtils.canonicalQueue(entry.getQueueType()).name());
                    pstmt.setString(4, entry.getTierDivisionType().name());
                    pstmt.setInt(5, entry.getLeaguePoints());
                    pstmt.setInt(6, TierDivisionUtils.getMmr(entry.getTierDivisionType(), entry.getLeaguePoints()));
                    pstmt.setInt(7, entry.getWins());
                    pstmt.setInt(8, entry.getLosses());
                    pstmt.addBatch();
                    LeagueService.putLeagueEntry(shard, entry);
                }
                pstmt.executeBatch();
                conn.commit();
                for (LeagueEntry entry : entries) {
                    MongoDB.mirrorRanks(entry.getPuuid(), shard, List.of(new Rank(entry.getQueueType(), entry.getTierDivisionType(), entry.getLeaguePoints(), entry.getWins(), entry.getLosses())));
                }
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

    public static String buildMatchHistoryQuery(int summonerId, LeagueMessageParameter parameter) {
        long timeStart = parameter.getTimeStart();
        long timeEnd = parameter.getTimeEnd();
        int champion = parameter.getShowingChampion();
        GameQueueType queue = parameter.getQueueType();
        LaneType lane = parameter.getLaneType();

        int offset = parameter.getOffset();
        int limit = parameter.getMessageType().getPageItem();

        String offsetFilter = parameter.getMessageType() == LeagueMessageType.OVERVIEW_OPGG
            ? "LIMIT " + limit + " OFFSET " + offset
            : "";

        if (GameQueueTypeUtils.isCherry(queue))
                lane = null;

        String timeFilter = timeStart != 0
                ? "AND sm.`time_start` >= '" + new Timestamp(timeStart) + "' AND sm.`time_end` <= '" + new Timestamp(timeEnd) + "' "
                : "";
        String queueFilter = queue != null ? "AND sm.queue = '" + queue + "' " : "";
        String championFilter = champion != 0 ? "AND st.champion = " + champion + " " : "";
        String laneFilter = lane != null ? "AND st.lane = '" + lane + "' " : "";

        return
            "SELECT sm.* " +
            "FROM `match` sm " +
            "JOIN participant st ON st.match_id = sm.id " +
            "WHERE st.summoner_id = " + summonerId + " " +
            timeFilter + queueFilter + championFilter + laneFilter + " ORDER BY sm.time_start DESC " +
            offsetFilter + ";";
    }


    public static int countMatchHistory(int summonerId, LeagueMessageParameter parameter) {
        long timeStart = parameter.getTimeStart();
        long timeEnd = parameter.getTimeEnd();
        int champion = parameter.getShowingChampion();
        GameQueueType queue = parameter.getQueueType();
        LaneType lane = parameter.getLaneType();

        if (GameQueueTypeUtils.isCherry(queue))
                lane = null;

        String timeFilter = timeStart != 0
                ? "AND sm.`time_start` >= '" + new Timestamp(timeStart) + "' AND sm.`time_end` <= '" + new Timestamp(timeEnd) + "' "
                : "";
        String queueFilter = queue != null ? "AND sm.queue = '" + queue + "' " : "";
        String championFilter = champion != 0 ? "AND st.champion = " + champion + " " : "";
        String laneFilter = lane != null ? "AND st.lane = '" + lane + "' " : "";

        String q = 
            "SELECT sm.id " +
            "FROM `match` sm " +
            "JOIN participant st ON st.match_id = sm.id " +
            "WHERE st.summoner_id = " + summonerId + " " +
            timeFilter + queueFilter + championFilter + laneFilter + " ORDER BY sm.time_start DESC;";

        return instance.query(q).size();
    }

    public static int getMatchIdByGameId(String id) {
        QueryResult result = LeagueDB.get().query("SELECT id FROM `match` WHERE game_id = '" + id + "'");
        return result.isEmpty() ? 0 : result.get(0).getAsInt("id");
    }

    public static Match getMatch(LeagueShard shard, String gameId) {
        String matchQuery = "SELECT * FROM `match` WHERE game_id = ? AND region = ? LIMIT 1";

        try (Connection connection = instance.getConnection()) {
            if (connection == null) return null;

            try (PreparedStatement statement = connection.prepareStatement(matchQuery)) {
                statement.setString(1, gameId);
                statement.setString(2, shard.name());

                try (ResultSet result = statement.executeQuery()) {
                    Map<Integer, Match> matches = readMatches(result);
                    loadParticipants(connection, matches);
                    connection.commit();
                    return matches.isEmpty() ? null : matches.values().iterator().next();
                }
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public static List<Match> getMatchesAfterId(int afterId, int limit) {
        if (limit < 1) throw new IllegalArgumentException("limit must be positive");
        String query = "SELECT * FROM `match` WHERE id > ? ORDER BY id ASC LIMIT ?";
        try (Connection connection = instance.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setInt(1, afterId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                Map<Integer, Match> matches = readMatches(result, false);
                loadParticipants(connection, matches);
                connection.commit();
                return new ArrayList<>(matches.values());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read MariaDB match migration batch after id=" + afterId, exception);
        }
    }

    public static List<Match> getMatchesByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        StringBuilder placeholders = new StringBuilder("?");
        for (int index = 1; index < ids.size(); index++) placeholders.append(",?");
        String query = "SELECT * FROM `match` WHERE id IN (" + placeholders + ") ORDER BY id ASC";
        try (Connection connection = instance.getConnection(); PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < ids.size(); index++) statement.setInt(index + 1, ids.get(index));
            try (ResultSet result = statement.executeQuery()) {
                Map<Integer, Match> matches = readMatches(result, false);
                loadParticipants(connection, matches);
                connection.commit();
                return new ArrayList<>(matches.values());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to read MariaDB match migration ids=" + ids.size(), exception);
        }
    }


    public static List<Match> getMatchHistory(int summonerId, LeagueMessageParameter parameter) throws SQLException {
        List<Match> result = new ArrayList<>();

        try (Connection c = instance.getConnection()) {
            if (c == null) return result;

            String q1 = buildMatchHistoryQuery(summonerId, parameter);
            try (Statement stmt = c.createStatement(); ResultSet rs = stmt.executeQuery(q1)) {
                Map<Integer, Match> matches = readMatches(rs);
                loadParticipants(c, matches);
                result.addAll(matches.values());
            }
            c.commit();
        }
        return result;
    }

    private static Map<Integer, Match> readMatches(ResultSet result) throws SQLException {
        return readMatches(result, true);
    }

    private static Map<Integer, Match> readMatches(ResultSet result, boolean retainEvents) throws SQLException {
        Map<Integer, Match> matches = new LinkedHashMap<>();
        while (result.next()) {
            Match match = readMatch(result, retainEvents);
            matches.put(match.id, match);
        }
        return matches;
    }

    private static void loadParticipants(Connection connection, Map<Integer, Match> matches) throws SQLException {
        if (matches.isEmpty()) return;

        String inClause = matches.keySet().toString().replace("[", "(").replace("]", ")");
        String query = "SELECT st.*, sm.id AS match_id, su.puuid as puuid, su.riot_id as riot_id "
            + "FROM participant st "
            + "JOIN `match` sm ON st.match_id = sm.id "
            + "LEFT JOIN summoner su on su.id = st.summoner_id "
            + "WHERE st.match_id IN " + inClause + ";";

        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(query)) {
            while (result.next()) {
                Participant participant = readParticipant(result);
                Match match = matches.get(participant.matchId);
                if (match != null) match.participants.add(participant);
            }
        }
    }

    private static Match readMatch(ResultSet result) throws SQLException {
        return readMatch(result, true);
    }

    private static Match readMatch(ResultSet result, boolean retainEvents) throws SQLException {
        Match match = new Match();
        match.id = result.getInt("id");
        match.gameId = result.getString("game_id");
        match.leagueShard = enumValue(LeagueShard.class, result.getString("region"));
        match.queue = enumValue(GameQueueType.class, result.getString("queue"));
        match.rank = enumValue(TierType.class, result.getString("rank"));
        match.lastUpdate = timestamp(result, "last_update");
        match.timeStart = timestamp(result, "time_start");
        match.timeEnd = timestamp(result, "time_end");
        match.patch = result.getString("patch");
        JSONObject events = jsonObject(result.getString("events"));
        match.eventData = events.toMap();
        if (retainEvents) match.events = events;
        match.participants = new ArrayList<>();

        JSONObject bans = jsonObject(result.getString("bans"));
        boolean legacyBlueRedOrdinals = bans.has("0") && !bans.has("2");
        for (String key : bans.keySet()) {
            int ordinal;
            try {
                ordinal = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                try {
                    TeamType team = TeamType.valueOf(key);
                    JSONArray values = bans.optJSONArray(key);
                    List<Integer> championIds = new ArrayList<>();
                    if (values != null) for (int i = 0; i < values.length(); i++) championIds.add(values.optInt(i, 0));
                    if (team == TeamType.BLUE || team == TeamType.RED) match.bans.put(team, championIds);
                } catch (IllegalArgumentException ignoredName) {
                    // Unsupported legacy team keys are not valid side bans.
                }
                continue;
            }
            if (legacyBlueRedOrdinals) ordinal++;
            if (ordinal < 0 || ordinal >= TeamType.values().length) continue;

            List<Integer> championIds = new ArrayList<>();
            JSONArray values = bans.optJSONArray(key);
            if (values != null) {
                for (int i = 0; i < values.length(); i++) championIds.add(values.optInt(i, 0));
            }
            TeamType team = TeamType.values()[ordinal];
            if (team == TeamType.BLUE || team == TeamType.RED) match.bans.put(team, championIds);
        }
        return match;
    }

    private static Participant readParticipant(ResultSet result) throws SQLException {
        Participant participant = new Participant();
        participant.id = result.getInt("id");
        participant.summonerId = result.getInt("summoner_id");
        participant.matchId = result.getInt("match_id");
        participant.win = result.getBoolean("win");
        participant.kda = result.getString("kda");
        participant.champion = result.getInt("champion");
        participant.level = result.getInt("level");
        participant.team = enumValue(TeamType.class, result.getString("team"));
        participant.lane = enumValue(LaneType.class, result.getString("lane"));
        participant.roleQuestId = result.getInt("role_quest_id");
        participant.subTeam = result.getInt("subteam");
        participant.subTeamPlacement = result.getInt("subteam_placement");
        participant.rank = enumValue(TierDivisionType.class, result.getString("rank"));
        participant.lp = result.getInt("lp");
        participant.gain = result.getInt("gain");
        participant.damage = result.getInt("damage");
        participant.damageBuilding = result.getInt("damage_building");
        participant.healing = result.getInt("healing");
        participant.cs = result.getInt("cs");
        participant.goldEarned = result.getInt("gold_earned");
        participant.ward = result.getInt("ward");
        participant.wardKilled = result.getInt("ward_killed");
        participant.visionScore = result.getInt("vision_score");
        participant.doubles = result.getInt("doubles");
        participant.triples = result.getInt("triples");
        participant.quadruples = result.getInt("quadruples");
        participant.pentas = result.getInt("pentas");
        participant.q = result.getInt("q");
        participant.w = result.getInt("w");
        participant.e = result.getInt("e");
        participant.r = result.getInt("r");
        participant.d = result.getInt("d");
        participant.f = result.getInt("f");
        participant.puuid = result.getString("puuid");
        applyRiotId(participant, result.getString("riot_id"));

        JSONObject pings = jsonObject(result.getString("pings"));
        for (String key : pings.keySet()) participant.pings.put(key, pings.optInt(key, 0));
        ParticipantBuildCodec.apply(participant, result.getString("build"));
        return participant;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> enumType, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static long timestamp(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value != null ? value.getTime() : 0;
    }

    private static JSONObject jsonObject(String value) {
        return value == null || value.isBlank() ? new JSONObject() : new JSONObject(value);
    }

    private static void applyRiotId(Participant participant, String value) {
        if (value == null || value.isBlank()) return;

        String[] parts = value.split("#", 2);
        participant.riotId = parts[0];
        participant.riotTag = parts.length > 1 ? parts[1] : null;
    }


    public static List<Build> getChampionBuild(Filter filter) {
        if (filter == null) return List.of();

        QueryResult result = instance.query("SELECT id, data FROM champion_builds WHERE filter = '" + filter.toKey() + "'");
        List<Build> builds = new ArrayList<>();
        List<Integer> invalidIds = new ArrayList<>();
        for (QueryRecord row : result) {
            Build build = Build.decode(row.get("data"));
            if (build == null) invalidIds.add(row.getAsInt("id"));
            else builds.add(build);
        }
        deleteChampionBuilds(invalidIds);
        return builds;
    }

    public static void saveChampionBuild(Build build) {
        BotLogger.info("Saving champion build for " + build.filter().toKey());
        String sql = "INSERT INTO champion_builds (games, winrate, filter, data) VALUES (?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE games = VALUES(games), winrate = VALUES(winrate), data = VALUES(data)";
        try (Connection conn = instance.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, build.games());
            pstmt.setDouble(2, build.winrate());
            pstmt.setString(3, build.filter().toKey());
            pstmt.setString(4, build.encode());
            pstmt.executeUpdate();
            conn.commit();
            try {
                MongoDB.upsertChampionBuild(build);
            } catch (RuntimeException exception) {
                BotLogger.error("Mongo mirror failed operation=champion.build collection=champion_builds id=" + build.filter().toKey() + " error=" + exception.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveChampionBuilds(List<Build> builds) {
        if (builds == null || builds.isEmpty()) return;
    
        String deleteSql = "DELETE FROM champion_builds WHERE filter = ?";
        String insertSql = "INSERT INTO champion_builds (games, winrate, filter, data) VALUES (?, ?, ?, ?)";
    
        try (Connection conn = instance.getConnection();
             PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
    
            conn.setAutoCommit(false);
    
            for (Build build : builds) {
                deleteStmt.setString(1, build.filter().toKey());
                deleteStmt.addBatch();
            }
    
            deleteStmt.executeBatch();
    
            for (Build build : builds) {
                insertStmt.setInt(1, build.games());
                insertStmt.setDouble(2, build.winrate());
                insertStmt.setString(3, build.filter().toKey());
                insertStmt.setString(4, build.encode());
                insertStmt.addBatch();
            }
    
            insertStmt.executeBatch();
    
            conn.commit();
            try {
                MongoDB.upsertChampionBuilds(builds);
            } catch (RuntimeException exception) {
                BotLogger.error("Mongo mirror failed operation=champion.builds collection=champion_builds id=batch error=" + exception.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static QueryResult getChampionBuildsRaw(Filter filter) {
        String query = "SELECT m.game_id, p.win, p.build, p.summoner_id FROM `match` m STRAIGHT_JOIN participant p ON p.match_id = m.id " + filter.sql();
        return instance.query(query);
    }

    public static void saveChampionStats(ChampionStatistics stats) {
        String sql = "INSERT INTO champion_stats (filter, champion, data) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE data = VALUES(data)";
        try (Connection conn = instance.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, stats.filter().genericKey());
            pstmt.setInt(2, stats.filter().champion());
            pstmt.setString(3, stats.encode());
            pstmt.executeUpdate();
            conn.commit();
            try {
                MongoDB.upsertChampionStatistics(stats);
            } catch (RuntimeException exception) {
                BotLogger.error("Mongo mirror failed operation=champion.stats collection=champion_stats id=" + stats.filter().genericKey() + " error=" + exception.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void saveChampionStats(Map<Integer, ChampionStatistics> stats) {
        if (stats == null || stats.isEmpty()) return;
        String sql = "INSERT INTO champion_stats (filter, champion, data) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE data = VALUES(data)";
        try (Connection conn = instance.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (ChampionStatistics stat : stats.values()) {
                pstmt.setString(1, stat.filter().genericKey());
                pstmt.setInt(2, stat.filter().champion());
                pstmt.setString(3, stat.encode());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            try {
                MongoDB.upsertChampionStatistics(stats);
            } catch (RuntimeException exception) {
                BotLogger.error("Mongo mirror failed operation=champion.stats collection=champion_stats id=batch error=" + exception.getMessage());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static ChampionStatistics getChampionStats(Filter filter, int champion) {
        QueryResult result = instance.query(
            "SELECT data FROM champion_stats WHERE filter = '" +
            filter.genericKey() + "' AND champion = '" + champion + "'"
        );
        if (result.isEmpty()) return null;
        ChampionStatistics statistics = ChampionStatistics.decode(result.get(0).get("data"));
        if (statistics == null) deleteChampionStats(filter.genericKey(), champion);
        return statistics;
    }
    
    public static Map<Integer, ChampionStatistics> getChampionStats(Filter filter) {
        QueryResult result = instance.query(
            "SELECT champion, data FROM champion_stats WHERE filter = '" +
            filter.genericKey() + "'"
        );
        if (result.isEmpty()) return null;
        Map<Integer, ChampionStatistics> map = new HashMap<>();
        List<Integer> invalidChampions = new ArrayList<>();
        for (QueryRecord r : result) {
            int champion = r.getAsInt("champion");
            ChampionStatistics statistics = ChampionStatistics.decode(r.get("data"));
            if (statistics == null) invalidChampions.add(champion);
            else map.put(champion, statistics);
        }
        for (int champion : invalidChampions) deleteChampionStats(filter.genericKey(), champion);
        return map.isEmpty() ? null : map;
    }

    private static void deleteChampionStats(String filter, int champion) {
        String sql = "DELETE FROM champion_stats WHERE filter = ? AND champion = ?";
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, filter);
                pstmt.setInt(2, champion);
                pstmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException ignored) {}
    }

    private static void deleteChampionBuilds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;

        String sql = "DELETE FROM champion_builds WHERE id IN (" + placeholders(ids.size()) + ")";
        try (Connection conn = instance.getConnection()) {
            if (conn == null) return;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < ids.size(); i++) pstmt.setInt(i + 1, ids.get(i));
                pstmt.executeUpdate();
            }
            conn.commit();
        } catch (SQLException ignored) {}
    }

    public static QueryResult getStoredChampionBuildFilters() {
        return instance.query("SELECT DISTINCT filter FROM champion_builds;");
    }

    public static QueryResult getStoredChampionStatsFilters() {
        return instance.query("SELECT DISTINCT filter FROM champion_stats;");
    }

    public static QueryResult getChampionBuildRefreshFilters(String patch) {
        String sql = "SELECT DISTINCT p.champion, p.lane, m.queue, m.rank, m.region, m.patch_major AS patch "
            + "FROM `match` m JOIN participant p ON p.match_id = m.id "
            + "WHERE m.patch_major = ? AND p.build IS NOT NULL AND p.build <> '' AND p.champion IS NOT NULL";
        QueryResult result = new QueryResult();

        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, patch);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    QueryRecord row = new QueryRecord();
                    row.put("champion", rs.getString("champion"));
                    row.put("lane", rs.getString("lane"));
                    row.put("queue", rs.getString("queue"));
                    row.put("rank", rs.getString("rank"));
                    row.put("region", rs.getString("region"));
                    row.put("patch", rs.getString("patch"));
                    result.add(row);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static QueryResult getChampionStatsRefreshFilters(String patch) {
        String sql = "SELECT DISTINCT p.lane, m.queue, m.rank, m.region, m.patch_major AS patch "
            + "FROM `match` m JOIN participant p ON p.match_id = m.id "
            + "WHERE m.patch_major = ?";
        QueryResult result = new QueryResult();

        try (Connection conn = instance.getConnection()) {
            if (conn == null) return result;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, patch);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    QueryRecord row = new QueryRecord();
                    row.put("lane", rs.getString("lane"));
                    row.put("queue", rs.getString("queue"));
                    row.put("rank", rs.getString("rank"));
                    row.put("region", rs.getString("region"));
                    row.put("patch", rs.getString("patch"));
                    result.add(row);
                }
            }
            conn.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
    private static QueryRecord toRecord(ResultSet rs) throws SQLException {
        QueryRecord record = new QueryRecord();
        ResultSetMetaData metadata = rs.getMetaData();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            int type = metadata.getColumnType(i);
            String typeName = metadata.getColumnTypeName(i);
            boolean binary = type == Types.BLOB || type == Types.BINARY || type == Types.VARBINARY ||
                type == Types.LONGVARBINARY || (typeName != null && typeName.toUpperCase().contains("BLOB"));
            if (binary) {
                byte[] bytes = rs.getBytes(i);
                if (bytes != null) record.put(metadata.getColumnLabel(i).toLowerCase(), Base64.getEncoder().encodeToString(bytes));
            } else {
                record.put(metadata.getColumnLabel(i).toLowerCase(), rs.getString(i));
            }
        }
        return record;
    }

    private static long timeMs(String value) {
        try { return Timestamp.valueOf(value).getTime(); }
        catch (Exception ignored) { return 0; }
    }

}
