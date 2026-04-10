package com.safjnest.lol.build;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class BuildFilter {

    public enum RankBehavior { EXACT, GREATER_OR_EQUAL }

    private int champion;
    private LaneType lane;
    private GameQueueType queue;
    private TierType rank;
    private RankBehavior rankBehavior = RankBehavior.GREATER_OR_EQUAL;
    private String patch;
    private LeagueShard region;

    public BuildFilter setChampion(int champion)           { this.champion = champion; return this; }
    public BuildFilter setLane(LaneType lane)              { this.lane = lane; return this; }
    public BuildFilter setQueue(GameQueueType queue)       { this.queue = queue; return this; }
    public BuildFilter setRank(TierType rank)              { this.rank = rank; return this; }
    public BuildFilter setRankBehavior(RankBehavior b)     { this.rankBehavior = b; return this; }
    public BuildFilter setPatch(String patch)              { this.patch = patch; return this; }
    public BuildFilter setRegion(LeagueShard region)       { this.region = region; return this; }

    public int champion()        { return champion; }
    public LaneType lane()       { return lane; }
    public GameQueueType queue() { return queue; }

    public String sql() {
        StringBuilder sb = new StringBuilder("WHERE p.champion = ").append(champion);
        if (lane != null)         sb.append(" AND p.lane = '").append(lane).append("'");
        if (queue != null)        sb.append(" AND m.queue = '").append(queue).append("'");
        if (patch != null)        sb.append(" AND m.patch_major = '").append(patch).append("'");
        if (rank != null)         sb.append(rankSql());
        if (region != null)       sb.append(" AND m.region = '").append(region).append("'");
        return sb.toString();
    }

    public String toKey(ChampionBuildService.Strategy strategy) {
        String raw = champion + "|" + val(lane) + "|" + val(queue) + "|" + val(rank) + "|"
                + val(patch) + "|" + val(region) + "|" + strategy.name();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String rankSql() {
        if (rank == TierType.CHALLENGER)
            return " AND m.rank IN ('CHALLENGER', 'GRANDMASTER')";
        if (rankBehavior == RankBehavior.EXACT)
            return " AND p.rank = '" + rank + "'";
        // TODO: GREATER_OR_EQUAL → IN list from TierType ordering
        return " AND p.rank = '" + rank + "'";
    }

    private static String val(Object o) { return o != null ? o.toString() : "*"; }
}
