package com.safjnest.lol.build;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.safjnest.lol.utils.GameQueueTypeUtils;

public class ChampionFilter {

    public enum RankBehavior {
        EXACT,
        GREATER_OR_EQUAL
    }

    private int champion;
    private LaneType lane;
    private GameQueueType queue;
    private TierType rank;
    private RankBehavior rankBehavior = RankBehavior.GREATER_OR_EQUAL;
    private String patch;
    private LeagueShard region;

    public ChampionFilter setChampion(int champion) {
        this.champion = champion;
        return this;
    }

    public ChampionFilter setLane(LaneType lane) {
        this.lane = lane;
        return this;
    }

    public ChampionFilter setQueue(GameQueueType queue) {
        this.queue = queue;
        return this;
    }

    public ChampionFilter setRank(TierType rank) {
        this.rank = rank;
        return this;
    }

    public ChampionFilter setRankBehavior(RankBehavior b) {
        this.rankBehavior = b;
        return this;
    }

    public ChampionFilter setPatch(String patch) {
        this.patch = patch;
        return this;
    }

    public ChampionFilter setRegion(LeagueShard region) {
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

    public String sql() {
        StringBuilder sb = new StringBuilder("WHERE p.champion = ").append(champion);
        if (lane != null && GameQueueTypeUtils.hasLane(queue))
            sb.append(" AND p.lane = '").append(lane).append("'");
        if (queue != null)
            sb.append(" AND m.queue = '").append(queue).append("'");
        if (patch != null)
            sb.append(" AND m.patch_major = '").append(patch).append("'");
        if (rank != null)
            sb.append(rankSql());
        if (region != null)
            sb.append(" AND m.region = '").append(region).append("'");
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

    /** Tutti i participant dei match filtrati, senza filtrare per champion/lane. */
    public String sqlAllParticipants() {
        StringBuilder sb = new StringBuilder(
                "FROM participant p JOIN `match` m ON p.match_id = m.id WHERE 1=1");
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

    public String toKey() {
        String raw = champion + "|" + val(lane) + "|" + val(queue) + "|" + val(rank) + "|"
                + val(patch) + "|" + val(region);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public String genericKey() {
        String raw = val(queue) + "|" + val(rank) + "|"
                + val(patch) + "|" + val(region);
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String rankSql() {
        if (rank == TierType.CHALLENGER)
            return " AND m.rank IN ('CHALLENGER', 'GRANDMASTER')";
        if (rankBehavior == RankBehavior.EXACT)
            return " AND m.rank = '" + rank + "'";
        return " AND m.rank = '" + rank + "'";
    }

    private static String val(Object o) {
        return o != null ? o.toString() : "*";
    }
}