package com.safjnest.lol.build;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class BuildFilter {
  
  private int champion;

  private LaneType lane;
  private GameQueueType queue;

  private LeagueShard region;

  private TierType rank;

  private enum RankBehavior {
    EQUAL,
    GREATER
  }

  private RankBehavior rankBehavior;

  private String patch;

  public BuildFilter() {
    this.rankBehavior = RankBehavior.GREATER;
  }

  public BuildFilter setChampion(int champion) {
    this.champion = champion;
    return this;
  }

  public BuildFilter setLane(LaneType lane) {
    this.lane = lane;
    return this;
  }

  public BuildFilter setQueue(GameQueueType queue) {
    this.queue = queue;
    return this;
  }

  public BuildFilter setRank(TierType rank) {
    this.rank = rank;
    return this;
  }

  public BuildFilter setRankBehavior(RankBehavior rankBehavior) {
    this.rankBehavior = rankBehavior;
    return this;
  }

  public BuildFilter setPatch(String patch) {
    this.patch = patch;
    return this;
  }

  public BuildFilter setRegion(LeagueShard region) {
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


  public String sql() {
    // String sql = "SELECT p.win, p.build FROM participant p " +
    // "JOIN `match` m ON m.id = p.match_id " +
    // "WHERE p.champion = ? AND p.lane = ? AND m.patch_major = '16.6' AND m.queue = 'TEAM_BUILDER_RANKED_SOLO'";
    StringBuilder sql = new StringBuilder();
    sql.append("WHERE p.champion = ").append(champion);

    if (lane != null) {
      sql.append(" AND p.lane = '").append(lane).append("'");
    }

    if (queue != null) {
      sql.append(" AND m.queue = '").append(queue).append("'");
    }
    if (patch != null) {
      sql.append(" AND m.patch_major = '").append(patch).append("'");
    }

    if (rank != null) {
      sql.append(rankSQL());
    }

    if (region != null) {
      sql.append(" AND m.region = '").append(region).append("'");
    }

    return sql.toString();
  }

  private String rankSQL() {
    if (rank == TierType.CHALLENGER) {
      return "AND m.rank IN ('CHALLENGER', 'GRANDMASTER')";
    }
    StringBuilder sql = new StringBuilder();
    switch (rankBehavior) {
      case EQUAL:
        sql.append(" AND p.rank = '").append(rank).append("'");
        break;
      case GREATER:
        //TODO: get all the greater TierTypes than use IN ()
        break;
    }
    return sql.toString();

  }
  
}
