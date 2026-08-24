package com.safjnest.lol.utils;

public final class MatchTrackingUtils {

    private MatchTrackingUtils() {}

    public static boolean hasTrackedRank(boolean tracked, boolean hasParticipantRank) {
        return tracked || hasParticipantRank;
    }
}
