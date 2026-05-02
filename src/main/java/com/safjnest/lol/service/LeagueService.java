package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.LeagueHandler;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public class LeagueService {

    static {
      riotApi = LeagueHandler.getRiotApi();
    }

    private static final int TTL_SUMMONER = 0;
    private static final int TTL_ACCOUNT = 0;
    private static final int TTL_LEAGUE_ENTRIES = 7200;
    private static final int TTL_CHAMPION_MASTERIES = 43200;
    private static final int TTL_SPECTATOR = 600;
    private static final int TTL_ADVANCED_LOL_DATA = 0;

    private static final TypeReference<List<LeagueEntry>> LEAGUE_ENTRIES_TYPE =
        new TypeReference<List<LeagueEntry>>() {};
    private static final TypeReference<List<ChampionMastery>> CHAMPION_MASTERIES_TYPE =
        new TypeReference<List<ChampionMastery>>() {};

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

    public static String getUserIdByLOLAccountId(String puuid, LeagueShard shard) {
        String key = RedisKey.USER_ID_BY_PUUID.of(shard.name(), puuid);
        String userId = RedisClient.get(key, String.class);
        if (userId != null) return userId;

        userId = LeagueDB.getUserIdByLOLAccountId(puuid, shard);
        if (userId != null) RedisClient.set(key, userId, TTL_SUMMONER);
        return userId;
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
        RedisClient.delete(RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid));
        RedisClient.delete(RedisKey.SPECTATOR_CURRENT.of(shard.name(), puuid));
    }

    public static List<LeagueEntry> getLeagueEntries(String puuid, LeagueShard shard) {
        String key = RedisKey.LEAGUE_ENTRIES.of(shard.name(), puuid);
        List<LeagueEntry> cached = RedisClient.get(key, LEAGUE_ENTRIES_TYPE);
        if (cached != null) {
            return cached;
        }
        try {
            List<LeagueEntry> entries = riotApi.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(shard, puuid);
            if (entries == null) {
                entries = new ArrayList<>();
            }
            RedisClient.set(key, entries, TTL_LEAGUE_ENTRIES);
            return entries;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static LeagueEntry getLeagueEntry(String puuid, LeagueShard shard, String queueCommonName) {
        for (LeagueEntry entry : getLeagueEntries(puuid, shard)) {
            if (entry.getQueueType().commonName().equals(queueCommonName)) {
                return entry;
            }
        }
        return null;
    }

    public static List<ChampionMastery> getChampionMasteries(String puuid, LeagueShard shard) {
        String key = RedisKey.CHAMPION_MASTERIES.of(shard.name(), puuid);
        List<ChampionMastery> cached = RedisClient.get(key, CHAMPION_MASTERIES_TYPE);
        if (cached != null) {
            return cached;
        }
        try {
            List<ChampionMastery> list = riotApi.getLoLAPI().getMasteryAPI().getChampionMasteries(shard, puuid);
            if (list == null) {
                list = new ArrayList<>();
            }
            RedisClient.set(key, list, TTL_CHAMPION_MASTERIES);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static SpectatorGameInfo getSpectatorGame(String puuid, LeagueShard shard) {
        String key = RedisKey.SPECTATOR_CURRENT.of(shard.name(), puuid);
        SpectatorGameInfo cached = RedisClient.get(key, SpectatorGameInfo.class);
        if (cached != null) {
            return cached;
        }
        try {
            SpectatorGameInfo game = riotApi.getLoLAPI().getSpectatorAPI().getCurrentGame(shard, puuid);
            if (game != null) {
                RedisClient.set(key, game, TTL_SPECTATOR);
            }
            return game;
        } catch (Exception e) {
            return null;
        }
    }

    public static QueryResult getAdvancedLOLData(int summonerId, long time_start, long time_end, GameQueueType queue) {
        String key = RedisKey.ADVANCED_LOL_DATA.of(summonerId, time_start, time_end, queue != null ? queue.name() : "null");
        QueryResult cached = RedisClient.get(key, QueryResult.class);
        if (cached != null) {
            return cached;
        }
        QueryResult result = LeagueDB.getAdvancedLOLData(summonerId, time_start, time_end, queue);
        if (result != null) {
            RedisClient.set(key, result, TTL_ADVANCED_LOL_DATA);
        }
        return result;
    }

}