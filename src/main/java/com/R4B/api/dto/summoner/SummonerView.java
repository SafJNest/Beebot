package com.R4B.api.dto.summoner;

import java.util.Map;

import com.R4B.api.dto.*;
import com.R4B.lol.GameQueueType;
import com.fasterxml.jackson.annotation.JsonInclude;

public record SummonerView(
    Summoner summoner,
    Map<GameQueueType, Rank> ranks,
    SummonerOverview overview,
    @JsonInclude(JsonInclude.Include.NON_NULL) ResponseMetadata metadata
) {

    public SummonerView(Summoner summoner, Map<GameQueueType, Rank> ranks, SummonerOverview overview) {
        this(summoner, ranks, overview, null);
    }

    public SummonerView withMetadata(ResponseMetadata value) {
        return new SummonerView(summoner, ranks, overview, value);
    }
}
