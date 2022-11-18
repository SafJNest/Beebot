package com.safjnest.Utilities;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;

public class LOLHandler {
    
    private static R4J riotApi;
    private static String dataDragonVersion = "22.1";

    public LOLHandler(R4J riotApi){
        LOLHandler.riotApi = riotApi;
    }

    public static Summoner getSummonerFromDB(String accountId, String discordId){
        String query = "SELECT account_id FROM lol_user WHERE discord_id = '" + discordId + "';";
        try { 
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByAccount(LeagueShard.EUW1, DatabaseHandler.getSql().getString(query, "account_id")); 
        } catch (Exception e) { return null; }
    }

    public static Summoner getSummonerByName(String nameAccount){
        try {
            return riotApi.getLoLAPI().getSummonerAPI().getSummonerByName(LeagueShard.EUW1, nameAccount);
        } catch (Exception e) { return null; }
    }

      
      
}
