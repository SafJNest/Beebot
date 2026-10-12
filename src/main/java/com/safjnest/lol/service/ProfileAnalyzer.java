package com.safjnest.lol.service;

import java.util.List;

import com.R4B.api.dto.Filter;
import com.R4B.api.dto.match.Match;
import com.R4B.api.dto.match.Participant;
import com.R4B.api.dto.statistics.ProfileActivity;
import com.R4B.api.dto.statistics.ProfileMatchups;
import com.R4B.api.dto.statistics.ProfileStatistics;
import com.R4B.lol.*;

public final class ProfileAnalyzer {

    public static final class MatchupsAccumulator {
        private final String puuid;
        private final Filter filter;
        private final ProfileMatchupsAnalyzer.Accumulator matchups;

        private MatchupsAccumulator(String puuid, Filter filter) {
            this.puuid = puuid;
            this.filter = filter;
            matchups = ProfileMatchupsAnalyzer.accumulator(filter);
        }

        public void accept(Match match) {
            ProfileMatchContext context = ProfileMatchContext.from(match, puuid, filter);
            if (!context.isCanonical()) return;
            matchups.accept(match, context.player(), context.teamKills(), context.enemyTeamKills(), context.arena());
        }

        public ProfileMatchups finish() {
            return matchups.finish().withLastUpdate(System.currentTimeMillis());
        }

    }

    public static final class ProfileRefreshAccumulator {
        private final String puuid;
        private final Filter statisticsFilter;
        private final ProfileStatistics statistics;
        private final ProfileActivityAnalyzer.Accumulator activity;
        private final ProfileMatchupsAnalyzer.Accumulator matchups;

        private ProfileRefreshAccumulator(
            String puuid,
            Filter statisticsFilter,
            Filter activityFilter,
            Filter matchupsFilter
        ) {
            this.puuid = puuid;
            this.statisticsFilter = statisticsFilter;
            statistics = new ProfileStatistics(statisticsFilter == null ? 0 : statisticsFilter.timeStart());
            activity = ProfileActivityAnalyzer.accumulator(puuid, activityFilter);
            matchups = ProfileMatchupsAnalyzer.accumulator(matchupsFilter);
        }

        public void accept(Match match) {
            ProfileMatchContext context = ProfileMatchContext.from(match, puuid, statisticsFilter);
            activity.accept(match, context.player());
            if (!context.isCanonical()) return;
            ProfileStatisticsAccumulator.of(statistics).accumulate(match, context.player(), context.teamKills(), context.enemyTeamKills(), context.arena());
            matchups.accept(match, context.player(), context.teamKills(), context.enemyTeamKills(), context.arena());
        }

        public ProfileRefresh finish() {
            ProfileStatisticsAccumulator.of(statistics).finish();
            return new ProfileRefresh(
                statistics,
                activity.finish(),
                matchups.finish().withLastUpdate(System.currentTimeMillis())
            );
        }
    }

    private ProfileAnalyzer() {}

    public static ProfileActivity activity(List<Match> matches, String puuid, Filter filter) {
        return ProfileActivityAnalyzer.from(matches == null ? List.of() : matches, puuid, filter);
    }

    public static ProfileMatchups matchups(List<Match> matches, String puuid, Filter filter) {
        return ProfileMatchupsAnalyzer.from(matches == null ? List.of() : matches, puuid, filter)
            .withLastUpdate(System.currentTimeMillis());
    }

    public static ProfileRefreshAccumulator refreshAccumulator(
        String puuid,
        Filter statisticsFilter,
        Filter activityFilter,
        Filter matchupsFilter
    ) {
        return new ProfileRefreshAccumulator(puuid, statisticsFilter, activityFilter, matchupsFilter);
    }

    public static MatchupsAccumulator matchupsAccumulator(String puuid, Filter filter) {
        return new MatchupsAccumulator(puuid, filter);
    }

    public record ProfileRefresh(
        ProfileStatistics statistics,
        ProfileActivity activity,
        ProfileMatchups matchups
    ) {}

    // ============================================================================

    private record ProfileMatchContext(
        Match match,
        Participant player,
        boolean isCanonical,
        boolean arena,
        int teamKills,
        int enemyTeamKills
    ) {

        private static ProfileMatchContext from(Match match, String puuid, Filter filter) {
            Participant player = participant(match, puuid);
            if (player == null || !ProfileStatisticsAccumulator.matchesFilter(match, player, filter))
                return new ProfileMatchContext(match, player, false, false, 0, 0);
            boolean arena = GameQueueType.isCherry(match.queue);
            int teamKills = kills(match, player, arena, false);
            int enemyTeamKills = arena ? 0 : kills(match, player, false, true);
            return new ProfileMatchContext(match, player, true, arena, teamKills, enemyTeamKills);
        }
    }

    private static Participant participant(Match match, String puuid) {
        if (match == null || match.participants == null || puuid == null) return null;
        for (Participant participant : match.participants)
            if (participant != null && puuid.equals(participant.puuid)) return participant;
        return null;
    }

    private static int kills(Match match, Participant player, boolean sameArenaTeam, boolean enemyTeam) {
        int result = 0;
        if (match == null || match.participants == null) return result;
        for (Participant participant : match.participants) {
            if (participant == null) continue;
            boolean selected = sameArenaTeam
                ? participant.subTeam == player.subTeam
                : enemyTeam ? participant.team != player.team : participant.team == player.team;
            if (selected) result += KdaUtils.getKills(participant.kda);
        }
        return result;
    }
}
