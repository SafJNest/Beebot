package com.R4B.api.dto.summoner;

import com.R4B.api.dto.*;
import com.R4B.lol.*;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Rank(
    TierDivisionType tier,
    int lp,
    int wins,
    int losses,
    Long globalRanking,
    Long regionRanking
) {
    public Rank {
        tier = tier == null ? TierDivisionType.UNRANKED : tier;
    }

    public static Rank unranked() {
        return new Rank(TierDivisionType.UNRANKED, 0, 0, 0, null, null);
    }

    public Rank(TierDivisionType tier, int lp, int wins, int losses) {
        this(tier, lp, wins, losses, null, null);
    }

    public Rank withRankings(Long globalValue, Long regionValue) {
        return new Rank(tier, lp, wins, losses, globalValue, regionValue);
    }

    public int games() {
        return wins + losses;
    }

    public double winrate() {
        return games() == 0 ? 0 : (double) wins * 100 / games();
    }
}
