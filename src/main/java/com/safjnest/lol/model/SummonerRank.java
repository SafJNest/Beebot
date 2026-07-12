package com.safjnest.lol.model;

import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;

public record SummonerRank(
    GameQueueType queue,
    TierDivisionType rank,
    int lp,
    int wins,
    int losses
) {
    public static SummonerRank unranked() {
        return new SummonerRank(GameQueueType.TEAM_BUILDER_RANKED_SOLO, TierDivisionType.UNRANKED, 0, 0, 0);
    }
}
