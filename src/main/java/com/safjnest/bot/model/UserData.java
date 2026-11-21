package com.safjnest.bot.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.bot.Bot;
import com.safjnest.database.nosql.MongoDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.lol.model.summoner.Summoner;
import com.safjnest.lol.service.SummonerService;
import com.safjnest.status.log.BotLogger;
import com.safjnest.status.log.LoggerIDpair;

import net.dv8tion.jda.api.entities.User;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class UserData {
    
    private final String USER_ID;
    private final BotDB database;

    private final HashMap<String, AliasData> aliases;
    private LinkedHashMap<String, Summoner> riotAccounts;

    private String globalGreetId;
    private final HashMap<String, String> guildGreetIds;
    private boolean allGreetsLoaded;

    public UserData(String USER_ID) {
        this(USER_ID, BotDB.getAliases(USER_ID), BotDB.get());
        BotLogger.debug("Retriving UserData => {0}", new LoggerIDpair(USER_ID, LoggerIDpair.IDType.USER));
    }

    private UserData(String USER_ID, List<QueryRecord> aliasRows, BotDB database) {
        this.USER_ID = USER_ID;
        this.database = database;

        this.aliases = new HashMap<>();
        for (QueryRecord row : aliasRows) {
            aliases.put(row.get("name"), new AliasData(row.getAsInt("id"), row.get("name"), row.get("command")));
        }

        globalGreetId = null;
        guildGreetIds = new LinkedHashMap<>();
    }

    public static UserData fromRows(String userId, List<QueryRecord> aliasRows) {
        return fromRows(userId, aliasRows, BotDB.get());
    }


    public static UserData fromRows(String userId, List<QueryRecord> aliasRows, BotDB database) {
        return new UserData(userId, aliasRows, database);
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

    public synchronized boolean addAlias(String name, String command) {
        if (aliases.containsKey(name))
            return false;

        try {
            int id = database.saveUserAlias(USER_ID, name, command);
            aliases.put(name, new AliasData(id, name, command));

            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    public synchronized HashMap<String, AliasData> getAliases() {
        return new HashMap<>(aliases);
    }

    public synchronized boolean deleteAlias(String name) {
        AliasData alias = aliases.get(name);
        if (alias == null)
            return false;

        try {
            if (!database.deleteUserAlias(USER_ID, alias.getID()))
                return false;

            aliases.remove(name);

            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
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

    public synchronized String getGreet(String guildId) {
        String guildGreet = getGuildGreet(guildId);

        return guildGreet == null || guildGreet.isEmpty() ? getGlobalGreet() : guildGreet;
    }

    public synchronized String getGuildGreet(String guildId) {
        if (!guildGreetIds.containsKey(guildId)) {
            String soundId = "";

            if (!allGreetsLoaded) {
                List<QueryRecord> rows = database.readGreet(USER_ID, guildId);
                if (!rows.isEmpty())
                    soundId = rows.get(0).get("sound_id");
            }

            guildGreetIds.put(guildId, soundId);
        }

        String soundId = guildGreetIds.get(guildId);

        return soundId.isEmpty() ? null : soundId;
    }

    public synchronized String getGlobalGreet() {
        if (globalGreetId == null) {
            String soundId = "";

            if (!allGreetsLoaded) {
                List<QueryRecord> rows = database.readGreet(USER_ID, "0");
                if (!rows.isEmpty())
                    soundId = rows.get(0).get("sound_id");
            }

            globalGreetId = soundId;
        }

        return globalGreetId.isEmpty() ? null : globalGreetId;
    }

    public synchronized boolean setGreet(String scope, String soundId) {
        try {
            saveGreet(scope, soundId);

            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public synchronized boolean unsetGreet(String scope) {
        return setGreet(scope, null);
    }

    public synchronized void saveGreet(String scope, String soundId) {
        database.saveGreet(USER_ID, scope, soundId);

        if (scope.equals("0"))
            globalGreetId = soundId == null ? "" : soundId;
        else
            guildGreetIds.put(scope, soundId == null ? "" : soundId);
    }

    public synchronized HashMap<String, String> getGreets() {
        HashMap<String, String> result = new HashMap<>(guildGreetIds);
        result.values().removeIf(String::isEmpty);

        return result;
    }

    public synchronized Map<String, String> getAllGreets() {
        if (!allGreetsLoaded) {
            List<QueryRecord> rows = database.readGreets(USER_ID);
            HashMap<String, String> guilds = new LinkedHashMap<>();
            String global = "";

            for (QueryRecord row : rows) {
                String scope = row.get("guild_id");
                if (scope.equals("0"))
                    global = row.get("sound_id");
                else
                    guilds.put(scope, row.get("sound_id"));
            }

            globalGreetId = global;
            guildGreetIds.clear();
            guildGreetIds.putAll(guilds);
            allGreetsLoaded = true;
        }

        Map<String, String> result = new LinkedHashMap<>();
        if (globalGreetId != null && !globalGreetId.isEmpty())
            result.put("0", globalGreetId);

        for (Map.Entry<String, String> entry : guildGreetIds.entrySet()) {
            if (!entry.getValue().isEmpty())
                result.put(entry.getKey(), entry.getValue());
        }

        return java.util.Collections.unmodifiableMap(result);
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
