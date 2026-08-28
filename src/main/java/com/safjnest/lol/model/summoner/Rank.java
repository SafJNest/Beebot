package com.safjnest.lol.model.summoner;

import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public record Rank(
    TierDivisionType tier,
    int lp,
    int wins,
    int losses
) {
    public Rank {
        tier = tier == null ? TierDivisionType.UNRANKED : tier;
    }

    public static Rank unranked() {
        return new Rank(TierDivisionType.UNRANKED, 0, 0, 0);
    }

    public int games() {
        return wins + losses;
    }

    public double winrate() {
        return games() == 0 ? 0 : (double) wins * 100 / games();
    }
}
