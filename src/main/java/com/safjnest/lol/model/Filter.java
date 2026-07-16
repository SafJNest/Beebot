package com.safjnest.lol.model;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;

import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.PatchUtils;
import com.safjnest.lol.utils.TierDivisionUtils;

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

    public String sql() {
        StringBuilder sb = new StringBuilder("WHERE p.champion = ").append(champion);
        if (queue != null)
            sb.append(" AND m.queue = '").append(queue).append("'");
        if (patch != null)
            sb.append(" AND m.patch_major = '").append(patch).append("'");
        if (rank != null)
            sb.append(rankSql());
        if (region != null)
            sb.append(" AND m.region = '").append(region).append("'");
        if (lane != null && GameQueueTypeUtils.hasLane(queue))
            sb.append(" AND p.lane = '").append(lane).append("'");
        if (opponent != 0) {
            sb.append(" AND EXISTS (SELECT 1 FROM participant opp WHERE opp.match_id = p.match_id")
              .append(" AND opp.champion = ").append(opponent);
            if (GameQueueTypeUtils.isCherry(queue))
                sb.append(" AND opp.subteam <> p.subteam");
            else {
                sb.append(" AND opp.team <> p.team");
                if (lane != null && GameQueueTypeUtils.hasLane(queue))
                    sb.append(" AND opp.lane = p.lane");
            }
            sb.append(")");
        }
        if (duo != 0) {
            sb.append(" AND EXISTS (SELECT 1 FROM participant duo WHERE duo.match_id = p.match_id")
              .append(" AND duo.champion = ").append(duo)
              .append(" AND duo.id <> p.id");
            if (GameQueueTypeUtils.isCherry(queue))
                sb.append(" AND duo.subteam = p.subteam");
            else
                sb.append(" AND duo.team = p.team");
            sb.append(")");
        }
        return sb.toString();
    }

    public String sqlMatchOnly() {
        StringBuilder sb = new StringBuilder("WHERE 1=1");
        if (patch != null)
            sb.append(" AND m.patch_major = '").append(patch).append("'");
        if (queue != null)
            sb.append(" AND m.queue = '").append(queue).append("'");
        if (rank != null)
            sb.append(rankSql());
        if (region != null)
            sb.append(" AND m.region = '").append(region).append("'");
        return sb.toString();
    }

    /** Tutti i participant dei match filtrati; una lane seleziona i match compatibili. */
    public String sqlAllParticipants() {
        StringBuilder sb = new StringBuilder(
                "FROM participant p JOIN `match` m ON p.match_id = m.id "
                + "LEFT JOIN summoner s ON s.id = p.summoner_id WHERE 1=1");
        if (patch != null)
            sb.append(" AND m.patch_major = '").append(patch).append("'");
        if (queue != null)
            sb.append(" AND m.queue = '").append(queue).append("'");
        if (rank != null)
            sb.append(rankSql());
        if (region != null)
            sb.append(" AND m.region = '").append(region).append("'");
        if (lane != null && GameQueueTypeUtils.hasLane(queue))
            sb.append(" AND EXISTS (SELECT 1 FROM participant lane_filter WHERE lane_filter.match_id = p.match_id")
              .append(" AND lane_filter.lane = '").append(lane).append("')");
        return sb.toString();
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

    private String rankSql() {
        if (rank == TierType.CHALLENGER)
            return " AND m.rank IN ('CHALLENGER', 'GRANDMASTER')";
        if (rankBehavior == RankBehavior.EXACT)
            return " AND m.rank = '" + rank + "'";
        return " AND m.rank IN ('" + String.join("', '", TierDivisionUtils.getHigherTiers(rank).stream().map(TierType::name).collect(Collectors.toList())) + "')";
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
