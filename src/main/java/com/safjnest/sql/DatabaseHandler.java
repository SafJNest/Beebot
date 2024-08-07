package com.safjnest.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.safjnest.model.guild.alert.AlertSendType;
import com.safjnest.model.guild.alert.AlertType;
import com.safjnest.model.sound.Tag;
import com.safjnest.util.log.BotLogger;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

/**
 * Useless (now usefull) class but {@link <a href="https://github.com/Leon412">Leon412</a>} is one
 * of the biggest caterpies ever made
 */
public class DatabaseHandler {
    private static String hostName;
    private static String database;
    private static String user;
    private static String password;

    private static Connection c;

    private static HashMap<Long, List<String>> queryAnalytics = new HashMap<>();

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
        try {
            if (c != null && !c.isClosed()) return;

            Class.forName("org.mariadb.jdbc.Driver");
            c = DriverManager.getConnection("jdbc:mariadb://" + hostName + "/" + database + "?autoReconnect=true", user, password);
            c.setAutoCommit(false);
            BotLogger.info("[SQL] Connection to the extreme db successful!");
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            BotLogger.error("[SQL] Connection to the extreme db failed!");
        }
    }

    private static void insertAnalytics(String query) {
        List<String> queries = queryAnalytics.getOrDefault(System.currentTimeMillis(), new ArrayList<>());
        queries.add(query);
        queryAnalytics.put(System.currentTimeMillis(), queries);
    }

    public static QueryResult safJQuery(String query) {
        connectIfNot();

        QueryResult result = new QueryResult();
        
        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {

            ResultSetMetaData rsmd = rs.getMetaData();
            while (rs.next()) {
                ResultRow beeRow = new ResultRow();
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
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
            //return null;
        }

        return result; 
    }


    /**
     * Method used for returning a {@link com.safjnest.sql.QueryResult result} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */ 
    public static QueryResult safJQuery(Statement stmt, String query) throws SQLException {
        connectIfNot();

        QueryResult result = new QueryResult();

        ResultSet rs = stmt.executeQuery(query);

        ResultSetMetaData rsmd = rs.getMetaData();

        while (rs.next()) {
            ResultRow beeRow = new ResultRow();
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
     * Method used for returning a single {@link com.safjnest.sql.ResultRow row} from a query using default statement
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static ResultRow fetchJRow(String query) {
        connectIfNot();

        ResultRow beeRow = new ResultRow();

        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            
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
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
            //return null;
        }

        return beeRow;
    }


    /**
     * Method used for returning a single {@link com.safjnest.sql.ResultRow row} from a query.
     * @param stmt
     * @param query
     * @throws SQLException
     */
    public static ResultRow fetchJRow(Statement stmt, String query) throws SQLException {
        connectIfNot();
        
        ResultRow beeRow = new ResultRow();

        ResultSet rs = stmt.executeQuery(query);
            
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
     * Run one or more queries using the deffault statement
     * @param queries
     */
    public static boolean runQuery(String... queries) {
        connectIfNot();

        try (Statement stmt = c.createStatement()) {
            for (String query : queries) 
                stmt.execute(query);
            
            insertAnalytics(queries.toString());
            c.commit();
            return true;
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
            return false;
        }
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

    public static QueryResult getGuildsData(String filter){        
        String query = "SELECT guild_id, prefix, exp_enabled, threshold, blacklist_channel FROM guild WHERE " + filter + ";";
        return safJQuery(query);
    }


    public static QueryResult getlistGuildSounds(String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static QueryResult getlistGuildSounds(String guild_id, int limit) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC LIMIT " + limit);
    }

    public static QueryResult getlistGuildSounds(String guild_id, String orderBy) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY " + orderBy +" ASC ");
    }



    public static QueryResult getGuildRandomSound(String guild_id){
        return safJQuery("SELECT name, id FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getUserSound(String user_id){
        return safJQuery("SELECT name, id, guild_id, extension FROM sound WHERE user_id = '" + user_id + "';");
    }

    public static QueryResult getlistUserSounds(String user_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY name ASC");
    }

    public static QueryResult getlistUserSoundsTime(String user_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY time ASC");
    }

    public static QueryResult getlistUserSounds(String user_id, String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY name ASC");
    }

    public static QueryResult getlistUserSoundsTime(String user_id, String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY time ASC");
    }

    public static QueryResult getFocusedGuildSound(String guild_id, String like){
        return safJQuery("SELECT name, id FROM sound WHERE name LIKE '" + like + "%' AND guild_id = '" + guild_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getFocusedUserSound(String user_id, String like){
        return safJQuery("SELECT name, id, guild_id FROM sound WHERE (name LIKE '" + like + "%' OR id LIKE '" + like + "%') AND user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getUserGuildSounds(String user_id, String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' OR guild_id = '" + guild_id + "' ORDER BY name ASC");
    }

    public static QueryResult getFocusedListUserSounds(String user_id, String guild_id, String like) {
        return safJQuery("SELECT name, id, guild_id, extension FROM sound WHERE name LIKE '" + like + "%' OR id LIKE '" + like + "%' AND (user_id = '" + user_id + "' OR guild_id = '" + guild_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getSoundsById(String... sound_ids) {
        StringBuilder sb = new StringBuilder();
        for(String sound_id : sound_ids)
            sb.append(sound_id + ", ");
        sb.setLength(sb.length() - 2);

        return safJQuery("SELECT id, name, guild_id, user_id, extension, public, time, plays, likes, dislikes FROM sound WHERE id IN (" + sb.toString() + ");");
    }

    public static QueryResult getSoundsById(String id, String guild_id, String author_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static ResultRow getSoundById(String id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "'");
    } 

    public static QueryResult getSoundsByName(String name, String guild_id, String author_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryResult getDuplicateSoundsByName(String name, String guild_id, String author_id) {
        return safJQuery("SELECT id, guild_id, user_id FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "' OR user_id = '" + author_id + "')");
    }

    public static ResultRow getAuthorSoundById(String id, String user_id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE id = '" + id + "' AND user_id = '" + user_id + "'");
    }

    public static ResultRow getAuthorSoundByName(String name, String user_id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public, time FROM sound WHERE name = '" + name + "' AND user_id = '" + user_id + "'");
    }

    public static String insertSound(String name, String guild_id, String user_id, String extension, boolean isPublic) {
        String soundId = null;
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO sound(name, guild_id, user_id, extension, public, time) VALUES('" + name + "','" + guild_id + "','" + user_id + "','" + extension + "', " + ((isPublic == true) ? "1" : "0") + ", '" +  Timestamp.from(Instant.now()) + "'); ");
            soundId = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").get("id");
            c.commit();
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
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

    public static boolean updateUserPlays(String sound_id, String user_id) {
        return runQuery("INSERT INTO play(user_id, sound_id, times, last_play) VALUES('" + user_id + "', '" + sound_id + "', 1, '" + Timestamp.from(Instant.now()) + "') ON DUPLICATE KEY UPDATE times = times + 1, last_play = '" + Timestamp.from(Instant.now()) + "';", "UPDATE sound SET plays = plays + 1 WHERE id = '" + sound_id + "';");
    }

    public static ResultRow getPlays(String sound_id, String user_id) {
        return fetchJRow("SELECT times FROM play WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "'");
    }

    public static ResultRow getGlobalPlays(String sound_id) {
        return fetchJRow("SELECT sum(times) as times FROM play WHERE sound_id = '" + sound_id + "'");
    }

    public static String getSoundsUploadedByUserCount(String user_id) {
        return fetchJRow("select count(name) as count from sound where user_id = '" + user_id + "';").get("count");
    }

    public static String getSoundsUploadedByUserCount(String user_id, String guild_id) {
        return fetchJRow("select count(name) as count from sound where guild_id = '" + guild_id + "' AND user_id = '" + user_id + "';").get("count");
    }

    public static String getTotalPlays(String user_id) {
        return fetchJRow("select sum(times) as sum from play where user_id = '" + user_id + "';").get("sum");
    }

    public static boolean soundboardExists(String id, String guild_id) {
        return !fetchJRow("SELECT id from soundboard WHERE ID = '" + id + "' AND guild_id = '" + guild_id + "'").emptyValues();
    }

    public static int getSoundInSoundboardCount(String id) {
        return fetchJRow("SELECT count(sound_id) as cont FROM soundboard_sounds WHERE id = '" + id + "'").getAsInt("count");
    }

    public static QueryResult getSoundsFromSoundBoard(String id) {
        return safJQuery("select soundboard_sounds.sound_id as sound_id, sound.extension as extension, sound.name as name, sound.guild_id as guild_id from soundboard_sounds join soundboard on soundboard.id = soundboard_sounds.id join sound on soundboard_sounds.sound_id = sound.id where soundboard.id = '" + id + "'");
    }

    public static ResultRow getSoundboardByID(String id) {
        return fetchJRow("select name from soundboard where id = '" + id + "'");
    }
    
    public static QueryResult getRandomSoundboard(String guild_id, String user_id) {
        return safJQuery("SELECT name, id, guild_id FROM soundboard WHERE guild_id = '" + guild_id + "' OR user_id = '" + user_id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getFocusedSoundboard(String guild_id, String user_id, String like){
        return safJQuery("SELECT name, id, guild_id FROM soundboard WHERE name LIKE '" + like + "%' AND (guild_id = '" + guild_id + "' OR user_id = '" + user_id + "') ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult getFocusedSoundFromSounboard(String id, String like){
        return safJQuery("SELECT s.name as name, s.id as sound_id, s.guild_id as guild_id FROM soundboard_sounds ss JOIN sound s ON ss.sound_id = s.id WHERE s.name LIKE '" + like + "%' AND ss.id = '" + id + "' ORDER BY RAND() LIMIT 25;");
    }

    public static QueryResult extremeSoundResearch(String query) {
        return safJQuery("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE s.name like '%" + query + "%' OR t.name like '%" + query + "%';");
        //return safJQuery("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE MATCH(s.name) AGAINST ('" + query + "') OR t.name like '%" + query + "%';");
    }

    public static QueryResult extremeSoundResearch(String query, String user_id) {
        return safJQuery("SELECT DISTINCT s.* FROM sound s LEFT JOIN tag_sounds ts ON s.id = ts.sound_id LEFT JOIN tag t ON ts.tag_id = t.id WHERE s.user_id = " + user_id + " AND (MATCH(s.name) AGAINST ('" + query + "') OR t.name like '%" + query + "%');");
    }



    public static boolean insertSoundBoard(String name, String guild_id, String user_id, String... sound_ids) {
        if(sound_ids.length == 0) throw new IllegalArgumentException("sound_ids must not be empty");

        StringBuilder sb = new StringBuilder();

        for (String sound_id : sound_ids)
            sb.append("(LAST_INSERT_ID(), " + sound_id + "), ");
        sb.setLength(sb.length() - 2);
        
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO soundboard (name, guild_id, user_id) VALUES ('" + name + "', '" + guild_id + "', '" + user_id + "'); ");
            runQuery(stmt, "INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + ";");
            c.commit();
            return true;
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
            return false;
        }
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
 
    public static ResultRow getDefaultVoice(String guild_id) {
        return fetchJRow("SELECT name_tts, language_tts FROM guild WHERE guild_id = '" + guild_id + "';");
    }

    
    public static QueryResult getLOLAccountsByUserId(String user_id){
        String query = "SELECT account_id, league_shard, tracking FROM summoner WHERE user_id = '" + user_id + "';";
        return safJQuery(query);
    }

    public static String getUserIdByLOLAccountId(String account_id) {
        return fetchJRow("SELECT user_id FROM summoner WHERE account_id = '" + account_id + "';").get("user_id");
    }

    

    public static boolean addLOLAccount(String user_id, String summoner_id, String account_id, LeagueShard shard) {
        String query = "INSERT INTO summoner(user_id, summoner_id, account_id, league_shard) VALUES('" + user_id + "','" + summoner_id + "','" + account_id + "','" + shard.ordinal() + "');";
        return runQuery(query);
    }

    public static boolean deleteLOLaccount(String user_id, String account_id){
        String query = "DELETE FROM summoner WHERE account_id = '" + account_id + "' and user_id = '" + user_id + "';";
        return runQuery(query);
    }

    public static QueryResult getGuildData(){
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel, blacklist_enabled FROM guild;";
        return safJQuery(query);
    }
    
    public static ResultRow getGuildData(String guild_id) {
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel, blacklist_enabled, league_shard FROM guild WHERE guild_id = '" + guild_id + "';";
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


    public static QueryResult getUsersByExp(String guild_id, int limit) {
        if (limit == 0) {
            return safJQuery("SELECT user_id, messages, level, experience as exp from user WHERE guild_id = '" + guild_id + "' order by experience DESC;");
        }
        return safJQuery("SELECT user_id, messages, level, experience as exp from user WHERE guild_id = '" + guild_id + "' order by experience DESC limit " + limit + ";");
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

    public static QueryResult getGuildByThreshold(int threshold, String guild_id){
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
    public static ResultRow getGreet(String user_id, String guild_id) {
        return fetchJRow("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND (greeting.guild_id = '" + guild_id + "' OR greeting.guild_id = '0') ORDER BY CASE WHEN greeting.guild_id = '0' THEN 1 ELSE 0 END LIMIT 1;");
    }

    public static ResultRow getSpecificGuildGreet(String user_id, String guild_id) {
        return fetchJRow("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND greeting.guild_id = '" + guild_id + "' LIMIT 1;");
    }

    public static ResultRow getGlobalGreet(String user_id) {
        return fetchJRow("SELECT sound.id, sound.extension from greeting join sound on greeting.sound_id = sound.id WHERE greeting.user_id = '" + user_id + "' AND greeting.guild_id = '0' LIMIT 1;");
    }


    public static boolean setAlertMessage(String ID, String message) {
        try (PreparedStatement pstmt = c.prepareStatement("UPDATE alert SET message = ? WHERE ID = ?")) {
            pstmt.setString(1, message);
            pstmt.setString(2, ID);
            int affectedRows = pstmt.executeUpdate();
            c.commit();
            return affectedRows > 0;
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
            return false;
        }
    }

    public static boolean setAlertPrivateMessage(String ID, String message) {
        try (PreparedStatement pstmt = c.prepareStatement("UPDATE alert SET private_message = ? WHERE ID = ?")) {
            pstmt.setString(1, message);
            pstmt.setString(2, ID);
            int affectedRows = pstmt.executeUpdate();
            c.commit();
            return affectedRows > 0;
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
            return false;
        }
    }

    public static boolean setAlertChannel(String ID, String channel) {
        return runQuery("UPDATE alert SET channel = '" + channel + "' WHERE ID = '" + ID + "';");
    }

    public static boolean setAlertEnabled(String ID, boolean toggle) {
        return runQuery("UPDATE alert SET enabled = '" + (toggle ? 1 : 0) + "' WHERE ID = '" + ID + "';");
    }

    public static QueryResult getAlerts(String guild_id) {
        return safJQuery("SELECT id, message, private_message, channel, enabled, send_type, type FROM alert WHERE guild_id = '" + guild_id + "';");
    }

    public static QueryResult getAlertsRoles(String guild_id) {
        return safJQuery("SELECT r.id as row_id, a.id as alert_id, r.role_id as role_id  FROM alert_role as r JOIN alert as a ON r.alert_id = a.id WHERE a.guild_id = '" + guild_id + "';");
    }

    public static int createAlert(String guild_id, String message, String privateMessage, String channelId, AlertSendType sendType, AlertType type) {
        int id = 0;
        String query = "INSERT INTO alert(guild_id, message, private_message, channel, enabled, send_type, type) VALUES(?, ?, ?, ?, 1, ?, ?);";
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
            try {
                if (c != null) c.rollback();
            } catch (SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
        }
        return id;
    }

    public static boolean createRewardData(String alertID, int level, boolean temporary) {
        return runQuery("INSERT INTO alert_reward(alert_id, level, temporary) VALUES('" + alertID + "', '" + level + "', '" + (temporary ? 1 : 0) + "');");
    }

    public static QueryResult getRewardData(String guild_id) {
        return safJQuery("SELECT r.id as id, a.id as alert_id, r.level as level, r.temporary as temporary FROM alert as a JOIN alert_reward as r ON a.id = r.alert_id WHERE a.guild_id = '" + guild_id + "';");
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
            QueryResult result = safJQuery("SELECT id, role_id FROM alert_role WHERE alert_id = '" + valueOf + "';");
            for(ResultRow row : result) {
                roleMap.put(row.getAsInt("id"), row.get("role_id"));
            }
            return roleMap;
        }

        return null;
                
    }


    public static int insertChannelData(long guild_id, long channel_id) {
        int id = 0;
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO channel(guild_id, channel_id) VALUES('" + guild_id + "','" + channel_id + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
        }
        return id;
    }

    public static QueryResult getChannelData(String guild_id) {
        return safJQuery("SELECT id, channel_id, exp_enabled, exp_modifier, stats_enabled FROM channel WHERE guild_id = '" + guild_id + "';");
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


    public static int insertUserData(long guild_id, long user_id) {
        int id = 0;
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO user(guild_id, user_id) VALUES('" + guild_id + "','" + user_id + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
        }
        return id;
    }

    public static ResultRow getUserData(String guild_id, long user_id) {
        return fetchJRow("SELECT id, experience, level, messages, update_time FROM user WHERE user_id = '"+ user_id +"' AND guild_id = '" + guild_id + "';");
    }

    public static boolean updateUserDataExperience(int ID, int experience, int level, int messages) {
        return runQuery("UPDATE user SET experience = '" + experience + "', level = '" + level + "', messages = '" + messages + "' WHERE id = '" + ID + "';");
    }

    public static boolean updateUserDataUpdateTime(int ID, int updateTime) {
        return runQuery("UPDATE user SET update_time = '" + updateTime + "' WHERE id = '" + ID + "';");
    }

    public static ResultRow getUserExp(String id, String id2) {
        return fetchJRow("SELECT experience, level, messages FROM user WHERE user_id = '" + id + "' AND guild_id = '" + id2 + "'");
    }


    public static QueryResult getAliases(String user_id) {
        return safJQuery("SELECT id, name, command FROM alias WHERE user_id = '" + user_id + "';");
    }

    public static int createAlias(String user_id, String name, String command) {
        int id = 0;
        try (Statement stmt = c.createStatement()) {
            runQuery(stmt, "INSERT INTO alias(user_id, name, command) VALUES('" + user_id + "','" + name + "','" + command + "');");
            id = fetchJRow(stmt, "SELECT LAST_INSERT_ID() AS id; ").getAsInt("id");
            c.commit();
        } catch (SQLException ex) {
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
        }
        return id;
    }

    public static boolean deleteAlias(String toDelete) {
        return runQuery("DELETE FROM alias WHERE id = '" + toDelete + "';");
    }



    public static HashMap<String, QueryResult> getCustomCommandData(String guild_id) {
        HashMap<String, QueryResult> commandData = new HashMap<>();
        QueryResult result = DatabaseHandler.safJQuery("SELECT ID,name,description,slash FROM commands WHERE guild_id = " + guild_id);

        if (result.isEmpty()) {
            return null;
        }
        commandData.put("commands", result);

        String ids = String.join(", ", result.arrayColumn("ID"));
        
        QueryResult optionResult = DatabaseHandler.safJQuery("SELECT ID,command_id,`key`,description,required,type FROM command_option WHERE command_id IN (" + ids + ")");
        commandData.put("options", optionResult);

        QueryResult valueResult = null;
        if (!optionResult.isEmpty()) {
            String optionIds = String.join(", ", optionResult.arrayColumn("ID"));
            valueResult = DatabaseHandler.safJQuery("SELECT ID,option_id,`key`,value FROM command_option_value WHERE option_id IN (" + optionIds + ")");
            commandData.put("values", valueResult);
        }

        QueryResult taskResult = DatabaseHandler.safJQuery("SELECT ID,command_id,type,`order` FROM command_task WHERE command_id IN (" + ids + ") order by `order`");
        commandData.put("tasks", taskResult);

        String taskIds = String.join(", ", taskResult.arrayColumn("ID"));
        QueryResult taskValueResult = DatabaseHandler.safJQuery("SELECT ID,task_id,value,from_option FROM command_task_value WHERE task_id IN (" + taskIds + ")");
        commandData.put("task_values", taskValueResult);
        
        String taskValueIds = String.join(", ", taskValueResult.arrayColumn("ID"));
        QueryResult taskMessage = DatabaseHandler.safJQuery("SELECT ID,task_value_id,message FROM command_task_message WHERE task_value_id IN (" + taskValueIds + ")");
        if (!taskMessage.isEmpty()) {
            commandData.put("task_messages", taskMessage);
        }
        return commandData;
    }

    public static boolean updateShard(String valueOf, LeagueShard shard) {
        return runQuery("UPDATE guild SET league_shard = '" + shard.ordinal() + "' WHERE guild_id = '" + valueOf + "';");
    }

    public static QueryResult getTwitchSubscriptions(String streamer_id) {
        return safJQuery("SELECT guild_id, channel_id, message, streamer_id FROM twitch_subscription WHERE streamer_id = '" + streamer_id + "';");
    }

    public static QueryResult getTwitchSubscriptionsGuild(String guild_id) {
        return safJQuery("SELECT guild_id, channel_id, message, streamer_id FROM twitch_subscription WHERE guild_id = '" + guild_id + "';");
    }

    public static ResultRow getTwitchSubscriptionsGuild(String streamer_id, String guild_id) {
        return fetchJRow("SELECT guild_id, channel_id, message, streamer_id FROM twitch_subscription WHERE streamer_id = '" + streamer_id + "' AND guild_id = '" + guild_id + "';");
    }

    public static boolean setTwitchSubscriptions(String streamer_id, String guild_id, String channel_id, String message) {
        // Adjusted SQL statement to handle duplicate key by updating existing record
        String sql = "INSERT INTO twitch_subscription(streamer_id, guild_id, channel_id, message) VALUES(?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE channel_id = VALUES(channel_id), message = VALUES(message);";
        try {
            PreparedStatement pstmt = c.prepareStatement(sql);
            pstmt.setObject(1, streamer_id);
            pstmt.setObject(2, guild_id);
            pstmt.setObject(3, channel_id);
            pstmt.setObject(4, message);
    
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public static boolean updateTwitchSubscription(String streamer_id, String guild_id, String channel_id, String message) {
        StringBuilder sql = new StringBuilder("UPDATE twitch_subscription SET ");
        boolean first = true;
    
        if (channel_id != null) {
            sql.append("channel_id = ?");
            first = false;
        }

        if (message != null) {
            if (!first) {
                sql.append(", ");
            }
            sql.append("message = ?");
        }
        sql.append(" WHERE streamer_id = ? AND guild_id = ?;");
        
        try {
            PreparedStatement pstmt = c.prepareStatement(sql.toString());
    
            int parameterIndex = 1;
    
            if (channel_id != null) {
                pstmt.setObject(parameterIndex++, channel_id);
            }
    
            if (message != null) {
                pstmt.setObject(parameterIndex++, message);
            }
    
            pstmt.setObject(parameterIndex++, streamer_id);
            pstmt.setObject(parameterIndex, guild_id);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0; // Returns true if at least one row was updated
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    public static boolean deleteTwitchSubscription(String streamer_id, String guild_id) {
        return runQuery("DELETE FROM twitch_subscription WHERE streamer_id = '" + streamer_id + "' AND guild_id = '" + guild_id + "';");
    }

    public static QueryResult getSoundTags(String sound_id) {
        return safJQuery("SELECT ts.tag_id as id,t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id = '" + sound_id + "';");
    }

    public static QueryResult getSoundsTags(String ...sound_id) {
        StringBuilder sb = new StringBuilder();
        for(String sound : sound_id) {
            sb.append("'" + sound + "', ");
        }
        sb.setLength(sb.length() - 2);
        return safJQuery("SELECT ts.sound_id as sound_id, ts.tag_id as tag_id, t.name as name FROM tag_sounds ts JOIN tag t ON ts.tag_id = t.id WHERE ts.sound_id IN (" + sb.toString() + ");");
    }

    public static ResultRow getTag(String tag_id) {
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
            try {
                if(c != null) c.rollback();
            } catch(SQLException e) {
                System.out.println(e.getMessage());
            }
            System.out.println(ex.getMessage());
        }
        return id;
    }

    public static ResultRow getLikeDislike(String sound_id) {
        return fetchJRow("SELECT"
            + "(SELECT SUM(`like`) FROM play WHERE sound_id = '" + sound_id + "') AS likes,"
            + "(SELECT SUM(dislike) FROM play WHERE sound_id = '" + sound_id + "') AS dislikes;");
    }

    public static ResultRow getLikeDislikeUser(String sound_id, String user_id) {
        return fetchJRow("SELECT `like`, dislike FROM play WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "';");
    }


    public static boolean setLikeDislike(String sound_id, String user_id, boolean like, boolean dislike) {
        ResultRow userLike = getLikeDislikeUser(sound_id, user_id);

        String likePart = "";
        String dislikePart = "";
        
        // Determine the action for likes
        if (like && !userLike.getAsBoolean("like")) {
            // If the user is liking for the first time
            likePart = "`likes` = `likes` + 1";
        } else if (!like && userLike.getAsBoolean("like")) {
            // If the user is removing their like
            likePart = "`likes` = `likes` - 1";
        }
        
        // Determine the action for dislikes
        if (dislike && !userLike.getAsBoolean("dislike")) {
            // If the user is disliking for the first time
            dislikePart = "dislikes = dislikes + 1";
        } else if (!dislike && userLike.getAsBoolean("dislike")) {
            // If the user is removing their dislike
            dislikePart = "dislikes = dislikes - 1";
        }
        
        // Combine the parts to form the full update query, ensuring we only include non-empty parts
        String updateQuery = "UPDATE sound SET ";
        if (!likePart.isEmpty() && !dislikePart.isEmpty()) {
            updateQuery += likePart + ", " + dislikePart;
        } else if (!likePart.isEmpty()) {
            updateQuery += likePart;
        } else if (!dislikePart.isEmpty()) {
            updateQuery += dislikePart;
        }
    
        if (!likePart.isEmpty() || !dislikePart.isEmpty()) {
            updateQuery += " WHERE id = '" + sound_id + "';";
        }
        boolean q1 =  runQuery("INSERT INTO play(user_id, sound_id, `like`, dislike) VALUES('" + user_id + "','" + sound_id + "', " + (like ? 1 : 0) + ", " + (dislike ? 1 : 0) + ") ON DUPLICATE KEY UPDATE `like` = " + (like ? 1 : 0) + ", dislike = " + (dislike ? 1 : 0) + ";");
        boolean q2 = runQuery(updateQuery);
        return q1 && q2;
    }

    public static ResultRow hasInterectedSound(String sound_id, String user_id) {
        return fetchJRow("SELECT `like`, dislike FROM play WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "';");
    }


    public static QueryResult getRegistredLolAccount() {
        return safJQuery("SELECT s.account_id, s.league_shard, st.game_id, st.rank, st.lp, st.time_start FROM summoner s LEFT JOIN (SELECT account_id, game_id, rank, lp, time_start FROM summoner_tracking WHERE (account_id, time_start) IN (SELECT account_id, MAX(time_start) AS latest_time FROM summoner_tracking GROUP BY account_id)) st ON s.account_id = st.account_id WHERE s.tracking = 1;");
    }

    public static boolean setSummonerData(String account_id, long game_id, LeagueShard shard, boolean win, int rank, int lp, int gain, int champion, long time_start, long time_end, String version) {
        return runQuery("INSERT INTO summoner_tracking(account_id, game_id, league_shard, win, rank, lp, gain, champion, time_start, time_end, patch) VALUES('" + account_id + "','" + game_id + "','" + shard.ordinal() + "','" + (win ? 1 : 0) + "','" + rank + "','" + lp + "','" + gain + "','" + champion + "','" + new Timestamp(time_start) + "','" + new Timestamp(time_end) + "','" + version + "');");
    }

    public static QueryResult getSummonerData(String account_id) {
        return safJQuery("SELECT rank, lp, wins, losses, time_start, time_end, patch FROM summoner_tracking WHERE account_id = '" + account_id + "';");
    }

    public static QueryResult getSummonerData(String account_id, long game_id) {
        return safJQuery("SELECT account_id, game_id, rank, lp, gain, win time_start, patch FROM summoner_tracking WHERE account_id = '" + account_id + "' AND game_id = '" + game_id + "';");
    }

    public static QueryResult getSummonerData(String account_id, String[] game_id) {
        return safJQuery("SELECT account_id, game_id, rank, lp, gain, win time_start, time_end, patch FROM summoner_tracking WHERE account_id = '" + account_id + "' AND game_id IN ('" + String.join("', '", game_id) + "');");
    }

    public static boolean trackSummoner(String user_id, String account_id, boolean track) {
        return runQuery("UPDATE summoner SET tracking = '" + (track ? 1 : 0) + "' WHERE user_id = '" + user_id + "' AND account_id = '" + account_id + "';");
    }

    public static ResultRow getSummonerData(String user_id, String account_id) {
        return fetchJRow("SELECT account_id, summoner_id, league_shard, tracking FROM summoner WHERE user_id = '" + user_id + "' AND account_id = '" + account_id + "';");
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

    public static Connection getConnection(){
        return c;
    }

}