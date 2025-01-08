package com.safjnest.sql;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.json.simple.JSONObject;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import net.dv8tion.jda.api.entities.Message.Attachment;

import com.safjnest.core.Chronos.ChronoTask;
import com.safjnest.core.audio.PlayerManager;
import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.sound.Tag;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.lol.LeagueHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TeamType;
import no.stelar7.api.r4j.pojo.lol.match.v5.ChampionBan;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchParticipant;
import no.stelar7.api.r4j.pojo.lol.match.v5.MatchTeam;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorParticipant;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

/**
 * Useless (now usefull) class but {@link <a href="https://github.com/Leon412">Leon412</a>} is one
 * of the biggest caterpies ever made
 */
public class DatabaseHandler {
    private static String hostName;
    private static String database;
    private static String user;
    private static String password;

    private static HikariDataSource dataSource;

    private static HashMap<Long, List<String>> queryAnalytics = new HashMap<>();

    static {
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Constructor
     *
     * @param hostName Hostname, as 'keria123.eu-west-1.compute.fakerAws.com'
     * @param database Name of the database to connect in
     * @param user Username
     * @param password Password
     */
    public DatabaseHandler(String hostName, String database, String user, String password){
        DatabaseHandler.hostName = hostName;
        DatabaseHandler.database = database;
        DatabaseHandler.user = user;
        DatabaseHandler.password = password;

        connectIfNot();
    }

    private static void connectIfNot() {
        if (dataSource != null) return;

        initializeConnectionPool();
        if(!dataSource.isRunning())
            BotLogger.error("[SQL] Connection to the extreme db failed!");

        BotLogger.info("[SQL] Connection to the extreme db successful!");
    }

    public static void initializeConnectionPool() {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl("jdbc:mariadb://" + hostName + "/" + database + "?autoReconnect=true");
        config.setUsername(user);
        config.setPassword(password);
        config.setAutoCommit(false);

        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() {
        connectIfNot();
        try {
            return dataSource.getConnection();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void insertAnalytics(String query) {
        List<String> queries = queryAnalytics.getOrDefault(System.currentTimeMillis(), new ArrayList<>());
        queries.add(query);
        queryAnalytics.put(System.currentTimeMillis(), queries);
    }

    public static QueryCollection safJQuery(String query) {
        connectIfNot();

        Connection c = getConnection();
        if(c == null) return null;

        QueryCollection result = new QueryCollection();

        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            while (rs.next()) {
                QueryRecord beeRow = new QueryRecord(rs);
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnLabel(i);
                    String columnValue = rs.getString(i);
                    beeRow.put(columnName, columnValue);
                }
                result.add(beeRow);
            }
            insertAnalytics(query);
            c.commit();
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

        return result;
    }


    /**
     * Method used for returning a {@link com.safjnest.sql.QueryCollection result} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static QueryCollection safJQuery(Statement stmt, String query) throws SQLException {
        connectIfNot();

        QueryCollection result = new QueryCollection();

        ResultSet rs = stmt.executeQuery(query);
        ResultSetMetaData rsmd = rs.getMetaData();

        while (rs.next()) {
            QueryRecord beeRow = new QueryRecord(rs);
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String columnName = rsmd.getColumnName(i);
                String columnValue = rs.getString(i);
                beeRow.put(columnName, columnValue);
            }
            result.add(beeRow);
        }
        insertAnalytics(query);

        return result;
    }


    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static QueryRecord fetchJRow(String query) {
        connectIfNot();

        Connection c = getConnection();
        if(c == null) return null;
        QueryRecord beeRow = new QueryRecord(null);
        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            beeRow.setResultSet(rs);
            ResultSetMetaData rsmd = rs.getMetaData();
            if (rs.next()) {
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnName(i);
                    String columnValue = rs.getString(i);
                    beeRow.put(columnName, columnValue);
                }
            }
            insertAnalytics(query);
            c.commit();
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

        return beeRow;
    }


    /**
     * Method used for returning a single {@link com.safjnest.sql.QueryRecord row} from a query.
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static QueryRecord fetchJRow(Statement stmt, String query) throws SQLException {
        connectIfNot();


        ResultSet rs = stmt.executeQuery(query);
        QueryRecord beeRow = new QueryRecord(rs);

        ResultSetMetaData rsmd = rs.getMetaData();

        if (rs.next()) {
            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String columnName = rsmd.getColumnName(i);
                String columnValue = rs.getString(i);
                beeRow.put(columnName, columnValue);
            }
        }
        insertAnalytics(query);
        return beeRow;
    }


    /**
     * Run one or more queries using the default statement
     * @param queries
     */
    public static boolean runQuery(String... queries) {
        connectIfNot();

        Connection c = getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            for (String query : queries)
                stmt.execute(query);
            insertAnalytics(queries.toString());
            c.commit();
            return true;
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
        return false;
    }

    public static CompletableFuture<Void> runQueryAsync(String... queries) {
        return new ChronoTask() {
            @Override
            public void run() {
                runQuery(queries);
            }
        }.queueFuture();
    }


    /**
     * Run one or more queries with a specific statement
     * <p>
     * Only for insert, update and delete
     * @param stmt
     * @param queries
     * @throws SQLException
     */
    public static void runQuery(Statement stmt, String... queries) throws SQLException {
        connectIfNot();

        for (String query : queries)
            stmt.execute(query);

        insertAnalytics(queries.toString());

    }

    public static HashMap<Long, List<String>> getQueryAnalytics() {
        return queryAnalytics;
    }

    //-------------------------------------------------------------------------

    public static QueryCollection getGuildsData(String filter){
        String query = "SELECT guild_id, prefix, exp_enabled, threshold, blacklist_channel FROM guild WHERE " + filter + ";";
        return safJQuery(query);
    }


    public static QueryCollection getlistGuildSounds(String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static QueryCollection getlistGuildSounds(String guild_id, int limit) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC LIMIT " + limit);
    }

    public static QueryCollection getlistGuildSounds(String guild_id, String orderBy) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY " + orderBy +" ASC ");
    }



    public static QueryCollection getGuildRandomSound(String guild_id){
        return safJQuery("SELECT name, id FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryCollection getUserSound(String user_id){
        return safJQuery("SELECT name, id, guild_id, extension FROM sound WHERE user_id = '" + user_id + "';");
    }

    public static QueryCollection getlistUserSounds(String user_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY name ASC");
    }

    public static QueryCollection getlistUserSoundsTime(String user_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY time ASC");
    }

    public static QueryCollection getlistUserSounds(String user_id, String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY name ASC");
    }

    public static QueryCollection getlistUserSoundsTime(String user_id, String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY time ASC");
    }

    public static QueryCollection getFocusedGuildSound(String guild_id, String like){
        return safJQuery("SELECT name, id FROM sound WHERE name LIKE '" + like + "%' AND guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryCollection getFocusedUserSound(String user_id, String like){
        return safJQuery("SELECT name, id, guild_id FROM sound WHERE (name LIKE '" + like + "%' OR id LIKE '" + like + "%') AND user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryCollection getUserGuildSounds(String user_id, String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' OR guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static QueryCollection getFocusedListUserSounds(String user_id, String guild_id, String like) {
        return safJQuery("SELECT name, id, guild_id, extension FROM sound WHERE name LIKE '" + like + "%' OR id LIKE '" + like + "%' AND (user_id = '" + user_id + "' OR guild_id = '" + guild_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public static QueryCollection getSoundsById(String... sound_ids) {
        StringBuilder sb = new StringBuilder();
        for(String sound_id : sound_ids)
            sb.append(sound_id + ", ");
        sb.setLength(sb.length() - 2);

        return safJQuery("SELECT id, name, guild_id, user_id, extension, public, time, plays, likes, dislikes FROM sound WHERE id IN (" + sb.toString() + ");");
    }

    public static QueryCollection getSoundsById(String id, String guild_id, String author_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryRecord getSoundById(String id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "'");
    }

    public static QueryCollection getSoundsByName(String name, String guild_id, String author_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryCollection getDuplicateSoundsByName(String name, String guild_id, String author_id) {
        return safJQuery("SELECT id, guild_id, user_id FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "' OR user_id = '" + author_id + "')");
    }

    public static QueryRecord getAuthorSoundById(String id, String user_id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "' AND user_id = '" + user_id + "'");
    }

    public static QueryRecord getAuthorSoundByName(String name, String user_id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE name = '" + name + "' AND user_id = '" + user_id + "'");
    }

    public static String insertSound(String name, String guild_id, String user_id, String extension, boolean isPublic) {
        Connection c = getConnection();
        if(c == null) return null;

        String soundId = null;
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO sound(name, guild_id, user_id, extension, public, time) VALUES('" + name + "','" + guild_id + "','" + user_id + "','" + extension + "', " + ((isPublic == true) ? "1" : "0") + ", '" +  Timestamp.from(Instant.now()) + "'); ");
            soundId = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").get("id");
            c.commit();
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
        return soundId;
    }

    public static boolean updateSound(String id, String name, boolean isPublic) {
        return runQuery("UPDATE sound SET name = '" + name + "', public = '" + (isPublic ? "1" : "0") + "' WHERE id = '" + id + "';");
    }

    public static boolean updateSound(String id, String name) {
        return runQuery("UPDATE sound SET name = '" + name + "' WHERE id = '" + id + "';");
    }

    public static boolean updateSound(String id, boolean isPublic) {
        return runQuery("UPDATE sound SET public = '" + (isPublic ? "1" : "0") + "' WHERE id = '" + id + "';");
    }

    public static boolean deleteSound(String id) {
        return runQuery("DELETE FROM sound WHERE id = " + id + ";");
    }

    public static boolean updateUserPlays(String sound_id, String user_id, int source) {
        return runQuery("INSERT INTO sound_history(user_id, sound_id, source) VALUES('" + user_id + "', '" + sound_id + "', " + source + ");", "UPDATE sound SET plays = plays + 1 WHERE id = '" + sound_id + "';");
    }

    public static QueryRecord getPlays(String sound_id, String user_id) {
        return fetchJRow("SELECT count(id) as times FROM sound_history WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "'");
    }

    public static QueryRecord getGlobalPlays(String sound_id) {
        return fetchJRow("SELECT count(id) as times FROM sound_history WHERE sound_id = '" + sound_id + "'");
    }

    public static String getSoundsUploadedByUserCount(String user_id) {
        return fetchJRow("select count(name) as count from sound where user_id = '" + user_id + "';").get("count");
    }

    public static String getSoundsUploadedByUserCount(String user_id, String guild_id) {
        return fetchJRow("select count(name) as count from sound where guild_id = '" + guild_id + "' AND user_id = '" + user_id + "';").get("count");
    }

    public static String getTotalPlays(String user_id) {
        return fetchJRow("select count(id) as sum from sound_history where user_id = '" + user_id + "';").get("sum");
    }

    public static QueryRecord searchSoundboard(String string, String guild_id, String user_id) {
        return fetchJRow("SELECT id from soundboard WHERE (ID = '" + string + "' OR name = '" + string + "') AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "')");
    }

    public static boolean soundboardExists(String id, String guild_id) {
        return !fetchJRow("SELECT id from soundboard WHERE name = '" + id + "' AND guild_id = '" + guild_id + "'").isEmpty();
    }

    public static boolean soundboardExists(String id, String guild_id, String user_id) {
        return !fetchJRow("SELECT id from soundboard WHERE ID = '" + id + "' AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "')").isEmpty();
    }

    public static int getSoundInSoundboardCount(String id) {
        return fetchJRow("SELECT count(sound_id) as cont FROM soundboard_sounds WHERE id = '" + id + "'").getAsInt("count");
    }

    public static QueryCollection getSoundsFromSoundBoard(String id) {
        return safJQuery("select soundboard_sounds.sound_id as sound_id, sound.extension as extension, sound.name as name, sound.guild_id as guild_id from soundboard_sounds join soundboard on soundboard.id = soundboard_sounds.id join sound on soundboard_sounds.sound_id = sound.id where soundboard.id = '" + id + "'");
    }

    public static QueryRecord getSoundboardByID(String id) {
        return fetchJRow("select name, thumbnail from soundboard where id = '" + id + "'");
    }

    public static QueryCollection getRandomSoundboard(String guild_id, String user_id) {
        return safJQuery("SELECT name, id, guild_id FROM soundboard WHERE guild_id = '" + guild_id + "' OR user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryCollection getFocusedSoundboard(String guild_id, String user_id, String like){
        return safJQuery("SELECT name, id, guild_id FROM soundboard WHERE name LIKE '" + like + "%' AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public static QueryCollection getFocusedSoundFromSounboard(String id, String like){
        return safJQuery("SELECT s.name as name, s.id as sound_id, s.guild_id as guild_id FROM soundboard_sounds ss JOIN sound s ON ss.sound_id = s.id WHERE s.name LIKE '" + like + "%' AND ss.id = '" + id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryCollection extremeSoundResearch(String query) {
        return safJQuery("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE s.name like '%" + query + "%' OR t.name like '%" + query + "%';");
        //return safJQuery("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE MATCH(s.name) AGAINST ('" + query + "') OR t.name like '%" + query + "%';");
    }

    public static QueryCollection extremeSoundResearch(String query, String user_id) {
        return safJQuery("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE s.user_id = " + user_id + " AND (MATCH(s.name) AGAINST ('" + query + "') OR t.name like '%" + query + "%');");
    }



    public static boolean insertSoundBoard(String name, Attachment attachment, String guild_id, String user_id, String... sound_ids) {
        if(sound_ids.length == 0) throw new IllegalArgumentException("sound_ids must not be empty");

        StringBuilder sb = new StringBuilder();

        for (String sound_id : sound_ids)
            sb.append("(LAST_INSERT_ID(), " + sound_id + "), ");
        sb.setLength(sb.length() - 2);

        Connection c = getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            //runQuery(stmt, "INSERT INTO soundboard (name, thumbnail, guild_id, user_id) VALUES ('" + name + "', '" + (attachment != null ? attachment.getUrl() : "") + "', '" + guild_id + "', '" + user_id + "'); ");

            String query = "INSERT INTO soundboard (name, thumbnail, guild_id, user_id) VALUES (?, ?, ?, ?);";
            try (PreparedStatement pstmt = c.prepareStatement(query)) {
                pstmt.setString(1, name);
                if (attachment != null) {
                    CompletableFuture<InputStream> futureInputStream = attachment.getProxy().download();
                    InputStream thumbnail = futureInputStream.join();
                    pstmt.setBlob(2, thumbnail);
                } else {
                    pstmt.setNull(2, java.sql.Types.BLOB);
                }
                pstmt.setString(3, guild_id);
                pstmt.setString(4, user_id);

                pstmt.execute();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }



            System.out.println("INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + ";");
            runQuery(stmt, "INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + ";");
            c.commit();
            return true;
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
        return false;
    }

    public static boolean updateSoundboardThumbnail(String id, Attachment thumbnail) {
        Connection c = getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            String query = "UPDATE soundboard SET thumbnail = ? WHERE id = ?;";
            try (PreparedStatement pstmt = c.prepareStatement(query)) {
                CompletableFuture<InputStream> futureInputStream = thumbnail.getProxy().download();
                InputStream thumbnailStream = futureInputStream.join();
                pstmt.setBlob(1, thumbnailStream);
                pstmt.setString(2, id);

                pstmt.execute();
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
            c.commit();
            return true;
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
        return false;
    }

    public static boolean insertSoundsInSoundBoard(String id, String... sound_ids) {
        if(sound_ids.length == 0) throw new IllegalArgumentException("sound_ids must not be empty");

        StringBuilder sb = new StringBuilder();
        for(String sound_id : sound_ids) {
            sb.append("('" + id + "', '" + sound_id + "'), ");
        }
        sb.setLength(sb.length() - 2);

        return runQuery("INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + "; ");
    }

    public static boolean deleteSoundboard(String id) {
        return runQuery("DELETE FROM soundboard WHERE id = '" + id + "'");
    }

    public static boolean deleteSoundFromSoundboard(String id, String sound_id) {
        return runQuery("DELETE FROM soundboard_sounds WHERE id = '" + id + "' AND sound_id = '" + sound_id + "'");
    }

    public static QueryRecord getDefaultVoice(String guild_id) {
        return fetchJRow("SELECT name_tts, language_tts FROM guild WHERE guild_id = '" + guild_id + "';");
    }


    public static QueryCollection getLOLAccountsByUserId(String user_id){
        String query = "SELECT account_id, league_shard, tracking FROM summoner WHERE user_id = '" + user_id + "' order by id;";
        return safJQuery(query);
    }

    public static String getUserIdByLOLAccountId(String account_id, LeagueShard shard) {
        return fetchJRow("SELECT user_id FROM summoner WHERE account_id = '" + account_id + "' AND league_shard = '" + shard.ordinal() + "';").get("user_id");
    }

    public static QueryCollection getAdvancedLOLData(String account_id) {
        return safJQuery("SELECT `champion`, COUNT(*) AS `games`, SUM(`win`) AS `wins`, SUM(CASE WHEN `win` = 0 THEN 1 ELSE 0 END) AS `losses`, AVG(CAST(SUBSTRING_INDEX(`kda`, '/', 1) AS UNSIGNED)) AS avg_kills, AVG(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(`kda`, '/', -2), '/', 1) AS UNSIGNED)) AS avg_deaths, AVG(CAST(SUBSTRING_INDEX(`kda`, '/', -1) AS UNSIGNED)) AS avg_assists, SUM(`gain`) AS total_lp_gain FROM `summoner_tracking` WHERE `account_id` = '" + account_id + "' GROUP BY `champion` ORDER BY `games` DESC;");
    }

    public static QueryCollection getAdvancedLOLData(String account_id, long time_start, long time_end) {
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
            "FROM `summoner_tracking` t " +
            "JOIN `summoner_match` sm ON t.`summoner_match_id` = sm.`id` " +
            "WHERE t.`account_id` = '" + account_id + "' " +
            "AND sm.`time_start` >= '" + new Timestamp(time_start) + "' " +
            "AND sm.`time_end` <= '" + new Timestamp(time_end) + "' " +
            "AND sm.game_type = " + GameQueueType.TEAM_BUILDER_RANKED_SOLO.ordinal() + " " +
            "GROUP BY t.`champion`";

        String laneQuery =
            "SELECT " +
            "  t.`champion`, " +
            "  t.`lane`, " +
            "  COUNT(*) AS `lane_games`, " +
            "  SUM(t.`win`) AS `lane_wins`, " +
            "  SUM(CASE WHEN t.`win` = 0 THEN 1 ELSE 0 END) AS `lane_losses` " +
            "FROM `summoner_tracking` t " +
            "JOIN `summoner_match` sm ON t.`summoner_match_id` = sm.`id` " +
            "WHERE t.`account_id` = '" + account_id + "' " +
            "AND sm.`time_start` >= '" + new Timestamp(time_start) + "' " +
            "AND sm.`time_end` <= '" + new Timestamp(time_end) + "' " +
            "AND sm.game_type = " + GameQueueType.TEAM_BUILDER_RANKED_SOLO.ordinal() + " " +
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

            System.out.println(combinedQuery);

        return safJQuery(combinedQuery);
    }




    public static boolean addLOLAccount(Summoner summoner) {
        return addLOLAccount(null, summoner);
    }

    public static boolean addLOLAccount(String user_id, Summoner summoner) {
        RiotAccount account = LeagueHandler.getRiotAccountFromSummoner(summoner);
        String query = "INSERT INTO summoner(user_id, summoner_id, account_id, puuid, riot_id, league_shard) " +
                "VALUES(?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "user_id = IF(VALUES(user_id) IS NOT NULL, VALUES(user_id), user_id), " +
                "summoner_id = VALUES(summoner_id), " +
                "account_id = VALUES(account_id), " +
                "puuid = VALUES(puuid), " +
                "riot_id = VALUES(riot_id), " +
                "league_shard = VALUES(league_shard);";

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
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

            int affectedRows = pstmt.executeUpdate();
            conn.commit();
            return affectedRows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
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

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
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

        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
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

    public static boolean deleteLOLaccount(String user_id, String account_id){
        String query = "UPDATE summoner SET tracking = 0, user_id = NULL WHERE user_id = '" + user_id + "' AND account_id = '" + account_id + "';";
        return runQuery(query);
    }

    public static QueryCollection getGuildData(){
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel, blacklist_enabled FROM guild;";
        return safJQuery(query);
    }

    public static QueryRecord getGuildData(String guild_id) {
        String query = "SELECT guild_id, PREFIX, exp_enabled, name_tts, language_tts, threshold, blacklist_channel, blacklist_enabled, league_shard FROM guild WHERE guild_id = '" + guild_id + "';";
        return fetchJRow(query);
    }

    public static boolean updateVoiceGuild(String guild_id, String language, String voice) {
        String query = "INSERT INTO guild (guild_id, language_tts, name_tts) VALUES ('" + guild_id + "', '" + language + "', '" + voice + "') ON DUPLICATE KEY UPDATE language_tts = '" + language + "', name_tts = '" + voice + "'";
        return runQuery(query);
    }

    public static boolean insertGuild(String guild_id, String prefix) {
        String query = "INSERT INTO guild (guild_id, PREFIX, exp_enabled, threshold, blacklist_channel) VALUES ('" + guild_id + "', '" + prefix + "', '1', '0', null) ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';";
        return runQuery(query);
    }


    public static QueryCollection getUsersByExp(String guild_id, int limit) {
        if (limit == 0) {
            return safJQuery("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC;");
        }
        return safJQuery("SELECT user_id, messages, level, experience as exp from member WHERE guild_id = '" + guild_id + "' order by experience DESC limit " + limit + ";");
    }



    public static boolean toggleLevelUp(String guild_id, boolean toggle) {
        return runQuery("INSERT INTO guild(guild_id, exp_enabled) VALUES ('" + guild_id + "', '" + (toggle ? "1" : "0") + "') ON DUPLICATE KEY UPDATE exp_enabled = '" + (toggle ? "1" : "0") + "';");
    }

    public static boolean toggleBlacklist(String guild_id, boolean toggle) {
        return runQuery("UPDATE guild SET blacklist_enabled = '" + toggle + "' WHERE guild_id = '" + guild_id + "';");
    }


    public static boolean setPrefix(String guild_id, String prefix) {
        return runQuery("INSERT INTO guild(guild_id, prefix)" + "VALUES('" + guild_id + "','" + prefix +"') ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';");
    }

    public static boolean updatePrefix(String guild_id, String prefix) {
        return runQuery("UPDATE guild SET prefix = '" + prefix + "' WHERE guild_id = '" + guild_id + "';");
    }

    public static boolean setGreet(String user_id, String guild_id, String sound_id) {
        return runQuery("INSERT INTO greeting (user_id, guild_id, sound_id) VALUES ('" + user_id + "', '" + guild_id + "', '" + sound_id + "') ON DUPLICATE KEY UPDATE sound_id = '" + sound_id + "';");
    }

    public static boolean deleteGreet(String user_id, String guild_id) {
        return runQuery("DELETE from greeting WHERE guild_id = '" + guild_id + "' AND user_id = '" + user_id + "';");
    }

    public static boolean setBlacklistChannel(String blacklist_channel, String guild_id) {
        return runQuery("UPDATE guild SET blacklist_channel = '" + blacklist_channel + "' WHERE guild_id = '" + guild_id +  "';");
    }

    public static boolean setBlacklistThreshold(String threshold, String guild_id) {
        return runQuery("UPDATE guild SET threshold = '" + threshold + "' WHERE guild_id = '" + guild_id +  "';");
    }

    public static boolean enableBlacklist(String guild_id, String threshold, String blacklist_channel) {
        return runQuery("INSERT INTO guild(guild_id, threshold, blacklist_channel, blacklist_enabled)" + "VALUES('" + guild_id + "','" + threshold +"', '" + blacklist_channel + "', 1) ON DUPLICATE KEY UPDATE threshold = '" + threshold + "', blacklist_channel = '" + blacklist_channel + "', blacklist_enabled = 1;");
    }

    public static boolean insertUserBlacklist(String user_id, String guild_id){
        return runQuery("INSERT INTO blacklist VALUES('" + user_id + "', '" + guild_id + "')");
    }

    public static int getBlacklistBan(String user_id){
        return fetchJRow("SELECT count(user_id) as times from blacklist WHERE user_id = '" + user_id + "'").getAsInt("times");
    }

    public static boolean deleteBlacklist(String guild_id, String user_id){
        return runQuery("DELETE FROM blacklist WHERE guild_id = '" + guild_id + "' AND user_id = '" + user_id + "'");
    }

    public static QueryCollection getGuildByThreshold(int threshold, String guild_id){
        return safJQuery("SELECT guild_id, blacklist_channel, threshold FROM guild WHERE blacklist_enabled = 1 AND threshold <= '" + threshold + "' AND blacklist_channel IS NOT NULL AND guild_id != '" + guild_id + "'");
    }

    public static boolean insertCommand(String guild_id, String author_id, String command, String args){
        return runQuery("INSERT INTO command(name, time, user_id, guild_id, args) VALUES ('" + command + "', '" + new Timestamp(System.currentTimeMillis()) + "', '" + author_id + "', '"+ guild_id +"', '"+ fixSQL(args) +"');");
    }

    public static int getBannedTimes(String user_id){
        return fetchJRow("SELECT count(user_id) as times from blacklist WHERE user_id = '" + user_id + "'").getAsInt("times");
    }

    public static int getBannedTimesInGuild(String guild_id){
        return fetchJRow("SELECT count(user_id) as times from blacklist WHERE guild_id = '" + guild_id + "'").getAsInt("times");
    }

    @Deprecated
    public static QueryRecord getGreet(String user_id, String guild_id) {
        return fetchJRow("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND (greeting.guild_id = '" + guild_id + "' OR greeting.guild_id = '0') ORDER BY CASE WHEN greeting.guild_id = '0' THEN 1 ELSE 0 END LIMIT 1;");
    }

    public static QueryRecord getSpecificGuildGreet(String user_id, String guild_id) {
        return fetchJRow("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND greeting.guild_id = '" + guild_id + "' LIMIT 1;");
    }

    public static QueryRecord getGlobalGreet(String user_id) {
        return fetchJRow("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND greeting.guild_id = '0' LIMIT 1;");
    }


    public static boolean setAlertMessage(String ID, String message) {
        Connection c = getConnection();
        if(c == null) return false;

        try (PreparedStatement pstmt = c.prepareStatement("UPDATE alert SET message = ? WHERE ID = ?")) {
            pstmt.setString(1, message);
            pstmt.setString(2, ID);
            int affectedRows = pstmt.executeUpdate();
            c.commit();
            return affectedRows > 0;
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
        return false;
    }

    public static boolean setAlertPrivateMessage(String ID, String message) {
        Connection c = getConnection();
        if(c == null) return false;

        try (PreparedStatement pstmt = c.prepareStatement("UPDATE alert SET private_message = ? WHERE ID = ?")) {
            pstmt.setString(1, message);
            pstmt.setString(2, ID);
            int affectedRows = pstmt.executeUpdate();
            c.commit();
            return affectedRows > 0;
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
        return false;
    }

    public static boolean setAlertChannel(String ID, String channel) {
        return runQuery("UPDATE alert SET channel = '" + channel + "' WHERE ID = '" + ID + "';");
    }

    public static boolean setAlertEnabled(String ID, boolean toggle) {
        return runQuery("UPDATE alert SET enabled = '" + (toggle ? 1 : 0) + "' WHERE ID = '" + ID + "';");
    }

    public static QueryCollection getAlertsRoles(String guild_id) {
        return safJQuery("SELECT r.id as row_id, a.id as alert_id, r.role_id as role_id  FROM alert_role as r JOIN alert as a ON r.alert_id = a.id WHERE a.guild_id = '" + guild_id + "';");
    }

    public static int createAlert(String guild_id, String message, String privateMessage, String channelId, AlertSendType sendType, AlertType type) {
        int id = 0;
        String query = "INSERT INTO alert(guild_id, message, private_message, channel, enabled, send_type, type) VALUES(?, ?, ?, ?, 1, ?, ?);";

        Connection c = getConnection();
        if(c == null) return id;

        try (PreparedStatement pstmt = c.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guild_id);
            pstmt.setString(2, message);
            if (privateMessage != null) {
                pstmt.setString(3, privateMessage);
            } else {
                pstmt.setNull(3, Types.VARCHAR);
            }
            pstmt.setString(4, channelId);
            pstmt.setInt(5, sendType.ordinal());
            pstmt.setInt(6, type.ordinal());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getInt(1);
                }
            }
            c.commit();
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

    public static boolean createRewardData(String alertID, int level, boolean temporary) {
        return runQuery("INSERT INTO alert_reward(alert_id, level, temporary) VALUES('" + alertID + "', '" + level + "', '" + (temporary ? 1 : 0) + "');");
    }

    public static boolean createTwitchData(String alertId, String streamerId, String roleId) {
        String query = "INSERT INTO alert_twitch (alert_id, streamer_id, role_id) VALUES (?, ?, ?);";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, alertId);
            pstmt.setString(2, streamerId);
            if (roleId != null) {
                pstmt.setString(3, roleId);
            } else {
                pstmt.setNull(3, java.sql.Types.VARCHAR);
            }
            int affectedRows = pstmt.executeUpdate();
            conn.commit();
            return affectedRows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateTwitchRole(int alertId, String roleId) {
        return runQuery("UPDATE alert_twitch SET role_id = '" + roleId + "' WHERE alert_id = '" + alertId + "';");
    }

    public static QueryCollection getAlerts(String guild_id) {
        String query = "SELECT " +
                       "a.id AS alert_id, " +
                       "a.message, " +
                       "a.private_message, " +
                       "a.channel, " +
                       "a.enabled, " +
                       "a.send_type, " +
                       "a.type, " +
                       "r.id AS reward_id, " +
                       "r.level AS level, " +
                       "r.temporary AS temporary, " +
                       "t.id AS twitch_id, " +
                       "t.streamer_id, " +
                       "t.role_id " +
                       "FROM alert AS a " +
                       "LEFT JOIN alert_reward AS r ON a.id = r.alert_id " +
                       "LEFT JOIN alert_twitch AS t ON a.id = t.alert_id " +
                       "WHERE a.guild_id = '" + guild_id + "';";
        return safJQuery(query);
    }

    public static boolean deleteAlert(String valueOf) {
        return runQuery("DELETE FROM alert WHERE id = '" + valueOf + "';");
    }

    public static boolean deleteAlertRoles(String valueOf) {
        return runQuery("DELETE FROM alert_role WHERE alert_id = '" + valueOf + "';");
    }

    public static boolean alertUpdateSendType(String valueOf, AlertSendType sendType) {
        return runQuery("UPDATE alert SET send_type = '" + sendType.ordinal() + "' WHERE id = '" + valueOf + "';");
    }

    public static HashMap<Integer, String> createRolesAlert(String valueOf, String[] roles) {

        String values = "";
        for(String role : roles) {
            if(role != null) {
                values += "('" + valueOf + "', '" + role + "'), ";
            }
        }

        if (values.isEmpty()) {
            return null;
        }

        values = values.substring(0, values.length() - 2);
        if (deleteAlertRoles(valueOf) && runQuery("INSERT INTO alert_role(alert_id, role_id) VALUES " + values + ";")) {
            HashMap<Integer, String> roleMap = new HashMap<>();
            QueryCollection result = safJQuery("SELECT id, role_id FROM alert_role WHERE alert_id = '" + valueOf + "';");
            for(QueryRecord row : result) {
                roleMap.put(row.getAsInt("id"), row.get("role_id"));
            }
            return roleMap;
        }

        return null;

    }


    public static int insertChannelData(long guild_id, long channel_id) {
        int id = 0;

        Connection c = getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO channel(guild_id, channel_id) VALUES('" + guild_id + "','" + channel_id + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
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

    public static QueryCollection getChannelData(String guild_id) {
        return safJQuery("SELECT id, channel_id, guild_id, exp_enabled, exp_modifier, stats_enabled, league_shard FROM channel WHERE guild_id = '" + guild_id + "';");
    }

    public static boolean setChannelExpModifier(int ID, double exp_modifier) {
        return runQuery("UPDATE channel SET exp_modifier = '" + exp_modifier + "' WHERE id = '" + ID + "';");
    }

    public static boolean setChannelExpEnabled(int ID, boolean toggle) {
        return runQuery("UPDATE channel SET exp_enabled = '" + (toggle ? 1 : 0) + "' WHERE id = '" + ID + "';");
    }

    public static boolean setChannelCommandEnabled(int ID, boolean toggle) {
        return runQuery("UPDATE channel SET stats_enabled = '" + (toggle ? 1 : 0) + "' WHERE id = '" + ID + "';");
    }

    public static boolean deleteChannelData(int ID) {
        return runQuery("DELETE FROM channel WHERE id = '" + ID + "';");
    }


    public static int insertUserData(String guild_id, String user_id) {
        int id = 0;

        Connection c = getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO member(guild_id, user_id) VALUES('" + guild_id + "','" + user_id + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
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

    public static QueryRecord getUserData(String guild_id, String user_id) {
        return fetchJRow("SELECT id, user_id, guild_id, experience, level, messages, update_time FROM member WHERE user_id = '"+ user_id +"' AND guild_id = '" + guild_id + "';");
    }

    public static boolean updateUserDataExperience(int ID, int experience, int level, int messages) {
        return runQuery("UPDATE member SET experience = '" + experience + "', level = '" + level + "', messages = '" + messages + "' WHERE id = '" + ID + "';");
    }

    public static boolean updateUserDataUpdateTime(int ID, int updateTime) {
        return runQuery("UPDATE member SET update_time = '" + updateTime + "' WHERE id = '" + ID + "';");
    }

    public static QueryRecord getUserExp(String id, String id2) {
        return fetchJRow("SELECT experience, level, messages FROM member WHERE user_id = '" + id + "' AND guild_id = '" + id2 + "'");
    }


    public static QueryCollection getAliases(String user_id) {
        return safJQuery("SELECT id, name, command FROM alias WHERE user_id = '" + user_id + "';");
    }

    public static int createAlias(String user_id, String name, String command) {
        int id = 0;

        Connection c = getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO alias(user_id, name, command) VALUES('" + user_id + "','" + name + "','" + command + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
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

    public static boolean deleteAlias(String toDelete) {
        return runQuery("DELETE FROM alias WHERE id = '" + toDelete + "';");
    }



    public static HashMap<String, QueryCollection> getCustomCommandData(String guild_id) {
        HashMap<String, QueryCollection> commandData = new HashMap<>();
        QueryCollection result = DatabaseHandler.safJQuery("SELECT ID,name,description,slash FROM commands WHERE guild_id = " + guild_id);

        if (result.isEmpty()) {
            return null;
        }
        commandData.put("commands", result);

        String ids = String.join(", ", result.arrayColumn("ID"));

        QueryCollection optionResult = DatabaseHandler.safJQuery("SELECT ID,command_id,`key`,description,required,type FROM command_option WHERE command_id IN (" + ids + ")");
        commandData.put("options", optionResult);

        QueryCollection valueResult = null;
        if (!optionResult.isEmpty()) {
            String optionIds = String.join(", ", optionResult.arrayColumn("ID"));
            valueResult = DatabaseHandler.safJQuery("SELECT ID,option_id,`key`,value FROM command_option_value WHERE option_id IN (" + optionIds + ")");
            commandData.put("values", valueResult);
        }

        QueryCollection taskResult = DatabaseHandler.safJQuery("SELECT ID,command_id,type,`order` FROM command_task WHERE command_id IN (" + ids + ") order by `order`");
        commandData.put("tasks", taskResult);

        String taskIds = String.join(", ", taskResult.arrayColumn("ID"));
        QueryCollection taskValueResult = DatabaseHandler.safJQuery("SELECT ID,task_id,value,from_option FROM command_task_value WHERE task_id IN (" + taskIds + ")");
        commandData.put("task_values", taskValueResult);

        String taskValueIds = String.join(", ", taskValueResult.arrayColumn("ID"));
        QueryCollection taskMessage = DatabaseHandler.safJQuery("SELECT ID,task_value_id,message FROM command_task_message WHERE task_value_id IN (" + taskValueIds + ")");
        if (!taskMessage.isEmpty()) {
            commandData.put("task_messages", taskMessage);
        }
        return commandData;
    }

    public static boolean updateShard(String valueOf, LeagueShard shard) {
        return runQuery("UPDATE guild SET league_shard = '" + shard.ordinal() + "' WHERE guild_id = '" + valueOf + "';");
    }

    public static boolean updateShardChannel(int valueOf, LeagueShard shard) {
        return runQuery("UPDATE channel SET league_shard = '" + shard.ordinal() + "' WHERE id = '" + valueOf + "';");
    }

    public static QueryCollection getTwitchSubscriptions(String streamer_id) {
        return safJQuery("SELECT a.guild_id as guild_id from alert_twitch as at join alert as a on at.alert_id = a.id WHERE at.streamer_id = '" + streamer_id + "';");
    }

    public static QueryCollection getSoundTags(String sound_id) {
        return safJQuery("SELECT ts.tag_id as id,t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id = '" + sound_id + "';");
    }

    public static QueryCollection getSoundsTags(String ...sound_id) {
        StringBuilder sb = new StringBuilder();
        for(String sound : sound_id) {
            sb.append("'" + sound + "', ");
        }
        sb.setLength(sb.length() - 2);
        return safJQuery("SELECT ts.sound_id as sound_id, ts.tag_id as tag_id, t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id IN (" + sb.toString() + ");");
    }

    public static QueryRecord getTag(String tag_id) {
        return fetchJRow("SELECT id, name FROM tag WHERE id = '" + tag_id + "';");
    }

    public static boolean setSoundTags(String sound_id, Tag[] tags) {
        String values = "";
        for(Tag tag : tags) {
            if (tag.getId() != 0) values += "('" + sound_id + "', '" + tag.getId() + "'), ";
        }
        if (!values.isEmpty()) {
            values = values.substring(0, values.length() - 2);
        }
        runQuery("DELETE FROM tag_sounds WHERE sound_id = '" + sound_id + "';");
        return runQuery("INSERT INTO tag_sounds(sound_id, tag_id) VALUES " + values + ";");
    }

    public static int insertTag(String tag) {
        int id = 0;

        Connection c = getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            PreparedStatement ps = c.prepareStatement("SELECT * FROM tag WHERE name = ?;");
            ps.setString(1, tag);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                id = rs.getInt("id");
            } else{
                ps = c.prepareStatement("INSERT INTO tag(name) VALUES(?);");
                ps.setString(1, tag);
                ps.executeUpdate();
                id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
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

    public static QueryRecord getLikeDislike(String sound_id) {
        return fetchJRow("SELECT "
            + "(SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = 1) AS likes, "
            + "(SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = -1) AS dislikes;");
    }


    public static boolean setLikeDislike(String sound_id, String user_id, int value) {
        return runQuery("INSERT INTO sound_interactions(sound_id, user_id, value) VALUES('" + sound_id + "', '" + user_id + "', '" + value + "') ON DUPLICATE KEY UPDATE value = '" + value + "';", "UPDATE sound SET likes = (SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = 1), dislikes = (SELECT COUNT(*) FROM sound_interactions WHERE sound_id = '" + sound_id + "' AND value = -1) WHERE id = '" + sound_id + "';");
    }

    public static QueryRecord hasInterectedSound(String sound_id, String user_id) {
        String query = "SELECT " +
                       "CASE WHEN value = 1 THEN 1 ELSE 0 END AS `like`, " +
                       "CASE WHEN value = -1 THEN 1 ELSE 0 END AS `dislike` " +
                       "FROM sound_interactions " +
                       "WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "';";
        return fetchJRow(query);
    }


    public static QueryCollection getRegistredLolAccount(long time_start) {
        return safJQuery(
            "SELECT s.account_id, s.league_shard, st.game_id, st.rank, st.lp, st.time_start "
            + "FROM summoner s "
            + "LEFT JOIN ("
            + "    SELECT t.account_id, t.game_id, t.rank, t.lp, t.time_start "
            + "    FROM ("
            + "        SELECT st.account_id, sm.game_id, st.rank, st.lp, sm.time_start, "
            + "        ROW_NUMBER() OVER (PARTITION BY st.account_id ORDER BY sm.time_start DESC) AS rn "
            + "        FROM summoner_tracking st "
            + "        JOIN summoner_match sm ON st.summoner_match_id = sm.id "
            + "        WHERE sm.time_start >= '" + new Timestamp(time_start) + "' "
            + "        AND sm.game_type = 43"
            + "    ) t "
            + "    WHERE t.rn = 1"
            + ") st ON s.account_id = st.account_id "
            + "WHERE s.tracking = 1;"
        );
    }





    public static QueryRecord getRegistredLolAccount(String account_id, long time_start) {
        return fetchJRow("SELECT s.account_id, s.league_shard, st.game_id, st.rank, st.lp, st.time_start "
                + "FROM summoner s "
                + "LEFT JOIN (SELECT t.account_id, t.game_id, t.rank, t.lp, t.time_start "
                + "           FROM (SELECT st.account_id, sm.game_id, st.rank, st.lp, sm.time_start, "
                + "                        ROW_NUMBER() OVER (PARTITION BY st.account_id ORDER BY sm.time_start DESC) AS rn "
                + "                 FROM summoner_tracking st "
                + "                 JOIN summoner_match sm ON st.summoner_match_id = sm.id "
                + "                 WHERE sm.time_start >= '" + new Timestamp(time_start) + "' "
                + "                   AND sm.game_type = 43 "
                + "                   AND st.account_id = '" + account_id + "') t "
                + "    WHERE t.rn = 1) st "
                + "ON s.account_id = st.account_id "
                + "WHERE s.tracking = 1 AND s.account_id = '" + account_id + "';");
    }




    public static boolean setSummonerData(String account_id, int summonerMatchId, boolean win, String kda, int rank, int lp, int gain, int champion, LaneType lane, TeamType side, String build) {
        return runQuery("INSERT IGNORE INTO summoner_tracking(account_id, summoner_match_id, win, kda, rank, lp, gain, champion, lane, side, build) VALUES('" + account_id + "', '" + summonerMatchId + "', '" + (win ? 1 : 0) + "', '" + kda + "', '" + rank + "', '" + lp + "', '" + gain + "', '" + champion + "', '" + lane.ordinal() + "', '" + side.ordinal() + "', '" + build + "');");
    }

    public static QueryCollection getFocusedSummoners(String query, LeagueShard shard) {
        return safJQuery("SELECT riot_id FROM summoner WHERE MATCH(riot_id) AGAINST('+" + query + "*' IN BOOLEAN MODE) AND league_shard = '" + shard.ordinal() + "' LIMIT 25;");
    }


    public static QueryCollection getSummonerData(String account_id, long game_id) {
        return safJQuery("SELECT account_id, game_id, rank, lp, gain, win time_start, patch FROM summoner_tracking WHERE account_id = '" + account_id + "' AND game_id = '" + game_id + "';");
    }

    public static QueryCollection getSummonerData(String account_id, LeagueShard shard, long time_start, long time_end) {
        return safJQuery("SELECT account_id, game_id, rank, lp, gain, win, time_start, time_end, patch FROM summoner_tracking WHERE account_id = '" + account_id + "' AND league_shard = '" + shard.ordinal() + "' AND time_start >= '" + new Timestamp(time_start) + "' AND time_end <= '" + new Timestamp(time_end) + "';");
    }

    public static QueryCollection getSummonerData(String account_id) {
        return safJQuery(
            "SELECT st.account_id, sm.game_id, st.rank, st.lp, st.gain, st.win, sm.time_start, sm.time_end, sm.patch " +
            "FROM summoner_tracking st " +
            "JOIN summoner_match sm ON st.summoner_match_id = sm.id " +
            "WHERE st.account_id = '" + account_id + "' AND sm.game_type = " + GameQueueType.TEAM_BUILDER_RANKED_SOLO.ordinal() + " " +
            "ORDER BY sm.game_id"
        );
    }

    public static boolean trackSummoner(String user_id, String account_id, boolean track) {
        return runQuery("UPDATE summoner SET tracking = '" + (track ? 1 : 0) + "' WHERE user_id = '" + user_id + "' AND account_id = '" + account_id + "';");
    }

    public static QueryRecord getSummonerData(String user_id, String account_id) {
        return fetchJRow("SELECT account_id, summoner_id, league_shard, tracking FROM summoner WHERE user_id = '" + user_id + "' AND account_id = '" + account_id + "';");
    }

    public static QueryCollection getSummonersBuPuuid(String puuid) {
        return safJQuery("SELECT account_id, league_shard FROM summoner WHERE puuid = '" + puuid + "';");
    }

    public static boolean setChampionData(LOLMatch match, HashMap<String, HashMap<String, String>> matchData) {
       String query = "INSERT INTO summoner_build(game_id, shard, game_type, champion, win, lane, starter, build, first_root, second_root, shard_root, summoner_spells, skill_order, patch, boots) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(query)) {
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

    @SuppressWarnings("unchecked")
    public static int setMatchData(LOLMatch match, boolean emptyIfExist) {
        int id = 0;

        Connection c = getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            PreparedStatement ps = c.prepareStatement("SELECT id FROM summoner_match WHERE game_id = ? AND league_shard = ?;");
            ps.setString(1, String.valueOf(match.getGameId()));
            ps.setInt(2, match.getPlatform().ordinal());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                id = emptyIfExist ? 0 : rs.getInt("id");
            } else{
                ps = c.prepareStatement("INSERT INTO summoner_match(game_id, league_shard, game_type, bans, time_start, time_end, patch) VALUES (?,?,?,?,?,?,?);");
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
                id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
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





    public static int createPlaylist(String name, String user_id) {
        int id = 0;

        Connection c = getConnection();
        if(c == null) return id;

        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO playlist(name, user_id) VALUES('" + name + "','" + user_id + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
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

    public static QueryCollection getPlaylists(String user_id) {
        return safJQuery("SELECT id, name, created_at FROM playlist WHERE user_id = '" + user_id + "';");
    }

    public static QueryRecord getPlaylist(String user_id, int playlist_id) {
        return fetchJRow("SELECT id, name, created_at FROM playlist WHERE user_id = '" + user_id + "' AND id = " + playlist_id + ";");
    }

    public static QueryCollection getPlaylistsWithSize(String user_id) {
        return safJQuery("SELECT id, name, created_at, (select count(*) as size from playlist_track where playlist_id = p.id) as size FROM playlist p WHERE user_id = '" + user_id + "';");
    }

    public static QueryRecord getPlaylistByIdWithSize(int playlist_id) {
        return fetchJRow("SELECT id, name, user_id, created_at, (select count(*) as size from playlist_track where playlist_id = p.id) as size FROM playlist p WHERE id = '" + playlist_id + "';");
    }

    public static int deletePlaylist(int playlist_id, String user_id) {

        Connection c = getConnection();
        if(c == null) return -2;

        try (Statement stmt = c.createStatement()) {
            QueryRecord search = fetchJRow("SELECT user_id FROM playlist WHERE id = '" + playlist_id + "';");

            if(search.isEmpty()) {
                return 0;
            }

            if(!search.get("user_id").equals(user_id)) {
                return -1;
            }

            runQuery(stmt, "DELETE FROM playlist WHERE id = '" + playlist_id + "';");
            c.commit();

            return 1;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -2;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }

    public static int deletePlaylistSong(int playlist_id, int song_id, String user_id) {
        Connection c = getConnection();
        if(c == null) return -2;

        try (Statement stmt = c.createStatement()) {
            QueryRecord search = fetchJRow("SELECT user_id FROM playlist WHERE id = '" + playlist_id + "';");

            if(search.isEmpty()) {
                return 0;
            }

            if(!search.get("user_id").equals(user_id)) {
                return -1;
            }

            runQuery(stmt, "DELETE FROM playlist_track WHERE id = '" + song_id + "';");
            c.commit();
            return 1;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -2;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }

    public static int updatePlaylistOrder(int playlist_id, String user_id, List<String> song_ids) {
        Connection c = getConnection();
        if(c == null) return -2;

        try (Statement stmt = c.createStatement()) {
            QueryRecord search = fetchJRow("SELECT user_id FROM playlist WHERE id = '" + playlist_id + "';");

            if(search.isEmpty()) {
                return 0;
            }

            if(!search.get("user_id").equals(user_id)) {
                return -1;
            }

            int order = 0;
            for(String song_id : song_ids) {
                runQuery(stmt, "UPDATE playlist_track SET `order` = '" + order + "' WHERE id = '" + song_id + "';");
                order++;
            }
            c.commit();
            return 1;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -2;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }


    public static boolean playlistExixtes(String name, String user_id) {
        return !safJQuery("SELECT 1 FROM playlist WHERE name = '" + name + "' AND user_id = '" + user_id + "'").isEmpty();
    }

    public static QueryCollection getPlaylistTracks(int playlist_id, Integer limit, Integer page) {
        String limitString = limit != null ? " LIMIT " + limit + " " : "";
        limitString += page != null ? " OFFSET " + (page * limit) + " " : "";
        return safJQuery("SELECT * FROM playlist_track WHERE playlist_id = " + playlist_id + " ORDER BY `order` ASC" + limitString);
    }

    public static boolean addTrackToPlaylist(int playlist_id, String uri, String encoded_track, Integer order) {
        if(order == null) {
            return runQuery("SET @max_order = (SELECT COALESCE(MAX(`order`), 0) FROM playlist_track WHERE playlist_id = " + playlist_id + ")", "INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES (" + playlist_id + ", '" + uri + "', '" + encoded_track + "', @max_order + 1);");
        } else {
            return runQuery("INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES (" + playlist_id + ", '" + uri + "', '" + encoded_track + "', " + order + ")");
        }
    }

    public static boolean addTrackToPlaylist(int playlist_id, List<AudioTrack> tracks, Integer order) {
        StringBuilder queryBuilder = new StringBuilder("INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES ");

        Connection c = getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            int currentOrder = order != null ? order : fetchJRow(stmt, "SELECT COALESCE(MAX(`order`), 0) AS max_order FROM playlist_track WHERE playlist_id = " + playlist_id).getAsInt("max_order");
            for (AudioTrack track : tracks) {
                queryBuilder.append("(")
                    .append(playlist_id).append(", '")
                    .append(track.getInfo().uri).append("', '")
                    .append(PlayerManager.get().encodeTrack(track)).append("', ")
                    .append(currentOrder++).append("),");
            }
            queryBuilder.setLength(queryBuilder.length() - 1);
            queryBuilder.append(";");
            runQuery(stmt, queryBuilder.toString());

            c.commit();
            return true;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return false;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }

    public static boolean addTrackToPlaylist(String playlist_name, String user_id, List<AudioTrack> tracks, Integer order) {
        StringBuilder queryBuilder = new StringBuilder("INSERT INTO playlist_track (playlist_id, uri, encoded_track, `order`) VALUES ");

        Connection c = getConnection();
        if(c == null) return false;

        try (Statement stmt = c.createStatement()) {
            int playlist_id = fetchJRow("SELECT id FROM playlist WHERE name = '" + playlist_name + "' AND user_id = '" + user_id + "'").getAsInt("id");
            int currentOrder = order != null ? order : fetchJRow(stmt, "SELECT COALESCE(MAX(`order`), 0) AS max_order FROM playlist_track WHERE playlist_id = " + playlist_id).getAsInt("max_order");
            for (AudioTrack track : tracks) {
                queryBuilder.append("(")
                    .append(playlist_id).append(", '")
                    .append(track.getInfo().uri).append("', '")
                    .append(PlayerManager.get().encodeTrack(track)).append("', ")
                    .append(currentOrder++).append("),");
            }
            queryBuilder.setLength(queryBuilder.length() - 1);
            queryBuilder.append(";");
            runQuery(stmt, queryBuilder.toString());

            c.commit();
            return true;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return false;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }

    public static int createAutomatedAction(String guildId, int action, String role, Integer actionTime, int infractions, Integer infractionsTime) {
        String query = "INSERT INTO automated_action (guild_id, action, action_role, action_time, infractions, infractions_time) VALUES(?, ?, ?, ?, ?, ?);";
        int id = 0;
        Connection c = getConnection();
        try (PreparedStatement pstmt = c.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, action);

            if (role != null) {
                pstmt.setString(3, role);
            } else {
                pstmt.setNull(3, Types.VARCHAR);
            }

            if (actionTime != null) {
                pstmt.setInt(4, actionTime);
            } else {
                pstmt.setNull(4, Types.INTEGER);
            }

            pstmt.setInt(5, infractions);

            if (infractionsTime != null) {
                pstmt.setInt(6, infractionsTime);
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }

            pstmt.executeUpdate();
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    id = generatedKeys.getInt(1);
                }
            }
            c.commit();
            return id;
        } catch (SQLException ex) {
            if (c != null) {
                try {
                    c.rollback();
                } catch (SQLException rollbackEx) {
                    System.out.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            System.out.println("Query execution failed: " + ex.getMessage());
            return -1;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (SQLException closeEx) {
                    System.out.println("Failed to close connection: " + closeEx.getMessage());
                }
            }
        }
    }

    public static int insertWarn(int memberId, String description) {
        Connection c = getConnection();
        if(c == null) return -1;

        int id = -1;
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO warning (member_id, reason) VALUES('" + memberId + "','" + description + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
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

    public static int getMemberWarnings(int memberId, int infractionsTime) {
        return fetchJRow("SELECT COUNT(*) AS warning_count FROM warning WHERE member_id = " + memberId + " AND `time` >= NOW() - INTERVAL " + infractionsTime + " SECOND " +  "GROUP BY member_id").getAsInt("warning_count");
    }

    public static int getMemberWarnings(int memberId) {
        return fetchJRow("SELECT COUNT(*) AS warning_count FROM warning WHERE member_id = " + memberId + " GROUP BY member_id").getAsInt("warning_count");
    }

    public static QueryCollection getWarnings(String valueOf) {
        return safJQuery("SELECT id, action, action_role, action_time, infractions, infractions_time FROM automated_action WHERE guild_id = '" + valueOf + "' ORDER BY infractions DESC");
    }

    public static QueryCollection getAutomatedActionsExpiring() {
        String query = "SELECT aae.*, u.user_id, u.guild_id " + "FROM automated_action_expiration aae " + "JOIN `member` u ON aae.member_id = u.id " + "WHERE aae.time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 2 HOUR)";
        return safJQuery(query);
    }

    public static String insertAutomatedActionExpiring(int member_id, int action, long time) {
        Connection c = getConnection();
        if(c == null) return "-1";;

        String id = "-1";
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO automated_action_expiration (member_id, action_id, time) VALUES('" + member_id + "','" + action + "','" + new Timestamp(time) + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").get("id");
            c.commit();
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

    public static String getMutedRole(String valueOf) {
        return fetchJRow("SELECT action_role FROM automated_action WHERE action = 1 AND guild_id = '" + valueOf + "';").get("action_role");
    }

    public static QueryCollection getMatchData() {
        return safJQuery("SELECT sm.id, sm.game_id, sm.league_shard, sm.game_type, sm.bans, sm.time_start, sm.time_end, sm.patch, st.account_id, st.win, st.kda, st.rank, st.lp, st.gain, st.champion, st.lane, st.side, st.build FROM summoner_tracking st JOIN summoner_match sm ON st.summoner_match_id = sm.id where sm.id > 10353;");
    }
    












    /**
     * What the actual fuck sunyx?
     * eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee la cannuccia
     * weru9fgt9uehrgferwfghreyuio
    */
    public static String getCannuccia() {
        return ":cannuccia:";
    }

    /**
     * @deprecated
     * deprecated this shit and use querySafe
     * @param s
     * @return
     */
    public static String fixSQL(String s){
        s = s.replace("\"", "\\\"");
        s = s.replace("\'", "\\\'");
        return s;
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

}
