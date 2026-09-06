package com.safjnest.lol.model;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
      String raw = new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      return new Filter()
        .setQueue(parts[0].equals("*") ? null : GameQueueType.valueOf(parts[0]))
        .setRank(parts[1].equals("*") ? null : TierType.valueOf(parts[1]))
        .setPatch(parts[2].equals("*") ? null : parts[2])
        .setRegion(parts[3].equals("*") ? null : LeagueShard.valueOf(parts[3]))
        .setLane(parts.length > 4 && !parts[4].equals("*") ? LaneType.valueOf(parts[4]) : null);
    }

    public static Filter fromKey(String key) {
      String raw = new String(Base64.getDecoder().decode(key), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      Filter filter = new Filter()
        .setChampion(Integer.parseInt(parts[0]))
        .setLane(parts[1].equals("*") ? null : LaneType.valueOf(parts[1]))
        .setQueue(parts[2].equals("*") ? null : GameQueueType.valueOf(parts[2]))
        .setRank(parts[3].equals("*") ? null : TierType.valueOf(parts[3]))
        .setRankBehavior(RankBehavior.valueOf(parts[4]))
        .setPatch(parts[5].equals("*") ? null : parts[5])
        .setRegion(parts[6].equals("*") ? null : LeagueShard.valueOf(parts[6]));
      if (parts.length > 7 && !parts[7].equals("*"))
        filter.setOpponent(Integer.parseInt(parts[7]));
      if (parts.length > 8 && !parts[8].equals("*"))
        filter.setDuo(Integer.parseInt(parts[8]));
      return filter;
    }

    public static Filter fromStateKey(String key) {
      String raw = new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      Filter filter = new Filter()
        .setChampion(Integer.parseInt(parts[0]))
        .setLane(parts[1].equals("*") ? null : LaneType.values()[Integer.parseInt(parts[1])])
        .setQueue(parts[2].equals("*") ? null : GameQueueType.values()[Integer.parseInt(parts[2])])
        .setRank(parts[3].equals("*") ? null : TierType.values()[Integer.parseInt(parts[3])])
        .setPatch(parts[4].equals("*") ? null : parts[4])
        .setRegion(parts[5].equals("*") ? null : LeagueShard.valueOf(parts[5]));
      if (parts.length > 6 && !parts[6].equals("*"))
        filter.setOpponent(Integer.parseInt(parts[6]));
      if (parts.length > 7 && !parts[7].equals("*"))
        filter.setDuo(Integer.parseInt(parts[7]));
      if (parts.length > 8) filter.setPeriod(longValue(parts[8]), parts.length > 9 ? longValue(parts[9]) : 0);
      if (parts.length > 10) {
        try { filter.setRankBehavior(RankBehavior.valueOf(parts[10])); }
        catch (RuntimeException ignored) { }
      }
      return filter;
    }

    public static Filter fromSummonerKey(String key) {
      String raw = new String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8);
      String[] parts = raw.split("\\|");
      if (parts.length != 11) throw new IllegalArgumentException("Invalid summoner filter key");
      return new Filter()
        .setChampion(intValue(parts[0]))
        .setLane(parts[1].equals("*") ? null : LaneType.valueOf(parts[1]))
        .setQueue(parts[2].equals("*") ? null : GameQueueType.valueOf(parts[2]))
        .setRank(parts[3].equals("*") ? null : TierType.valueOf(parts[3]))
        .setRankBehavior(RankBehavior.valueOf(parts[4]))
        .setPatch(parts[5].equals("*") ? null : parts[5])
        .setRegion(parts[6].equals("*") ? null : LeagueShard.valueOf(parts[6]))
        .setOpponent(intValue(parts[7]))
        .setDuo(intValue(parts[8]))
        .setPeriod(longValue(parts[9]), longValue(parts[10]));
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
        String raw = champion + "|" + val(lane) + "|" + val(queue) + "|" + val(rank) + "|"
                + rankBehavior + "|" + val(patch) + "|" + val(region);
        if (opponent != 0 || duo != 0)
            raw += "|" + val(opponent) + "|" + val(duo);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String pageKey() {
        String raw = val(lane) + "|" + val(queue) + "|" + val(rank) + "|"
                + rankBehavior + "|" + val(patch) + "|" + val(region) + "|" + timeStart + "|" + timeEnd;
        if (opponent != 0 || duo != 0)
            raw += "|" + val(opponent) + "|" + val(duo);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String toStateKey() {
        String raw = champion + "|" + ordinal(lane) + "|" + ordinal(queue) + "|" + ordinal(rank) + "|"
                + val(patch) + "|" + val(region) + "|" + val(opponent) + "|" + val(duo)
                + "|" + timeStart + "|" + timeEnd + "|" + rankBehavior;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String toSummonerKey() {
        String raw = champion + "|" + val(lane) + "|" + val(queue) + "|" + val(rank) + "|"
                + rankBehavior + "|" + val(patch) + "|" + val(region) + "|" + val(opponent) + "|" + val(duo)
                + "|" + timeStart + "|" + timeEnd;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String genericKey() {
        String raw = val(queue) + "|" + val(rank) + "|"
                + rankBehavior + "|" + val(patch) + "|" + val(region) + "|" + val(lane)
                + "|" + timeStart + "|" + timeEnd;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String val(Object o) {
        return o != null ? o.toString() : "*";
    }

    private static String val(int i) {
        return i != 0 ? String.valueOf(i) : "*";
    }

    private static long longValue(String value) {
        try { return Long.parseLong(value); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static int intValue(String value) {
        return value.equals("*") ? 0 : Integer.parseInt(value);
    }

    private static String ordinal(Enum<?> e) {
        return e != null ? String.valueOf(e.ordinal()) : "*";
    }

}
