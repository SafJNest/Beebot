package com.safjnest.lol.service;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.safjnest.lol.model.SummonerDTO;
import com.safjnest.mongo.LeagueMongo;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.QueryResult;
import com.safjnest.sql.database.LeagueDB;

import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.api.regions.RegionShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery;
import no.stelar7.api.r4j.pojo.lol.league.LeagueEntry;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLTimeline;
import no.stelar7.api.r4j.pojo.lol.spectator.SpectatorGameInfo;
import no.stelar7.api.r4j.pojo.lol.summoner.Summoner;
import no.stelar7.api.r4j.pojo.shared.RiotAccount;

public final class LeagueService {

    private record SummonerAutocompleteChoice(String riotId, String puuid) {}

    private static final int TTL_SUMMONER_DTO = 600;
    private static final int TTL_DB_LOOKUP = 0;
    private static final int TTL_SUMMONER_AUTOCOMPLETE = 86_400;

    private static final TypeReference<List<SummonerAutocompleteChoice>> SUMMONER_AUTOCOMPLETE_TYPE =
        new TypeReference<>() {};

    private LeagueService() {}

    public static SummonerDTO getSummonerByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_DTO.of(shard.name(), puuid);
        SummonerDTO cached = RedisClient.getSerializable(key, SummonerDTO.class);
        if (cached != null) return cached;

        try {
            cached = LeagueMongo.getSummoner(puuid, shard);
            if (cached != null) {
                cacheSummoner(cached);
                return cached;
            }
        } catch (RuntimeException ignored) {}

        Summoner summoner = LeagueR4J.getSummonerByPuuid(puuid, shard);
        if (summoner == null) return null;

        RiotAccount account = LeagueR4J.getAccountByPuuid(puuid, shard);
        List<LeagueEntry> ranks = LeagueR4J.getLeagueEntries(puuid, shard);
        SummonerDTO result = SummonerDTO.from(summoner, account, ranks);

