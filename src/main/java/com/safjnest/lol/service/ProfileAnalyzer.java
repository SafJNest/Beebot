package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.ProfileStatistics;

public final class ProfileAnalyzer {

    private ProfileAnalyzer() {}

    public static ProfileStatistics updateStatistics(
        ProfileStatistics statistics,
        List<Match> matches,
        String puuid,
        Filter filter
    ) {
        if (statistics == null) statistics = new ProfileStatistics(filter.timeStart());
        if (matches == null) return statistics;
        for (Match match : matches) statistics.add(match, puuid, filter);
        return statistics;
    }

    public static ProfileActivity activity(List<Match> matches, String puuid, Filter filter) {
        return ProfileActivity.from(matches == null ? List.of() : matches, puuid, filter);
    }

    public static ProfileMatchups matchups(List<Match> matches, String puuid, Filter filter) {
        return ProfileMatchups.from(matches == null ? List.of() : matches, puuid, filter)
            .withLastUpdate(System.currentTimeMillis());
    }
}
