package com.safjnest.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.core.Bot;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.nosql.MongoDB;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.utils.log.BotLogger;
import com.safjnest.utils.log.LoggerIDpair;

import net.dv8tion.jda.api.entities.User;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class UserData {
    
    private final String USER_ID;
    private HashMap<String, AliasData> aliases;
    private LinkedHashMap<String, Summoner> riotAccounts;

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


//     ▄████████  ▄█        ▄████████    ▄████████ 
//    ███    ███ ███       ███    ███   ███    ███ 
//    ███    ███ ███       ███▌   ███    ███   ███    █▀  
//    ███    ███ ███       ███▌   ███    ███   ███        
//  ▀███████████ ███       ███▌ ▀███████████ ▀███████████ 
//    ███    ███ ███       ███    ███    ███          ███ 
//    ███    ███ ███▌    ▄ ███    ███    ███    ▄█    ███ 
//    ███    █▀  █████▄▄██ █▀     ███    █▀   ▄████████▀  
//               ▀                                        

    private void retriveAlies() {
        this.aliases = new HashMap<>();
        
        List<QueryRecord> result = BotDB.getAliases(USER_ID);
        if (result == null) { return; }

        for(QueryRecord row: result){
            AliasData alias = new AliasData(row.getAsInt("ID"), row.get("name"), row.get("command"));
            aliases.put(row.get("name"), alias);
        }

    }

    public boolean addAlias(String name, String command) {
        int id = BotDB.createAlias(USER_ID, name, command);
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
        return BotDB.deleteAlias(toDelete);
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
        QueryRecord possibleGreet = BotDB.getSpecificGuildGreet(USER_ID, guildId);

        if (possibleGreet == null || possibleGreet.emptyValues()) {
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
        
        QueryRecord possibleGreet = BotDB.getSpecificGuildGreet(USER_ID, guildId);
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
            QueryRecord possibleGreet = BotDB.getGlobalGreet(USER_ID);
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
        return BotDB.setGreet(this.USER_ID, guildId, soundId);
    }

    public boolean unsetGreet(String guildId) {
        if (guildId.equals("0")) {
            globalGreetId = null;
        } else {
            guildGreetIds.remove(guildId);
        }
        return BotDB.deleteGreet(this.USER_ID, guildId);
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
        this.riotAccounts = new LinkedHashMap<>();
        for (QueryRecord row : MongoDB.findAccountsByUserId(USER_ID)) {
            Summoner summoner = MongoDB.read(row, Summoner.class);
            if (summoner != null && summoner.puuid() != null) riotAccounts.put(summoner.puuid(), summoner);
        }
    }

    private void checkRiotAccounts() {
        if (riotAccounts == null) retriveRiotAccounts();
    }


    public Map<String, Summoner> getRiotAccounts() {
        checkRiotAccounts();
        return riotAccounts;
    }

    public boolean addRiotAccount(Summoner s) {
        checkRiotAccounts();
        boolean result = SummonerService.upsert(s, USER_ID);
        if (result) {
            riotAccounts.put(s.puuid(), s);
            SummonerService.invalidate(s.puuid(), s.region());
        }
        
        return result;
    }

    public boolean deleteRiotAccount(String puuid) {
        checkRiotAccounts();
        Summoner summoner = riotAccounts.get(puuid);
        LeagueShard region = summoner == null ? null : summoner.region();
        boolean result = MongoDB.detachSummonerUser(puuid, USER_ID);
        if (result) {
            riotAccounts.remove(puuid);
            if (region != null) SummonerService.invalidate(puuid, region);
        }
        
        return result;
    }

}
