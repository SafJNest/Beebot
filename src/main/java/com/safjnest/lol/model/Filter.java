package com.safjnest.lol.model;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import com.safjnest.lol.utils.FilterCodec;
import com.safjnest.lol.utils.NumberUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.SeasonUtils;

public class Filter {

    public enum RankBehavior {
        EXACT,
        GREATER_OR_EQUAL
    }

    public Filter() {
        this.patch = PatchUtils.getPatch();
        this.rank = TierType.EMERALD;
        this.rankBehavior = RankBehavior.GREATER_OR_EQUAL;
        long[] period = SeasonUtils.getCurrentSplitRange();
        if (period != null) {
            this.timeStart = period[0];
            this.timeEnd = period[1];
        }
    }

    public static Filter summoner(long timeStart, long timeEnd) {
        return new Filter()
            .setChampion(0)
            .setLane(null)
            .setQueue(null)
            .setRank(null)
            .setPatch(null)
            .setRegion(null)
            .setOpponent(0)
            .setDuo(0)
            .setPeriod(timeStart, timeEnd);
    }

    public static Filter canonical() {
        SeasonUtils.SeasonRange season = SeasonUtils.getCurrentSeasonRange();
        return new Filter()
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

    public static Filter fromGenericKey(String key) {
        String raw = FilterCodec.decode(key);
        String[] parts = FilterCodec.split(raw);
        return new Filter()
            .setQueue(FilterCodec.decodeEnum(parts[0], GameQueueType.class))
            .setRank(FilterCodec.decodeEnum(parts[1], TierType.class))
            .setPatch(FilterCodec.decodeString(parts[2]))
            .setRegion(FilterCodec.decodeEnum(parts[3], LeagueShard.class))
            .setLane(parts.length > 4 ? FilterCodec.decodeEnum(parts[4], LaneType.class) : null);
    }

    public static Filter fromKey(String key) {
        String raw = FilterCodec.decode(key);
        String[] parts = FilterCodec.split(raw);
        Filter filter = new Filter()
            .setChampion(NumberUtils.parseInt(parts[0]))
            .setLane(FilterCodec.decodeEnum(parts[1], LaneType.class))
            .setQueue(FilterCodec.decodeEnum(parts[2], GameQueueType.class))
            .setRank(FilterCodec.decodeEnum(parts[3], TierType.class))
            .setRankBehavior(RankBehavior.valueOf(parts[4]))
            .setPatch(FilterCodec.decodeString(parts[5]))
            .setRegion(FilterCodec.decodeEnum(parts[6], LeagueShard.class));
        if (parts.length > 7) filter.setOpponent(FilterCodec.decodeInt(parts[7]));
        if (parts.length > 8) filter.setDuo(FilterCodec.decodeInt(parts[8]));
        return filter;
    }

    public static Filter fromStateKey(String key) {
        String raw = FilterCodec.decodeUrl(key);
        String[] parts = FilterCodec.split(raw);
        Filter filter = new Filter()
            .setChampion(NumberUtils.parseInt(parts[0]))
            .setLane(FilterCodec.decodeOrdinal(parts[1], LaneType.class))
            .setQueue(FilterCodec.decodeOrdinal(parts[2], GameQueueType.class))
            .setRank(FilterCodec.decodeOrdinal(parts[3], TierType.class))
            .setPatch(FilterCodec.decodeString(parts[4]))
            .setRegion(FilterCodec.decodeEnum(parts[5], LeagueShard.class));
        if (parts.length > 6) filter.setOpponent(FilterCodec.decodeInt(parts[6]));
        if (parts.length > 7) filter.setDuo(FilterCodec.decodeInt(parts[7]));
        if (parts.length > 8) filter.setPeriod(FilterCodec.decodeLong(parts[8]), parts.length > 9 ? FilterCodec.decodeLong(parts[9]) : 0);
        if (parts.length > 10) {
        try { filter.setRankBehavior(RankBehavior.valueOf(parts[10])); }
        catch (RuntimeException ignored) { }
      }
        return filter;
    }

    public static Filter fromSummonerKey(String key) {
        String raw = FilterCodec.decodeUrl(key);
        String[] parts = FilterCodec.split(raw);
        if (parts.length != 11) throw new IllegalArgumentException("Invalid summoner filter key");
        return new Filter()
            .setChampion(FilterCodec.decodeInt(parts[0]))
            .setLane(FilterCodec.decodeEnum(parts[1], LaneType.class))
            .setQueue(FilterCodec.decodeEnum(parts[2], GameQueueType.class))
            .setRank(FilterCodec.decodeEnum(parts[3], TierType.class))
            .setRankBehavior(RankBehavior.valueOf(parts[4]))
            .setPatch(FilterCodec.decodeString(parts[5]))
            .setRegion(FilterCodec.decodeEnum(parts[6], LeagueShard.class))
            .setOpponent(FilterCodec.decodeInt(parts[7]))
            .setDuo(FilterCodec.decodeInt(parts[8]))
            .setPeriod(FilterCodec.decodeLong(parts[9]), FilterCodec.decodeLong(parts[10]));
    }

    private int champion;
    private LaneType lane;
    private GameQueueType queue;
    private TierType rank;
    private RankBehavior rankBehavior = RankBehavior.GREATER_OR_EQUAL;
    private String patch;
    private LeagueShard region;
    private int opponent;
    private int duo;
    private long timeStart;
    private long timeEnd;

    public Filter setChampion(int champion) {
        this.champion = champion;
        return this;
    }

    public Filter setLane(LaneType lane) {
        this.lane = lane;
        return this;
    }

    public Filter setQueue(GameQueueType queue) {
        this.queue = queue;
        return this;
    }

    public Filter setRank(TierType rank) {
        this.rank = rank;
        return this;
    }

    public Filter setRankBehavior(RankBehavior b) {
        this.rankBehavior = b != null ? b : RankBehavior.GREATER_OR_EQUAL;
        return this;
    }

    public Filter setPatch(String patch) {
        this.patch = patch;
        return this;
    }

    public Filter setRegion(LeagueShard region) {
        this.region = region;
        return this;
    }

    public int champion() {
        return champion;
    }

    public LaneType lane() {
        return lane;
    }

    public GameQueueType queue() {
        return queue;
    }

    public TierType rank() {
        return rank;
    }

    public String patch() {
        return patch;
    }

    public LeagueShard region() {
        return region;
    }

    public int opponent() {
        return opponent;
    }

    public int duo() {
        return duo;
    }

    public Filter setOpponent(int opponent) {
        this.opponent = opponent;
        return this;
    }

    public Filter setDuo(int duo) {
        this.duo = duo;
        return this;
    }

    public Filter setPeriod(long timeStart, long timeEnd) {
        this.timeStart = Math.max(0, timeStart);
        this.timeEnd = Math.max(0, timeEnd);
        return this;
    }

    public Filter setPeriod(long[] period) {
        return period == null || period.length < 2 ? setPeriod(0, 0) : setPeriod(period[0], period[1]);
    }

    public RankBehavior rankBehavior() {
        return rankBehavior;
    }

    public long timeStart() {
        return timeStart;
    }

    public long timeEnd() {
        return timeEnd;
    }

    public long[] period() {
        return new long[] {timeStart, timeEnd};
    }

    public String toKey() {
        String raw = FilterCodec.join(
            String.valueOf(champion),
            FilterCodec.encodeEnum(lane),
            FilterCodec.encodeEnum(queue),
            FilterCodec.encodeEnum(rank),
            rankBehavior.name(),
            FilterCodec.encodeValue(patch),
            FilterCodec.encodeEnum(region)
        );
        if (opponent != 0 || duo != 0) raw += "|" + FilterCodec.encodeInt(opponent) + "|" + FilterCodec.encodeInt(duo);
        return FilterCodec.encode(raw);
    }

    public String pageKey() {
        String raw = FilterCodec.join(
            FilterCodec.encodeEnum(lane),
            FilterCodec.encodeEnum(queue),
            FilterCodec.encodeEnum(rank),
            rankBehavior.name(),
            FilterCodec.encodeValue(patch),
            FilterCodec.encodeEnum(region),
            String.valueOf(timeStart),
            String.valueOf(timeEnd)
        );
        if (opponent != 0 || duo != 0) raw += "|" + FilterCodec.encodeInt(opponent) + "|" + FilterCodec.encodeInt(duo);
        return FilterCodec.encode(raw);
    }

    public String toStateKey() {
        String raw = FilterCodec.join(
            String.valueOf(champion),
            FilterCodec.encodeOrdinal(lane),
            FilterCodec.encodeOrdinal(queue),
            FilterCodec.encodeOrdinal(rank),
            FilterCodec.encodeValue(patch),
            FilterCodec.encodeEnum(region),
            FilterCodec.encodeInt(opponent),
            FilterCodec.encodeInt(duo),
            String.valueOf(timeStart),
            String.valueOf(timeEnd),
            rankBehavior.name()
        );
        return FilterCodec.encodeUrl(raw);
    }

    public String toSummonerKey() {
        String raw = FilterCodec.join(
            String.valueOf(champion),
            FilterCodec.encodeEnum(lane),
            FilterCodec.encodeEnum(queue),
            FilterCodec.encodeEnum(rank),
            rankBehavior.name(),
            FilterCodec.encodeValue(patch),
            FilterCodec.encodeEnum(region),
            FilterCodec.encodeInt(opponent),
            FilterCodec.encodeInt(duo),
            String.valueOf(timeStart),
            String.valueOf(timeEnd)
        );
        return FilterCodec.encodeUrl(raw);
    }

    public String genericKey() {
        String raw = FilterCodec.join(
            FilterCodec.encodeEnum(queue),
            FilterCodec.encodeEnum(rank),
            rankBehavior.name(),
            FilterCodec.encodeValue(patch),
            FilterCodec.encodeEnum(region),
            FilterCodec.encodeEnum(lane),
            String.valueOf(timeStart),
            String.valueOf(timeEnd)
        );
        return FilterCodec.encode(raw);
    }

}
