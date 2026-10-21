package com.safjnest.lol.model.match;

import java.util.List;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public record MatchFacetQuery(
    LeagueShard shard,
    List<GameQueueType> queues,
    List<Integer> champions,
    List<LaneType> lanes,
    List<String> patches,
    long timeStart,
    long timeEnd,
    String selectedSeason,
    List<Season> seasons
) {

    public MatchFacetQuery {
        queues = queues == null ? List.of() : List.copyOf(queues);
        champions = champions == null ? List.of() : List.copyOf(champions);
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
        patches = patches == null ? List.of() : List.copyOf(patches);
        seasons = seasons == null ? List.of() : List.copyOf(seasons);
    }

    public record Season(String value, Integer year, Long timeStart, Long timeEnd) {}
}
