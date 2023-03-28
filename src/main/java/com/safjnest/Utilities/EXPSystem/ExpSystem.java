package com.safjnest.Utilities.EXPSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import com.safjnest.Utilities.DatabaseHandler;

public class ExpSystem {
    private HashMap<String, UserTime> users;

    public ExpSystem() {
        users = new HashMap<>();
    }

    public synchronized int receiveMessage(String userId, String guildId) {
        if(!users.containsKey(userId+"-"+guildId)){
            users.put(userId+"-"+guildId, new UserTime());
            addExp(userId, guildId);
            return -1;
        }
        UserTime user =  users.get(userId+"-"+guildId);
        if (user.canReceiveExperience()) {
           return addExp(userId, guildId);
           
        }else{
           return -1;
        }
        
    }

    public int calculateExp(){
        return new Random().nextInt((25 - 15) + 1) + 15;
    }

    public int addExp(String userId, String guildId){
        int exp, lvl, msg;
        String query = "select exp, level, messages from exp_table where user_id ='"+userId+"' and guild_id = '"+guildId+"';";
        ArrayList<String> arr = DatabaseHandler.getSql().getSpecifiedRow(query, 0);
        if(arr == null){
            query = "INSERT INTO exp_table (user_id, guild_id, exp, level, messages) VALUES ('"+userId+"','"+guildId+"',"+0+","+1+","+0+");";
            DatabaseHandler.getSql().runQuery(query);
            exp = calculateExp();
            lvl = 1;
            msg = 1;
        }else{
            exp = Integer.valueOf(arr.get(0)) + calculateExp();
            lvl = Integer.valueOf(arr.get(1));
            msg = Integer.valueOf(arr.get(2)) + 1;
        }
        int expNeeded = (int) ((5.0/6.0) * (lvl+1) * (2 * (lvl+1) * (lvl+1) + 27 * (lvl+1) + 91) - exp);
        if(expNeeded <= 0){
            query = "update exp_table set exp = " + exp + ", level = " + (lvl+1) + ", messages = "+msg+" where user_id ='"+userId+"' and guild_id = '"+guildId+"';";
            DatabaseHandler.getSql().runQuery(query);
            return lvl+1;
        }else{
            query = "update exp_table set exp = " + exp + ", messages = "+msg+" where user_id ='"+userId+"' and guild_id = '"+guildId+"';";
            DatabaseHandler.getSql().runQuery(query);
            return -1;
        }

    }   
}