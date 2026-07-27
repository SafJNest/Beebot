package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public class ProfileActivityService {

    public ProfileActivity get(LeagueShard shard, String puuid, Filter filter) {
        if (shard == null || puuid == null || puuid.isBlank() || filter == null)
            return ProfileActivity.from(List.of(), puuid, filter);
        List<Match> matches = MongoDB.findProfileStatisticsMatches(puuid, shard, filter, 0, 0);
        return ProfileActivity.from(matches, puuid, filter);
    }

    // ============================================================================
}
