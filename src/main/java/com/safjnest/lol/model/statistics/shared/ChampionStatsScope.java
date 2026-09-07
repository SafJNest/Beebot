package com.safjnest.lol.model.statistics.shared;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.FilterCodec;
import com.safjnest.lol.utils.PatchUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public record ChampionStatsScope(
    GameQueueType queue,
    TierType rank,
    Filter.RankBehavior rankBehavior,
    String patch,
    LeagueShard region
) {

    public static ChampionStatsScope from(Filter filter) {
        if (filter == null) return new ChampionStatsScope(null, null, Filter.RankBehavior.GREATER_OR_EQUAL, PatchUtils.getPatch(), null);
        return new ChampionStatsScope(
            filter.queue(),
            filter.rank(),
            filter.rankBehavior(),
            filter.patch(),
            filter.region()
        );
    }

    public String toKey() {
        String raw = FilterCodec.join(
            FilterCodec.encodeEnum(queue),
            rankBehavior.name(),
            FilterCodec.encodeEnum(rank),
            FilterCodec.encodeValue(patch),
            FilterCodec.encodeEnum(region)
        );
        return FilterCodec.encodeUrl(raw);
    }

    public static ChampionStatsScope fromKey(String key) {
        String raw = FilterCodec.decodeUrl(key);
        String[] parts = FilterCodec.split(raw);
        GameQueueType queue = FilterCodec.decodeEnum(parts[0], GameQueueType.class);
        Filter.RankBehavior behavior = FilterCodec.decodeEnum(parts[1], Filter.RankBehavior.class);
        TierType rank = FilterCodec.decodeEnum(parts[2], TierType.class);
        String patch = FilterCodec.decodeString(parts[3]);
        LeagueShard region = FilterCodec.decodeEnum(parts[4], LeagueShard.class);
        return new ChampionStatsScope(queue, rank, behavior, patch, region);
    }

    public Filter toFilter() {
        return new Filter()
            .setQueue(queue)
            .setRank(rank)
            .setRankBehavior(rankBehavior)
            .setPatch(patch)
            .setRegion(region);
    }

}
