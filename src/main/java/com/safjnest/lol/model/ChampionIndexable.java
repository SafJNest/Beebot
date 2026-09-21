package com.safjnest.lol.model;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public record ChampionIndexable(
    int champion,
    LaneType role,
    int games,
    boolean indexable,
    long lastUpdate
) {}
