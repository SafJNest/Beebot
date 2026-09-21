package com.safjnest.lol.utils;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;
import no.stelar7.api.r4j.pojo.lol.match.v5.LOLMatch;

public final class MatchUtils {

    private MatchUtils() {}

    public static String fullGameId(LOLMatch match) {
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

    public static boolean isRemake(LOLMatch match) {
        return match != null && match.getGameDuration() != null && match.getGameDuration() <= 330;
    }
}
