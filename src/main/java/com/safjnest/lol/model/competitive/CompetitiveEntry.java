package com.safjnest.lol.model.competitive;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierDivisionType;

public record CompetitiveEntry(
    String puuid,
    LeagueShard region,
    GameQueueType queue,
    TierDivisionType tier,
    long mmr,
    LaneType primary,
    Integer otpChampionId,
    long lastUpdate
) {
}
