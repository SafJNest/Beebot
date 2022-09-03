package com.safjnest.Utilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

    public void AddLolUSer(String discId, String PUUID, String accountId, String sumId, String sumName){
        Statement stmt;
        try {
            String query = "INSERT INTO LOL_user(discord_id, summoner_id, account_id, sum_name)"
                    + "VALUES('"+discId+"','"+sumId+"','"+accountId+"','"+sumName+"');";
            stmt = c.createStatement();
            stmt.executeQuery(query);
            stmt.close();
        } catch (SQLException e) {e.printStackTrace();}
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
}
