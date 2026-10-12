package com.R4B.api.dto;

import com.R4B.api.dto.*;
import com.R4B.lol.LaneType;

public record ChampionIndexable(
    int champion,
    LaneType role,
    int games,
    boolean indexable,
    long lastUpdate
) {}
