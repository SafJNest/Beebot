package com.safjnest.lol.model;

import java.util.Date;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public class RankData {

    private GameQueueType queue;
    private TierDivisionType rank;
    private int lp;
    private int wins;
    private int losses;
    private Date updated_at;

    public GameQueueType getQueue() { return queue; }
    public void setQueue(GameQueueType queue) { this.queue = queue; }

    public TierDivisionType getRank() { return rank; }
    public void setRank(TierDivisionType rank) { this.rank = rank; }

    public int getLp() { return lp; }
    public void setLp(int lp) { this.lp = lp; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public Date getUpdated_at() { return updated_at; }
    public void setUpdated_at(Date updated_at) { this.updated_at = updated_at; }
}
