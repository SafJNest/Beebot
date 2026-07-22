package com.safjnest.lol.model;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.safjnest.lol.utils.PatchUtils;

public class Filter {

    public enum RankBehavior {
        EXACT,
        GREATER_OR_EQUAL
    }

    public Filter() {
        this.patch = PatchUtils.getPatch();
        this.rank = TierType.EMERALD;
        this.rankBehavior = RankBehavior.GREATER_OR_EQUAL;
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
        .setPatch(parts[4].equals("*") ? null : parts[4])
        .setRegion(parts[5].equals("*") ? null : LeagueShard.valueOf(parts[5]));
      if (parts.length > 6 && !parts[6].equals("*"))
        filter.setOpponent(Integer.parseInt(parts[6]));
      if (parts.length > 7 && !parts[7].equals("*"))
        filter.setDuo(Integer.parseInt(parts[7]));
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
      return filter;
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
        this.rankBehavior = b;
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

    public String toKey() {
        String raw = champion + "|" + val(lane) + "|" + val(queue) + "|" + val(rank) + "|"
                + val(patch) + "|" + val(region);
        if (opponent != 0 || duo != 0)
            raw += "|" + val(opponent) + "|" + val(duo);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String toStateKey() {
        String raw = champion + "|" + ordinal(lane) + "|" + ordinal(queue) + "|" + ordinal(rank) + "|"
                + val(patch) + "|" + val(region) + "|" + val(opponent) + "|" + val(duo);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String genericKey() {
        String raw = val(queue) + "|" + val(rank) + "|"
                + val(patch) + "|" + val(region) + "|" + val(lane);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String val(Object o) {
        return o != null ? o.toString() : "*";
    }

    private static String val(int i) {
        return i != 0 ? String.valueOf(i) : "*";
    }

    private static String ordinal(Enum<?> e) {
        return e != null ? String.valueOf(e.ordinal()) : "*";
    }

}
