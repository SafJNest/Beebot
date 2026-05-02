package com.safjnest.lol.service;

import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.database.LeagueDB;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class LeagueService {

    static {
      riotApi = LeagueHandler.getRiotApi();
    }

    private static final int TTL_SUMMONER = 3600;
    private static final int TTL_ACCOUNT = 3600;

    private static R4J riotApi;

    public static Summoner getSummonerByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER.of(shard.name(), puuid);
        Summoner summoner = RedisClient.get(key, Summoner.class);
        if (summoner != null) return summoner;

        try { summoner = riotApi.getLoLAPI().getSummonerAPI().getSummonerByPUUID(shard, puuid); } 
        catch (Exception e) { return null; }
        if (summoner != null) RedisClient.set(key, summoner, TTL_SUMMONER);
        return summoner;
    }

    public static int getSummonerIdByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_ID.of(shard.name(), puuid);
        Integer id = RedisClient.get(key, Integer.class);
        if (id != null) return id;

        id = LeagueDB.getSummonerIdByPuuid(puuid, shard);
        if (id != 0) RedisClient.set(key, id, TTL_SUMMONER);
        return id;
    }

    public static RiotAccount getRiotAccountByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.ACCOUNT.of(shard.name(), puuid);
        RiotAccount account = RedisClient.get(key, RiotAccount.class);
        if (account != null) return account;

        try { account = riotApi.getAccountAPI().getAccountByPUUID(LeagueShardUtils.getAccountRegion(shard), puuid); } 
        catch (Exception e) { return null; }

        if (account != null) RedisClient.set(key, account, TTL_ACCOUNT);
        return account;
    }

    public static RiotAccount getRiotAccountByName(String name, String tag, LeagueShard shard) {
        String key = RedisKey.ACCOUNT_BY_NAME.of(shard.name(), name, tag);
        RiotAccount account = RedisClient.get(key, RiotAccount.class);
        if (account != null) return account;

        try { account = riotApi.getAccountAPI().getAccountByTag(LeagueShardUtils.getAccountRegion(shard), name, tag); } 
        catch (Exception e) { return null; }

        if (account != null) RedisClient.set(key, account, TTL_ACCOUNT);
        return account;
    }

    public static RiotAccount getRiotAccountFromSummoner(Summoner s) {
        return getRiotAccountByPuuid(s.getPUUID(), s.getPlatform());
    }

    public static Summoner getSummonerByName(String name, String tag, LeagueShard shard) {
        RiotAccount account = getRiotAccountByName(name, tag, shard);
        return account != null 
            ? getSummonerByPuuid(account.getPUUID(), shard) 
            : null;
    }

    public static void invalidateSummoner(String puuid, LeagueShard shard) {
        RedisClient.delete(RedisKey.SUMMONER.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.ACCOUNT.of(shard.name(), puuid));
    }

}