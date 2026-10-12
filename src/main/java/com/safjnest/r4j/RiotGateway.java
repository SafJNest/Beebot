package com.safjnest.r4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.R4B.api.dto.riot.*;
import com.R4B.api.dto.summoner.Mastery;
import com.R4B.api.dto.summoner.Summoner;
import com.R4B.lol.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.utils.SettingsLoader;
import com.safjnest.utils.JsonCodec;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.APICredentials;
import no.stelar7.api.r4j.basic.cache.impl.EmptyCacheProvider;
import no.stelar7.api.r4j.basic.calling.DataCall;
import no.stelar7.api.r4j.impl.R4J;
import no.stelar7.api.r4j.impl.lol.builders.matchv5.match.MatchListBuilder;

public final class RiotGateway {

    private static final ObjectMapper DATA = new ObjectMapper()
        .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    private RiotGateway() {}

    public static void initialize() { Runtime.api.getLoLAPI(); }

    public static Summoner summoner(LeagueShard shard, String puuid) {
        var value = Runtime.api.getLoLAPI().getSummonerAPI().getSummonerByPUUID(platform(shard), puuid);
        if (value != null) RedisClient.set(RedisKey.R4J_SUMMONER, value, shard.name(), puuid);
        return identity(value);
    }

    public static RiotAccount account(RegionShard region, String puuid) {
        return data(Runtime.api.getAccountAPI().getAccountByPUUID(region(region), puuid), RiotAccount.class);
    }

    public static RiotAccount account(RegionShard region, String name, String tag) {
        return data(Runtime.api.getAccountAPI().getAccountByTag(region(region), name, tag), RiotAccount.class);
    }

    public static SpectatorGameInfo spectator(LeagueShard shard, String puuid) {
        return data(Runtime.api.getLoLAPI().getSpectatorAPI().getCurrentGame(platform(shard), puuid), SpectatorGameInfo.class);
    }

    public static List<LeagueEntry> ranks(LeagueShard shard, String puuid) {
        return DATA.convertValue(Runtime.api.getLoLAPI().getLeagueAPI().getLeagueEntriesByPUUID(platform(shard), puuid),
            new TypeReference<List<LeagueEntry>>() {});
    }

    public static List<LeagueEntry> ranks(LeagueShard shard, GameQueueType queue, TierDivisionType tier, int page) {
        return DATA.convertValue(Runtime.api.getLoLAPI().getLeagueAPI().getLeagueByTierDivision(platform(shard),
            no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType.valueOf(queue.name()),
            no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType.valueOf(tier.name()), page),
            new TypeReference<List<LeagueEntry>>() {});
    }

    public static List<Mastery> masteries(LeagueShard shard, String puuid) {
        var entries = Runtime.api.getLoLAPI().getMasteryAPI().getChampionMasteries(platform(shard), puuid);
        if (entries != null) RedisClient.set(RedisKey.R4J_CHAMPION_MASTERIES, entries, shard.name(), puuid);
        return masteryValues(entries);
    }

    public static RiotMatchData match(RegionShard region, String gameId) {
        return data(Runtime.api.getLoLAPI().getMatchAPI().getMatch(region(region), gameId), RiotMatchData.class);
    }

    public static RiotTimelineData timeline(RegionShard region, String gameId) {
        return data(Runtime.api.getLoLAPI().getMatchAPI().getTimeline(region(region), gameId), RiotTimelineData.class);
    }

    public static List<String> matchIds(Summoner summoner, GameQueueType queue, int index, int count,
            long startTime, MatchlistMatchType type) {
        MatchListBuilder builder = new MatchListBuilder().withPlatform(platform(summoner.region()))
            .withPuuid(summoner.puuid()).withBeginIndex(index);
        if (count > 0) builder = builder.withCount(count);
        if (startTime > 0) builder = builder.withStartTime(startTime);
        if (type != null) builder = builder.withType(no.stelar7.api.r4j.basic.constants.types.lol.MatchlistMatchType.valueOf(type.name()));
        if (queue != null) builder = builder.withQueue(no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType.valueOf(queue.name()));
        return builder.get();
    }

    public static Map<Integer, Item> items() {
        return DATA.convertValue(Runtime.api.getDDragonAPI().getItems(), new TypeReference<Map<Integer, Item>>() {});
    }

    public static Map<Integer, StaticChampion> champions() {
        return DATA.convertValue(Runtime.api.getDDragonAPI().getChampions(), new TypeReference<Map<Integer, StaticChampion>>() {});
    }

    public static Map<Integer, StaticSummonerSpell> summonerSpells() {
        return DATA.convertValue(Runtime.api.getDDragonAPI().getSummonerSpells(), new TypeReference<Map<Integer, StaticSummonerSpell>>() {});
    }

    public static <T> T data(Object source, Class<T> target) {
        return source == null ? null : DATA.convertValue(source, target);
    }


    public static Summoner cachedSummoner(LeagueShard shard, String puuid) {
        return summonerFromCache(RedisClient.get(RedisKey.R4J_SUMMONER.of(shard.name(), puuid)));
    }

    public static List<Mastery> cachedMasteries(LeagueShard shard, String puuid) {
        return masteriesFromCache(RedisClient.get(RedisKey.R4J_CHAMPION_MASTERIES.of(shard.name(), puuid)));
    }

    public static Summoner summonerFromCache(String json) {
        return identity(JsonCodec.fromJson(json, no.stelar7.api.r4j.pojo.lol.summoner.Summoner.class));
    }

    public static List<Mastery> masteriesFromCache(String json) {
        return masteryValues(JsonCodec.fromJson(json,
            new TypeReference<List<no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery>>() {}));
    }

    // ============================================================================

    private static no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard platform(LeagueShard shard) {
        return no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard.valueOf(shard.name());
    }

    private static no.stelar7.api.r4j.basic.constants.api.regions.RegionShard region(RegionShard region) {
        return no.stelar7.api.r4j.basic.constants.api.regions.RegionShard.valueOf(region.name());
    }


    private static Summoner identity(no.stelar7.api.r4j.pojo.lol.summoner.Summoner value) {
        return value == null ? null : new Summoner(value.getPUUID(), value.getName(),
            value.getPlatform() == null ? null : LeagueShard.valueOf(value.getPlatform().name()),
            value.getSummonerLevel(), value.getProfileIconId());
    }

    private static List<Mastery> masteryValues(List<no.stelar7.api.r4j.pojo.lol.championmastery.ChampionMastery> entries) {
        if (entries == null) return null;
        List<Mastery> result = new ArrayList<>();
        for (var entry : entries) if (entry != null) result.add(new Mastery(entry.getChampionId(), entry.getChampionLevel(), entry.getChampionPoints()));
        return result;
    }

    private static final class Runtime {
        private static final R4J api = create();
        private static R4J create() {
            DataCall.setCacheProvider(EmptyCacheProvider.INSTANCE);
            return new R4J(new APICredentials(SettingsLoader.getSettings().getJsonSettings().getRiot().getKey()));
        }
    }
}
