package com.safjnest.lol.service;

import com.safjnest.lol.model.Build;
import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.ChampionView;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.ChampionUtils;
import com.safjnest.lol.tracker.DatabaseTracker;
import com.safjnest.redis.RedisClient;
import com.safjnest.redis.RedisKey;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;
import no.stelar7.api.r4j.pojo.lol.staticdata.champion.StaticChampion;

public class ChampionPageService {

    public ApiResult<ChampionView> get(
            String championValue,
            TierType rank,
            LeagueShard region,
            GameQueueType queue,
            LaneType role
    ) {
        StaticChampion champion = ChampionUtils.findChampion(championValue);
        if (champion == null) {
            return ApiResult.notFound();
        }

        GameQueueType selectedQueue = queue != null ? queue : GameQueueType.TEAM_BUILDER_RANKED_SOLO;
        Filter filter = new Filter()
            .setChampion(champion.getId())
            .setRank(rank)
            .setRegion(region)
            .setQueue(selectedQueue)
            .setLane(role);

        String key = RedisKey.CHAMPION_PAGE.of(filter.toKey());
        ChampionView cached;
        try {
            cached = RedisClient.get(key, ChampionView.class);
        } catch (RuntimeException exception) {
            RedisClient.delete(key);
            cached = null;
        }
        if (cached != null) {
            return ApiResult.ready(cached);
        }

        return compute(champion, filter, key);
    }

    // ============================================================================

    static void invalidate(Filter filter) {
        if (filter != null) RedisClient.delete(RedisKey.CHAMPION_PAGE.of(filter.toKey()));
    }

    private ApiResult<ChampionView> compute(StaticChampion champion, Filter filter, String key) {
        ChampionStatistics stats = ChampionStatsService.get(filter, false);
        Build build = BuildService.getAggregate(filter, false);
        if (stats == null || build == null) {
            DatabaseTracker.startChampionData(filter, stats == null, build == null);
            return ApiResult.pending();
        }

        ChampionView page = new ChampionView(
            new ChampionView.Champion(
                champion.getId(),
                champion.getName(),
                ChampionUtils.getChampionProfilePic(champion.getId())
            ),
            stats,
            build
        );
        RedisClient.set(RedisKey.CHAMPION_PAGE, page, filter.toKey());
        return ApiResult.ready(page);
    }

}
