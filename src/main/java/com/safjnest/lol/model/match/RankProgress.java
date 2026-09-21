package com.safjnest.lol.model.match;

import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public final class RankProgress {

    public TierDivisionType rank;
    public Integer lp;
    public Integer gain;
    public TierDivisionType previousRank;
    public Integer previousLp;

    public RankProgress() {}

    public RankProgress(TierDivisionType rank, Integer lp, Integer gain, TierDivisionType previousRank, Integer previousLp) {
        this.rank = rank;
        this.lp = lp;
        this.gain = gain;
        this.previousRank = previousRank;
        this.previousLp = previousLp;
    }

}
