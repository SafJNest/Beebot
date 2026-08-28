package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.ProfileActivity;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.ProfileStatistics;
import com.safjnest.lol.utils.GameQueueTypeUtils;

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

    public static final class MatchupsAccumulator {
        private final String puuid;
        private final Filter filter;
        private final ProfileMatchups.Accumulator matchups;

        private MatchupsAccumulator(String puuid, Filter filter) {
            this.puuid = puuid;
            this.filter = filter;
            matchups = ProfileMatchups.accumulator(filter);
        }

        public void accept(Match match) {
            ProfileMatchContext context = ProfileMatchContext.from(match, puuid, filter);
            if (!context.inCurrentSplit()) return;
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
        private final ProfileActivity.Accumulator activity;
        private final ProfileMatchups.Accumulator matchups;

        private ProfileRefreshAccumulator(
            String puuid,
            Filter statisticsFilter,
            Filter activityFilter,
            Filter matchupsFilter
        ) {
            this.puuid = puuid;
            this.statisticsFilter = statisticsFilter;
            statistics = new ProfileStatistics(statisticsFilter == null ? 0 : statisticsFilter.timeStart());
            activity = ProfileActivity.accumulator(puuid, activityFilter);
            matchups = ProfileMatchups.accumulator(matchupsFilter);
        }

        public void accept(Match match) {
            ProfileMatchContext context = ProfileMatchContext.from(match, puuid, statisticsFilter);
            activity.accept(match, context.player());
            if (!context.inCurrentSplit()) return;
            statistics.addRaw(match, context.player(), context.teamKills(), context.enemyTeamKills(), context.arena());
            matchups.accept(match, context.player(), context.teamKills(), context.enemyTeamKills(), context.arena());
        }

        public ProfileRefresh finish() {
            statistics.finish();
            return new ProfileRefresh(
                statistics,
                activity.finish(),
                matchups.finish().withLastUpdate(System.currentTimeMillis())
            );
        }
    }

    public record ProfileRefresh(
        ProfileStatistics statistics,
        ProfileActivity activity,
        ProfileMatchups matchups
    ) {}

    private record ProfileMatchContext(
        Match match,
        Participant player,
        boolean inCurrentSplit,
        boolean arena,
        int teamKills,
        int enemyTeamKills
    ) {

        private static ProfileMatchContext from(Match match, String puuid, Filter filter) {
            Participant player = participant(match, puuid);
            if (player == null || !ProfileStatistics.matchesFilter(match, player, filter))
                return new ProfileMatchContext(match, player, false, false, 0, 0);
            boolean arena = GameQueueTypeUtils.isCherry(match.queue);
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
            if (selected) result += kills(participant.kda);
        }
        return result;
    }

    private static int kills(String kda) {
        if (kda == null || kda.isBlank()) return 0;
        try { return Integer.parseInt(kda.split("/", 2)[0]); }
        catch (RuntimeException ignored) { return 0; }
    }
}
