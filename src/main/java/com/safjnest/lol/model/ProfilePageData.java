package com.safjnest.lol.model;

import java.util.List;
import java.util.Map;

public record ProfilePageData(
    SummonerProfile profile,
    List<SummonerRank> ranks,
    ProfileStatistics statistics,
    List<ProfileMastery> masteries,
    Map<Integer, ProfileChampion> champions
) {}
