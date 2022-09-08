package com.safjnest.Utilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.postgresql.util.PSQLException;

public class PostgreSQL {
    Connection c;

    public PostgreSQL(String hostName, String database, String user, String password){
        try {
            Class.forName("org.postgresql.Driver");
            c = DriverManager
                .getConnection("jdbc:postgresql://"+hostName+"/"+database,user,password);
            System.out.println("[SQL] INFO Connection to the extreme db successful!");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.getClass().getName()+": "+e.getMessage());
            System.out.println("[SQL] INFO Connection to the extreme db ANNODAM!");;
        }
    }

    public boolean runQuery(String query){
        Statement stmt;
        try {
            stmt = c.createStatement();
            stmt.executeQuery(query);
            stmt.close();
            return true;
        }
        catch (PSQLException e) {return true;}
        catch (SQLException e1) {return false;}
    }


    public String getLolInfo(String id, String column){
        Statement stmt;
        try {
            String query = "SELECT "+column+" FROM LOL_user WHERE discord_id = '" + id + "';";
            stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            rs.next();
            String info = rs.getString(column);
            rs.close();
            stmt.close();
            return info;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }


    public String getWelcomeChannel(String discordId){
        Statement stmt;
        try {
            String query = "SELECT channel_id FROM welcome_message WHERE discord_id = '" + discordId + "';";
            stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            rs.next();
            String info = rs.getString("channel_id");
            rs.close();
            stmt.close();
            return info;
        } catch (SQLException e) {
            return null;
        }
    }

    public String getWelcomeMessage(String discordId){
        Statement stmt;
        try {
            String query = "SELECT message_text FROM welcome_message WHERE discord_id = '" + discordId + "';";
            stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            rs.next();
            String info = rs.getString("message_text");
            rs.close();
            stmt.close();
            return info;
        } catch (SQLException e) {
            return null;
        }
    } 
    public ArrayList<String> getWelcomeRoles(String discordId){
        Statement stmt;
        ArrayList<String> roles = new ArrayList<>();
        try {
            String query = "SELECT role_id FROM welcome_roles WHERE discord_id = '" + discordId + "';";
            stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            while(rs.next()){
                roles.add(rs.getString("role_id"));
            };
            rs.close();
            stmt.close();
            return roles;
        } catch (SQLException e) {
            return null;
        }
    }

    public String getString(String query, String nameRow){
        Statement stmt;
        try {
            stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            rs.next();
            String info = rs.getString(nameRow);
            rs.close();
            stmt.close();
            return info;
        } catch (SQLException e) {
            return null;
        }
    }

     public ArrayList<String> getListString(String query, String nameRow){
        Statement stmt;
        ArrayList<String> arr = new ArrayList<>();
        try {
            stmt = c.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            while(rs.next()){
                arr.add(rs.getString(nameRow));
            }
            rs.close();
            stmt.close();
            return arr;
        } catch (SQLException e) {
            return null;
        }
    }

}
