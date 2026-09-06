package com.safjnest.lol.model.statistics.shared;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.utils.PatchUtils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record ChampionStatsScope(
    GameQueueType queue,
    TierType rank,
    Filter.RankBehavior rankBehavior,
    String patch,
    LeagueShard region,
    long timeStart,
    long timeEnd
) {

    public static ChampionStatsScope from(Filter filter) {
        if (filter == null) return new ChampionStatsScope(null, null, Filter.RankBehavior.GREATER_OR_EQUAL, PatchUtils.getPatch(), null, 0, 0);
        return new ChampionStatsScope(
            filter.queue(),
            filter.rank(),
            filter.rankBehavior(),
            filter.patch(),
            filter.region(),
            filter.timeStart(),
            filter.timeEnd()
        );
    }

    public String toKey() {
        String raw = val(queue) + "|" + rankBehavior + "|" + val(rank) + "|" + val(patch) + "|" + val(region);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static ChampionStatsScope fromKey(String key) {
        String raw = new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);
        String[] parts = raw.split("\\|", -1);
        GameQueueType queue = parts[0].equals("*") ? null : GameQueueType.valueOf(parts[0]);
        Filter.RankBehavior behavior = Filter.RankBehavior.valueOf(parts[1]);
        TierType rank = parts[2].equals("*") ? null : TierType.valueOf(parts[2]);
        String patch = parts[3].equals("*") ? null : parts[3];
        LeagueShard region = parts[4].equals("*") ? null : LeagueShard.valueOf(parts[4]);
        return new ChampionStatsScope(queue, rank, behavior, patch, region, 0, 0);
    }

    public Filter toFilter() {
        return new Filter()
            .setQueue(queue)
            .setRank(rank)
            .setRankBehavior(rankBehavior)
            .setPatch(patch)
            .setRegion(region)
            .setPeriod(timeStart, timeEnd);
    }

    private static String val(Object o) {
        return o == null ? "*" : o.toString();
    }
}
