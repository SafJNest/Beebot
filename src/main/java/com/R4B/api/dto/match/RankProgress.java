package com.R4B.api.dto.match;

import com.R4B.api.dto.*;
import com.R4B.lol.TierDivisionType;

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
