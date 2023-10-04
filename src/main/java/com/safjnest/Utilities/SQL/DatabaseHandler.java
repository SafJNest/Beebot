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





    public static QueryResult getGuildsData(String filter){        
        String query = "SELECT guild_id, prefix, exp_enabled, threshold, blacklist_channel FROM guild_settings WHERE " + filter + ";";
        return safJQuery(query);
    }

    public static QueryResult getRoomsData(String filter){        
        String query = "SELECT guild_id, room_id, room_name, has_exp, exp_value, has_command_stats FROM rooms_settings WHERE " + filter + ";";
        return safJQuery(query);
    }

    public static QueryResult getSounds(String filter){
        String query = "SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE " + filter + ";";
        System.out.println(query);
        return safJQuery(query);
    }

    public static QueryResult getSounds(String filter, String custom){
        String query = "SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE " + filter + " " + custom + ";";
        System.out.println(query);
        return safJQuery(query);
    }

    public static QueryResult getlistGuildSounds(String guild_id) {
        return getSounds("where guild_id = '" + guild_id + "'", "ORDER BY name ASC LIMIT 24");
    }

    public static QueryResult getlistUserSounds(String user_id) {
        return getSounds("WHERE user_id = '" + user_id + "' ", "ORDER BY name ASC LIMIT 24");
    }

    public static QueryResult getlistUserSounds(String user_id, String guild_id) {
        return getSounds("where user_id = '" + user_id + "' AND (guild_id = '" + guild_id + "'  OR public = 1)", "ORDER BY name ASC LIMIT 24");
    }

    public static QueryResult getSoundsfromId(String id, String guild_id, String author_id) {
        return getSounds("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE id = '" + id + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
    }

    public static QueryResult getSoundsfromName(String name, String guild_id, String author_id) {
        return getSounds("SELECT id, name, guild_id, user_id, extension, public FROM sound WHERE name = '" + name + "' AND  (guild_id = '" + guild_id + "'  OR public = 1 OR user_id = '" + author_id + "')");
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