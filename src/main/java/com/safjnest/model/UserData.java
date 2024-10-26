package com.safjnest.model;

import java.util.HashMap;
import java.util.LinkedHashMap;

import com.safjnest.core.Bot;
import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.ResultRow;
import com.safjnest.util.log.BotLogger;
import com.safjnest.util.log.LoggerIDpair;

import net.dv8tion.jda.api.entities.User;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

public class UserData {
    
    private final String USER_ID;
    private HashMap<String, AliasData> aliases;
    private LinkedHashMap<String, String> riotAccounts;

    private String globalGreetId;
    private HashMap<String, String> guildGreetIds;

    public UserData(String USER_ID) {
        this.USER_ID = USER_ID;
        
        BotLogger.debug("Retriving UserData => {0}", new LoggerIDpair(USER_ID, LoggerIDpair.IDType.USER));
        
        retriveAlies();

        globalGreetId = null;
        guildGreetIds = new HashMap<>();
    }


    /* -------------------------------------------------------------------------- */

    public String getName() {
        User possibleUser = Bot.getJDA().getUserById(USER_ID) != null ? Bot.getJDA().getUserById(USER_ID) : Bot.getJDA().retrieveUserById(USER_ID).complete();
        return possibleUser != null ? possibleUser.getName() : "Unknown";
    }

    @Override
    public String toString() {
        return "UserData {USER_ID=" + USER_ID + ", aliases=" + aliases.toString() + ", riotAccounts=" + riotAccounts.toString()
                + ", globalGreetId=" + globalGreetId + ", guildGreetIds=" + guildGreetIds.toString() + "}";
    }

    public String getId() {
        return USER_ID;
    }


//     ▄████████  ▄█        ▄█     ▄████████    ▄████████ 
//    ███    ███ ███       ███    ███    ███   ███    ███ 
//    ███    ███ ███       ███▌   ███    ███   ███    █▀  
//    ███    ███ ███       ███▌   ███    ███   ███        
//  ▀███████████ ███       ███▌ ▀███████████ ▀███████████ 
//    ███    ███ ███       ███    ███    ███          ███ 
//    ███    ███ ███▌    ▄ ███    ███    ███    ▄█    ███ 
//    ███    █▀  █████▄▄██ █▀     ███    █▀   ▄████████▀  
//               ▀                                        

    private void retriveAlies() {
        this.aliases = new HashMap<>();
        
        QueryResult result = DatabaseHandler.getAliases(USER_ID);
        if (result == null) { return; }

        for(ResultRow row: result){
            AliasData alias = new AliasData(row.getAsInt("ID"), row.get("name"), row.get("command"));
            aliases.put(row.get("name"), alias);
        }

    }

    public boolean addAlias(String name, String command) {
        int id = DatabaseHandler.createAlias(USER_ID, name, command);
        if (id == 0) {
            return false;
        }
        AliasData alias = new AliasData(id, name, command);
        aliases.put(name, alias);
        return true;
    }

    public HashMap<String, AliasData> getAliases() {
        return aliases;
    }

    public boolean deleteAlias(String toDelete) {
        getAliases().remove(toDelete);
        return DatabaseHandler.deleteAlias(toDelete);
    }


//     ▄██████▄     ▄████████    ▄████████    ▄████████     ███     
//    ███    ███   ███    ███   ███    ███   ███    ███ ▀█████████▄ 
//    ███    █▀    ███    ███   ███    █▀    ███    █▀     ▀███▀▀██ 
//   ▄███         ▄███▄▄▄▄██▀  ▄███▄▄▄      ▄███▄▄▄         ███   ▀ 
//  ▀▀███ ████▄  ▀▀███▀▀▀▀▀   ▀▀███▀▀▀     ▀▀███▀▀▀         ███     
//    ███    ███ ▀███████████   ███    █▄    ███    █▄      ███     
//    ███    ███   ███    ███   ███    ███   ███    ███     ███     
//    ████████▀    ███    ███   ██████████   ██████████    ▄████▀   
//                 ███    ███                                       

