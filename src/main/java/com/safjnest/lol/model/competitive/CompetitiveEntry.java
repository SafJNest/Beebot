package com.safjnest.lol.model.competitive;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public record CompetitiveEntry(
    String puuid,
    LeagueShard region,
    GameQueueType queue,
    long mmr,
    LaneType primary,
    long lastUpdate
) {

    public String id() {
        return puuid + ':' + queue.name();
    }
}