        try {
            LeagueMongo.saveSummoner(result);
        } catch (RuntimeException ignored) {}
        cacheSummoner(result);
        return result;
    }

    public static Summoner getR4JSummonerByPuuid(String puuid, LeagueShard shard) {
        return LeagueR4J.getSummonerByPuuid(puuid, shard);
    }

    public static Summoner getSummonerByName(String name, String tag, LeagueShard shard) {
        RiotAccount account = getRiotAccountByName(name, tag, shard);
        return account != null ? getR4JSummonerByPuuid(account.getPUUID(), shard) : null;
    }

    public static int getSummonerIdByPuuid(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_ID.of(shard.name(), puuid);
        Integer id = RedisClient.get(key, Integer.class);
        if (id != null) return id;

        id = LeagueDB.getSummonerIdByPuuid(puuid, shard);
        if (id != 0) RedisClient.set(key, id, TTL_DB_LOOKUP);
        return id;
    }

    public static String getUserIdByLOLAccountId(String puuid, LeagueShard shard) {
        String key = RedisKey.USER_ID_BY_PUUID.of(shard.name(), puuid);
        String userId = RedisClient.get(key, String.class);
        if (userId != null) return userId;

        userId = LeagueDB.getUserIdByLOLAccountId(puuid, shard);
        if (userId != null) RedisClient.set(key, userId, TTL_DB_LOOKUP);
        return userId;
    }

    public static RiotAccount getRiotAccountByPuuid(String puuid, LeagueShard shard) {
        return LeagueR4J.getAccountByPuuid(puuid, shard);
    }

    public static RiotAccount getRiotAccountByName(String name, String tag, LeagueShard shard) {
        return LeagueR4J.getAccountByName(name, tag, shard);
    }

    public static RiotAccount getRiotAccountFromSummoner(Summoner summoner) {
        return getRiotAccountByPuuid(summoner.getPUUID(), summoner.getPlatform());
    }

    public static List<LeagueEntry> getLeagueEntries(String puuid, LeagueShard shard) {
        return LeagueR4J.getLeagueEntries(puuid, shard);
    }

    public static LeagueEntry getLeagueEntry(
        String puuid,
        LeagueShard shard,
        String queueCommonName
    ) {
        for (LeagueEntry entry : getLeagueEntries(puuid, shard)) {
            if (entry.getQueueType().commonName().equals(queueCommonName)) return entry;
        }
        return null;
    }

    public static List<ChampionMastery> getChampionMasteries(String puuid, LeagueShard shard) {
        return LeagueR4J.getMasteries(puuid, shard);
    }

    public static ChampionMastery getChampionMastery(
        String puuid,
        LeagueShard shard,
        int championId
    ) {
        return LeagueR4J.getMastery(puuid, shard, championId);
    }

    public static SpectatorGameInfo getSpectatorGame(String puuid, LeagueShard shard) {
        return LeagueR4J.getSpectatorGame(puuid, shard);
    }

    public static List<String> getMatchList(
        Summoner summoner,
        GameQueueType queue,
        int index
    ) {
        List<String> matches = summoner.getLeagueGames()
            .withQueue(queue)
            .withBeginIndex(index)
            .get();
        return matches != null ? matches : new ArrayList<>();
    }

    public static LOLMatch getMatch(String gameId, LeagueShard shard) {
        return LeagueR4J.getMatch(gameId, shard);
    }

    public static LOLMatch getMatch(String gameId, RegionShard region) {
        return LeagueR4J.getMatch(gameId, region);
    }

    public static LOLTimeline getTimeline(String gameId, LeagueShard shard) {
        return LeagueR4J.getTimeline(gameId, shard);
    }

    public static LOLTimeline getTimeline(String gameId, RegionShard region) {
        return LeagueR4J.getTimeline(gameId, region);
    }

    public static String putMatch(LOLMatch match) {
        return match.getPlatform().name() + "_" + match.getGameId();
    }

    public static List<LeagueEntry> getLeagueByTierDivision(
        LeagueShard shard,
        GameQueueType queue,
        TierDivisionType tier,
        int page
    ) {
        return LeagueR4J.getLeagueByTierDivision(shard, queue, tier, page);
    }

    public static void putLeagueEntry(LeagueShard shard, LeagueEntry entry) {
        invalidateSummonerDTO(entry.getPuuid(), shard);
    }

    public static void puWeaktLeagueEntry(LeagueShard shard, LeagueEntry entry) {
        putLeagueEntry(shard, entry);
    }

    public static void invalidateSummonerDTO(String puuid, LeagueShard shard) {
        RedisClient.delete(RedisKey.SUMMONER_DTO.of(shard.name(), puuid));
        try {
            LeagueMongo.deleteSummoner(puuid, shard);
        } catch (RuntimeException ignored) {}
    }

    public static QueryResult getAdvancedLOLData(
        int summonerId,
        long timeStart,
        long timeEnd,
        GameQueueType queue
    ) {
        String key = RedisKey.ADVANCED_LOL_DATA.of(
            summonerId,
            timeStart,
            timeEnd,
            queue != null ? queue.name() : "null"
        );
        QueryResult cached = RedisClient.get(key, QueryResult.class);
        if (cached != null) return cached;

        QueryResult result = LeagueDB.getAdvancedLOLData(summonerId, timeStart, timeEnd, queue);
        if (result != null) RedisClient.set(key, result, TTL_DB_LOOKUP);
        return result;
    }

    public static QueryResult getSummonerData(String puuid, LeagueShard shard) {
        String key = RedisKey.SUMMONER_DATA.of(puuid, shard.name());
        QueryResult cached = RedisClient.get(key, QueryResult.class);
        if (cached != null) return cached;

        QueryResult result = LeagueDB.getSummonerData(puuid, shard);
        if (result != null) RedisClient.set(key, result, TTL_DB_LOOKUP);
        return result;
    }

    public static List<Choice> getSummonerAutocomplete(String query, LeagueShard shard) {
        if (query == null || query.isBlank()) return new ArrayList<>();

        String normalizedQuery = query.trim().toLowerCase();
        String key = RedisKey.SUMMONER_AUTOCOMPLETE.of(shard.name(), normalizedQuery);
        List<SummonerAutocompleteChoice> cached = RedisClient.get(
            key,
            SUMMONER_AUTOCOMPLETE_TYPE
        );
        if (cached != null) return toChoices(cached);

        List<SummonerAutocompleteChoice> autocompleteChoices = new ArrayList<>();
        QueryResult summoners = LeagueDB.getFocusedSummoners(normalizedQuery, shard);
        for (QueryRecord summoner : summoners) {
            autocompleteChoices.add(new SummonerAutocompleteChoice(
                summoner.get("riot_id"),
                summoner.get("puuid")
            ));
        }

        RedisClient.set(key, autocompleteChoices, TTL_SUMMONER_AUTOCOMPLETE);
        return toChoices(autocompleteChoices);
    }

    private static void cacheSummoner(SummonerDTO summoner) {
        RedisClient.setSerializable(
            RedisKey.SUMMONER_DTO.of(summoner.getRegion().name(), summoner.getPuuid()),
            summoner,
            TTL_SUMMONER_DTO
        );
    }

    private static List<Choice> toChoices(
        List<SummonerAutocompleteChoice> autocompleteChoices
    ) {
        List<Choice> choices = new ArrayList<>();
        for (SummonerAutocompleteChoice choice : autocompleteChoices) {
            choices.add(new Choice(choice.riotId(), choice.puuid()));
        }
        return choices;
    }
}
