package com.safjnest.Utilities;

import java.util.HashMap;

import com.safjnest.Utilities.SQL.DatabaseHandler;
import com.safjnest.Utilities.SQL.QueryResult;
import com.safjnest.Utilities.SQL.ResultRow;

public class UserData {
    
    private final String USER_ID;
    private HashMap<String, AliasData> aliases;
    private HashMap<String, String> riotAccounts;

    public UserData(String USER_ID) {
        this.USER_ID = USER_ID;
        retriveAlies();
        retriveRiotAccounts();
    }

    private void retriveAlies() {
        this.aliases = new HashMap<>();
        QueryResult result = DatabaseHandler.getAliases(USER_ID);
        
        if (result == null) { return; }
        System.out.println("[CACHE] Retriving Aliases from database => " + USER_ID);
        for(ResultRow row: result){
            AliasData alias = new AliasData(row.getAsInt("ID"), row.get("name"), row.get("command"));
            aliases.put(row.get("name"), alias);
        }

    }

    private void retriveRiotAccounts() {
        QueryResult result = DatabaseHandler.getLOLAccountsByUserId(USER_ID);
        if (result == null) { return; }
        System.out.println("[CACHE] Retriving Riot Accounts from database => " + USER_ID);
        this.riotAccounts = new HashMap<>();
        for(ResultRow row: result){
            riotAccounts.put(row.get("account"), row.get("account"));
        }
    }

    /* -------------------------------------------------------------------------- */
    /*                                    AliasData                               */
    /* -------------------------------------------------------------------------- */

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

}