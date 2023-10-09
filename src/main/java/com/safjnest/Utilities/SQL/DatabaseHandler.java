package com.safjnest.Utilities.SQL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;


/**
 * Useless class but {@link <a href="https://github.com/Leon412">Leon412</a>} is one
 * of the biggest caterpies ever made
 */
public class DatabaseHandler {

    /** Object that opens the connection between database and beeby */
    private static Connection c;

    /**
     * Constructor
     * 
     * @param hostName Hostname, as 'keria123.eu-west-1.compute.fakerAws.com'
     * @param database Name of the database to connect in
     * @param user Username
     * @param password Password
     */
    public DatabaseHandler(String hostName, String database, String user, String password){
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            c = DriverManager
                .getConnection("jdbc:mysql://"+hostName+"/"+ database + "?autoReconnect=true",user,password);
            System.out.println("[SQL] INFO Connection to the extreme db successful!");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName()+": "+e.getMessage());
            System.out.println("[SQL] INFO Connection to the extreme db ANNODAM!");;
        }
    }

    /**
    * Useless method but {@link <a href="https://github.com/NeutronSun">NeutronSun</a>} is one
    * of the biggest bellsprout ever made
    */
	public void doSomethingSoSunxIsNotHurtBySeeingTheFuckingThingSayItsNotUsed() {
        return;
	}

    public static QueryResult safJQuery(String query) {
        QueryResult result = new QueryResult();
        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData rsmd = rs.getMetaData();
            while (rs.next()) {
                Map<String, String> row = new HashMap<>();
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnName(i);
                    String columnValue = rs.getString(i);
                    row.put(columnName, columnValue);
                }
                ResultRow beeRow = new ResultRow(row);
                result.add(beeRow);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result; 
    }

    public static ResultRow fetchJRow(String query) {
        ResultRow beeRow = null;

        try (Statement stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData rsmd = rs.getMetaData();
            if (rs.next()) {
                Map<String, String> row = new HashMap<>();
                for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                    String columnName = rsmd.getColumnName(i);
                    String columnValue = rs.getString(i);
                    row.put(columnName, columnValue);
                }
                beeRow = new ResultRow(row);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return beeRow;
    }

    public static boolean runQuery(String query){
        Statement stmt;
        try {
            stmt = c.createStatement();
            stmt.executeUpdate(query);
            stmt.close();
            return true;
        }
        catch (SQLException e1) {e1.printStackTrace(); return false;}
    }


    public static QueryResult getGuildsData(String filter){        
        String query = "SELECT guild_id, prefix, exp_enabled, threshold, blacklist_channel FROM guild_settings WHERE " + filter + ";";
        return safJQuery(query);
    }

    public static QueryResult getRoomsData(String filter){        
        String query = "SELECT guild_id, room_id, room_name, has_exp, exp_value, has_command_stats FROM rooms_settings WHERE " + filter + ";";
        return safJQuery(query);
    }

    public static QueryResult getlistGuildSounds(String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE guild_id = '" + guild_id + "' ORDER BY name ASC LIMIT 24");
    }

    public static QueryResult getlistUserSounds(String user_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' ORDER BY name ASC LIMIT 24");
    }

    public static QueryResult getlistUserSounds(String user_id, String guild_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1) ORDER BY name ASC LIMIT 24");
    }

    public static QueryResult getSoundsById(String... sound_ids) {
        StringBuilder sb = new StringBuilder();
        for(String sound_id : sound_ids) {
            sb.append("('" + sound_id + "'), ");
        }
        sb.setLength(sb.length() - 2);
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE id IN " + sb.toString() + ";");
    }

    public static QueryResult getSoundsById(String id, String guild_id, String author_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE id = '" + id + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryResult getSoundsByName(String name, String guild_id, String author_id) {
        return safJQuery("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryResult getDuplicateSoundsByName(String name, String guild_id, String author_id) {
        return safJQuery("SELECT id, guild_id, user_id FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "' OR user_id = '" + author_id + "')");
    }

    public static ResultRow getAuthorSoundById(String id, String user_id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE id = '" + id + "' AND user_id = '" + user_id + "'");
    }

    public static ResultRow getAuthorSoundByName(String name, String user_id) {
        return fetchJRow("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE name = '" + name + "' AND user_id = '" + user_id + "'");
    }

    public static String insertSound(String name, String guild_id, String user_id, String extension, boolean isPublic) {
        return fetchJRow("START TRANSACTION; "
            + "INSERT INTO sound(name, guild_id, user_id, extension, public) VALUES('" + name + "','" + guild_id + "','" + user_id + "','" + extension + "', " + ((isPublic == true) ? "1" : "0") + "); "
            + "SELECT LAST_INSERT_ID() AS id; "
            + "COMMIT;")
            .get("id"
        );
    }

    public static boolean updateSound(String id, String name, boolean isPublic) {
        return runQuery("UPDATE sound SET name = '" + name + "', public = '" + (isPublic ? "1" : "0") + "' WHERE id = '" + id + "';");
    }

    public static boolean deleteSound(String id) {
        return runQuery("DELETE FROM sound WHERE id = " + id + ";");
    }

    public static boolean updateUserPlays(String sound_id, String user_id) {
        return runQuery("INSERT INTO play(user_id, sound_id, times) VALUES('" + user_id + "','" + sound_id + "', 1) ON DUPLICATE KEY UPDATE times = times + 1;");
    }

    public static ResultRow getPlays(String sound_id, String user_id) {
        return fetchJRow("SELECT"
            + "(SELECT SUM(times) FROM play WHERE sound_id = '" + sound_id + "') AS totalTimes,"
            + "(SELECT times FROM play WHERE sound_id = '" + sound_id + "' AND user_id = '" + user_id + "') AS timesByUser;");
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

    public static boolean soundboardExists(String name, String guild_id) {
        return !safJQuery("SELECT count(name) as count from soundboard WHERE name = '" + name + "' guild_id = '" + guild_id + "'").isEmpty();
    }

    public static int getSoundInSoundboardCount(String id) {
        return fetchJRow("SELECT count(sound_id) as cont FROM soundboard_sounds WHERE id = '" + id + "'").getAsInt("count");
    }

    public static QueryResult getSoundsFromSoundBoard(String id) {
        return safJQuery("select soundboard_sounds.sound_id, sound.extension, sound.name from soundboard_sounds join soundboard on soundboard.id = soundboard_sounds.id join sound on soundboard_sounds.sound_id = sound.id where soundboard.id = '" + id + "'");
    }

    public static ResultRow getSoundboardByID(String id) {
        return fetchJRow("select name from soundboard where id = '" + id + "'");
    }

    public static boolean insertSoundBoard(String name, String guild_id, String... sound_ids) {
        if(sound_ids.length == 0) throw new IllegalArgumentException("sound_ids must not be empty");

        StringBuilder sb = new StringBuilder();
        for(String sound_id : sound_ids) {
            sb.append("(@soundboard_id, '" + sound_id + "'), ");
        }
        sb.setLength(sb.length() - 2);

        return runQuery("START TRANSACTION; "
            + "INSERT INTO soundboard (name, guild_id) VALUES ('" + name + "', '" + guild_id + "'); "
            + "SET @soundboard_id = LAST_INSERT_ID(); "
            + "INSERT INTO soundboard_sounds (id, sound_id) VALUES " + sb.toString() + "; "
            + "COMMIT;"
        );
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
 
    public static ResultRow getDefaultVoice(String guild_id, String bot_id) {
        return fetchJRow("SELECT name_tts, language_tts FROM guild_settings WHERE guild_id = '" + guild_id + "' AND bot_id = '" + bot_id + "';");
    }

    public static String getLOLAccountIdByUserId(String user_id){
        String query = "SELECT account_id FROM lol_user WHERE user_id = '" + user_id + "';";
        return fetchJRow(query).get("account_id");
    }

    public static QueryResult getLOLAccountsByUserId(String user_id){
        String query = "SELECT account_id FROM lol_user WHERE user_id = '" + user_id + "';";
        return safJQuery(query);
    }

    public static String getUserIdByLOLAccountId(String account_id) {
        return fetchJRow("SELECT user_id FROM lol_user WHERE account_id = '" + account_id + "';").get("user_id");
    }

    

    public static boolean addLOLAccount(String user_id, String summoner_id, String account_id){
        String query = "INSERT INTO lol_user(user_id, summoner_id, account_id) VALUES('" + user_id + "','" + summoner_id + "','" + account_id + "');";
        return runQuery(query);
    }

    public static boolean deleteLOLaccount(String user_id, String account_id){
        String query = "DELETE FROM lol_user WHERE account_id = '" + account_id + "' and user_id = '" + user_id + "';";
        return runQuery(query);
    }

    public static String getLolProfilesCount(String user_id){
        String query = "SELECT count(user_id) as count FROM lol_user WHERE user_id = '" + user_id + "';";
        return fetchJRow(query).get("count");
    }

    public static QueryResult getGuildData(String bot_id){
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel FROM guild_settings WHERE bot_id = '" + bot_id + "';";
        return safJQuery(query);
    }
    
    public static ResultRow getGuildData(String guild_id, String bot_id){
        String query = "SELECT guild_id, PREFIX, exp_enabled, threshold, blacklist_channel FROM guild_settings WHERE guild_id = '" + guild_id + "' AND bot_id = '" + bot_id + "';";
        return fetchJRow(query);
    }

    public static boolean insertGuild(String guild_id, String bot_id, String prefix){
        String query = "INSERT INTO guild_settings (guild_id, bot_id, PREFIX, exp_enabled, threshold, blacklist_channel) VALUES ('" + guild_id + "', '" + bot_id + "', '" + prefix + "', '0', '0', null); ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';";
        return runQuery(query);
    }

    public static boolean updateVoiceGuild(String guild_id, String bot_id, String language, String voice){
        String query = "INSERT INTO guild_settings (guild_id, bot_id, language_tts, name_tts) VALUES ('" + guild_id + "', '" + bot_id + "', '" + language + "', '" + voice + "') ON DUPLICATE KEY UPDATE language_tts = '" + language + "', name_tts = '" + voice + "'";
        return runQuery(query);
    }

    public static QueryResult getRoomData(String guild_id){
        String query = "SELECT room_id, room_name, has_exp, exp_value, has_command_stats FROM rooms_settings WHERE guild_id ='" + guild_id + "';";
        return safJQuery(query);
    }

    public static ResultRow getExp(String guild_id, String user_id){
        String query = "SELECT exp, level, messages FROM exp_table WHERE user_id = '" + user_id + "' AND guild_id = '" + guild_id + "';";
        return fetchJRow(query);
    }

    public static boolean addExpData(String guild_id, String user_id){
        String query = "INSERT INTO exp_table (user_id, guild_id, exp, level, messages) VALUES ('" + user_id + "','" + guild_id + "',0,1,0);";
        return runQuery(query);
    }

    public static boolean updateExp(String guild_id, String user_id, int exp, int level, int messages){
        String  query = "UPDATE exp_table SET exp = " + exp + ", level = " + level + ", messages = " + messages + " WHERE user_id = '" + user_id + "' AND guild_id = '" + guild_id + "';";
        return runQuery(query);
    }

    public static boolean updateExp(String guild_id, String user_id, int exp, int messages){
        String  query = "UPDATE exp_table SET exp = " + exp + ", messages = " + messages + " WHERE user_id = '" + user_id + "' AND guild_id = '" + guild_id + "';";
        return runQuery(query);
    }

    public static QueryResult getUsersByExp(String guild_id, int limit) {
        return safJQuery("SELECT user_id, messages, level, exp from exp_table WHERE guild_id = '" + guild_id + "' order by exp DESC limit " + limit + ";");
    }

    public static QueryResult getLolAccounts(String user_id) {
        return safJQuery("SELECT summoner_id FROM lol_user WHERE user_id = '" + user_id + "';");
    }

    public static ResultRow getUserExp(String user_id, String guild_id) {
        return fetchJRow("select exp, level, messages from exp_table where user_id ='" + user_id + "' and guild_id = '" + guild_id + "';");
    }

    public static ResultRow getAlert(String guild_id) {
        return fetchJRow("SELECT * FROM alert WHERE guild_id = '" + guild_id + "'");
    }

    public static boolean setPrefix(String guild_id, String bot_id, String prefix) {
        return runQuery("INSERT INTO guild_settings(guild_id, bot_id, prefix)" + "VALUES('" + guild_id + "','" + bot_id + "','" + prefix +"') ON DUPLICATE KEY UPDATE prefix = '" + prefix + "';");
    }

    public static boolean updatePrefix(String guild_id, String bot_id, String prefix) {
        return runQuery("UPDATE guild_settings SET prefix = '" + prefix + "' WHERE guild_id = '" + guild_id + "' AND bot_id = '" + bot_id + "';");
    }

    public static boolean setGreet(String user_id, String guild_id, String bot_id, String sound_id) {
        return runQuery("INSERT INTO greeting (user_id, guild_id, bot_id, sound_id) VALUES ('" + user_id + "', '" + guild_id + "', '" + bot_id + "', '" + sound_id + "') ON DUPLICATE KEY UPDATE sound_id = '" + sound_id + "';");
    }

    public static boolean deleteGreet(String user_id, String guild_id, String bot_id) {
        return runQuery("DELETE from greeting WHERE guild_id = '" + guild_id + "' AND user_id = '" + user_id + "' AND bot_id = '" + bot_id + "';");
    }
    













    /** 
     * What the actual fuck sunyx?
     * eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee la cannuccia
     * weru9fgt9uehrgferwfghreyuio
    */
    public static String getCannuccia(){
        return ":cannuccia:";
    }
}