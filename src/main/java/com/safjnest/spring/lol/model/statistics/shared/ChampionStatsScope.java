package com.safjnest.spring.lol.model.statistics.shared;

import com.safjnest.spring.lol.service.FilterDefaults;
import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.R4B.lol.*;
import com.safjnest.spring.lol.utils.PatchUtils;

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
        return FilterDefaults.create()
            .setQueue(queue)
            .setRank(rank)
            .setRankBehavior(rankBehavior)
            .setPatch(patch)
            .setRegion(region);
    }

}
