package com.safjnest.lol.model.competitive;

import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.*;
import com.R4B.api.dto.statistics.shared.*;
import com.R4B.lol.GameQueueType;
import com.R4B.lol.LaneType;
import com.R4B.lol.LeagueShard;

public record CompetitiveEntry(
    String puuid,
    LeagueShard region,
    GameQueueType queue,
    long mmr,
    LaneType primary,
    Integer otpChampionId,
    long lastUpdate
) {
}
