package com.R4B.api.dto.summoner;

import java.util.List;
import java.util.Map;

import com.R4B.api.dto.*;
import com.R4B.api.dto.statistics.ProfileStatistics;

public record SummonerOverview(
    ProfileStatistics statistics,
    List<Mastery> masteries,
    Map<Integer, Champion> champions
) {
    public record Champion(
        String name,
        String image
    ) {}
}
