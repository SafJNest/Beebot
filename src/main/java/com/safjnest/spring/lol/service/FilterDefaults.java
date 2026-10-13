package com.safjnest.spring.lol.service;
import com.R4B.api.dto.ActivityFilter;
import com.R4B.api.dto.Filter;
import com.R4B.lol.*;
import com.safjnest.spring.lol.utils.PatchUtils;
import com.safjnest.spring.lol.utils.SeasonUtils;
public final class FilterDefaults {
    private FilterDefaults() {}
    public static Filter create() {
        Filter value = new Filter().setPatch(PatchUtils.getPatch()).setRank(TierType.EMERALD);
        long[] period = SeasonUtils.getCurrentSplitRange();
        return value.setPeriod(period);
    }
    public static ActivityFilter activity() {
        ActivityFilter value = new ActivityFilter();
        value.setPeriod(canonical().period());
        return value;
    }

    public static Filter canonical() {
        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        return create()
            .setChampion(0)
            .setLane(null)
            .setQueue(null)
            .setRank(null)
            .setPatch(null)
            .setRegion(null)
            .setOpponent(0)
            .setDuo(0)
            .setPeriod(season == null ? 0 : season.start(), season == null ? 0 : season.end());
    }

    public static Filter fromKey(String key) {
        return Filter.fromKey(key).setPeriod(create().period());
    }
    public static Filter fromGenericKey(String key) {
        return Filter.fromGenericKey(key).setPeriod(create().period());
    }
    public static Filter fromStateKey(String key) {
        Filter result = Filter.fromStateKey(key);
        String[] parts = com.R4B.lol.FilterCodec.split(com.R4B.lol.FilterCodec.decodeUrl(key));
        return parts.length < 9 ? result.setPeriod(create().period()) : result;
    }
}