    public String getGreet(String guildId) {
        if (guildGreetIds.containsKey(guildId)) {
            return guildGreetIds.get(guildId).isEmpty() ? getGlobalGreet() : guildGreetIds.get(guildId);
        }
        ResultRow possibleGreet = DatabaseHandler.getSpecificGuildGreet(USER_ID, guildId);

        if (possibleGreet.emptyValues()) {
            guildGreetIds.put(guildId, "");
            return getGlobalGreet();
        }

        String guildGreet = possibleGreet.get("id");
        guildGreetIds.put(guildId, guildGreet);

        return guildGreet;
    }

    public String getGuildGreet(String guildId) {
        if (guildGreetIds.containsKey(guildId)) 
            return guildGreetIds.get(guildId);
        
        ResultRow possibleGreet = DatabaseHandler.getSpecificGuildGreet(USER_ID, guildId);
        if (possibleGreet.emptyValues()) {
            guildGreetIds.put(guildId, "");
            return null;
        }

        String guildGreet = possibleGreet.get("id");
        guildGreetIds.put(guildId, guildGreet);

        return guildGreet;
        
    }

    public String getGlobalGreet() {
        if (globalGreetId == null) {
            ResultRow possibleGreet = DatabaseHandler.getGlobalGreet(USER_ID);
            if (possibleGreet.emptyValues()) {
                this.globalGreetId = "";
                return null;
            }
            globalGreetId = possibleGreet.get("id");
        }
        else if (globalGreetId.isEmpty()) return null;
        
        return globalGreetId;
    }

    public boolean setGreet(String guildId, String soundId) {
        if (guildId.equals("0")) globalGreetId = soundId;
        else guildGreetIds.put(guildId, soundId);
        return DatabaseHandler.setGreet(this.USER_ID, guildId, soundId);
    }

    public boolean unsetGreet(String guildId) {
        if (guildId.equals("0")) {
            globalGreetId = null;
        } else {
            guildGreetIds.remove(guildId);
        }
        return DatabaseHandler.deleteGreet(this.USER_ID, guildId);
    }

    public HashMap<String, String> getGreets() {
        return guildGreetIds != null ? guildGreetIds : new HashMap<>();
    }


//   ▄█          ▄████████    ▄████████    ▄██████▄  ███    █▄     ▄████████ 
//  ███         ███    ███   ███    ███   ███    ███ ███    ███   ███    ███ 
//  ███         ███    █▀    ███    ███   ███    █▀  ███    ███   ███    █▀  
//  ███        ▄███▄▄▄       ███    ███  ▄███        ███    ███  ▄███▄▄▄     
//  ███       ▀▀███▀▀▀     ▀███████████ ▀▀███ ████▄  ███    ███ ▀▀███▀▀▀     
//  ███         ███    █▄    ███    ███   ███    ███ ███    ███   ███    █▄  
//  ███▌    ▄   ███    ███   ███    ███   ███    ███ ███    ███   ███    ███ 
//  █████▄▄██   ██████████   ███    █▀    ████████▀  ████████▀    ██████████ 
//  ▀                                                                        

    private void retriveRiotAccounts() {
        QueryResult result = DatabaseHandler.getLOLAccountsByUserId(USER_ID);
        if (result == null) { return; }

        this.riotAccounts = new LinkedHashMap<>();
        for(ResultRow row: result){
            riotAccounts.put(row.get("account_id"), row.get("league_shard"));
        }
    }

    private void checkRiotAccounts() {
        if (riotAccounts == null) retriveRiotAccounts();
    }


    public HashMap<String, String> getRiotAccounts() {
        checkRiotAccounts();
        return riotAccounts;
    }

    public boolean addRiotAccount(Summoner s) {
        checkRiotAccounts();
        boolean result = DatabaseHandler.addLOLAccount(USER_ID, s);
        if (result) riotAccounts.put(s.getAccountId(), String.valueOf(s.getPlatform().ordinal()));
        
        return result;
    }

    public boolean deleteRiotAccount(String account_id) {
        checkRiotAccounts();
        boolean result = DatabaseHandler.deleteLOLaccount(USER_ID, account_id);
        if (result) riotAccounts.remove(account_id);
        
        return result;
    }

}