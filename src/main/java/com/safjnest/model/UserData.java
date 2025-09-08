package com.safjnest.model;

import java.util.HashMap;
import java.util.LinkedHashMap;

import com.safjnest.core.Bot;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.sql.database.LeagueDB;
import com.safjnest.spring.api.service.lol.LeagueService;
import com.safjnest.spring.util.SpringContextHolder;
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
        
        QueryResult result = BotDB.getAliases(USER_ID);
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
        try {
            LeagueService leagueService = SpringContextHolder.getBean(LeagueService.class);
            var summoners = leagueService.getLOLAccountsByUserId(USER_ID);
            
            this.riotAccounts = new LinkedHashMap<>();
            for(var summoner : summoners) {
                riotAccounts.put(summoner.getPuuid(), String.valueOf(summoner.getLeagueShard()));
            }
        } catch (Exception e) {
            // Fallback to old implementation
            QueryResult result = LeagueDB.getLOLAccountsByUserId(USER_ID);
            if (result == null) { return; }

            this.riotAccounts = new LinkedHashMap<>();
            for(QueryRecord row: result){
                riotAccounts.put(row.get("puuid"), row.get("league_shard"));
            }
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
        boolean result = false;
        try {
            LeagueService leagueService = SpringContextHolder.getBean(LeagueService.class);
            Integer id = leagueService.addLOLAccount(USER_ID, null, s.getSummonerId(), s.getAccountId(), s.getPUUID(), s.getPlatform().getValue());
            result = id > 0;
        } catch (Exception e) {
            // Fallback to old implementation
            result = LeagueDB.addLOLAccount(USER_ID, s) > 0;
        }
        if (result) riotAccounts.put(s.getPUUID(), String.valueOf(s.getPlatform().ordinal()));
        
        return result;
    }

    public boolean deleteRiotAccount(String puuid) {
        checkRiotAccounts();
        boolean result = false;
        try {
            LeagueService leagueService = SpringContextHolder.getBean(LeagueService.class);
            result = leagueService.deleteLOLAccount(USER_ID, puuid);
        } catch (Exception e) {
            // Fallback to old implementation
            result = LeagueDB.deleteLOLaccount(USER_ID, puuid);
        }
        if (result) riotAccounts.remove(puuid);
        
        return result;
    }

}