package com.safjnest.spring.lol.utils;

import com.R4B.api.dto.riot.RiotMatchData;
import com.R4B.lol.*;

public final class MatchUtils {

    private MatchUtils() {}

    public static String fullGameId(RiotMatchData match) {
        return match == null || match.getPlatform() == null ? null : match.getPlatform().name() + "_" + match.getGameId();
    }

    public static LeagueShard matchShard(String matchId, LeagueShard fallback) {
        if (matchId == null || matchId.isBlank()) return fallback;
        try {
            return LeagueShard.valueOf(matchId.split("_", 2)[0]);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static boolean isRemake(RiotMatchData match) {
        return match != null && match.getGameDuration() != null && match.getGameDuration() <= 330;
    }
}
