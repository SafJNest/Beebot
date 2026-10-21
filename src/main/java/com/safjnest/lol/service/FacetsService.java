package com.safjnest.lol.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.safjnest.lol.model.match.MatchFacetQuery;
import com.safjnest.lol.model.match.MatchFacets;
import com.safjnest.lol.utils.LeagueShardUtils;
import com.safjnest.nosql.MongoDB;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;
import com.safjnest.utils.JsonCodec;

public final class FacetsService {

    private FacetsService() {}

    public static MatchFacets get(String puuid, MatchFacetQuery filter) {
        if (puuid == null || puuid.isBlank() || filter == null || filter.shard() == null) return empty();

        String filterKey = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(JsonCodec.toJson(filter).getBytes(StandardCharsets.UTF_8));
        Object[] keyArguments = {
            LeagueShardUtils.cacheRegion(filter.shard()), filter.shard().name(), puuid, filterKey
        };
        MatchFacets facets = RedisClient.get(RedisKey.SUMMONER_MATCH_FACETS.of(keyArguments), MatchFacets.class);
        if (facets != null) return facets;

        facets = MongoDB.searchFacets(puuid, filter);
        if (facets != null) RedisClient.set(RedisKey.SUMMONER_MATCH_FACETS, facets, keyArguments);
        return facets;
    }

    public static long count(String puuid, MatchFacetQuery filter) {
        return get(puuid, filter).total();
    }

    public static long count(String puuid) {
        if (puuid == null || puuid.isBlank()) return 0;
        Object[] keyArguments = {puuid};
        Long count = RedisClient.get(RedisKey.SUMMONER_MATCH_FACET_COUNT.of(keyArguments), Long.class);
        if (count != null) return count;

        count = MongoDB.countMatchFacets(puuid);
        RedisClient.set(RedisKey.SUMMONER_MATCH_FACET_COUNT, count, keyArguments);
        return count;
    }

    //====

    private static MatchFacets empty() {
        return new MatchFacets(0, new MatchFacets.Facets(
            List.of(), List.of(), List.of(), List.of(), List.of(), new MatchFacets.Range(null, null)));
    }
}
