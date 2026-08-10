package com.safjnest.lol.model.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public record ProfileMatchups(
    Filter filter,
    long timeStart,
    long timeEnd,
    long lastUpdate,
    List<Champion> champions,
    ResponseMetadata metadata
) {

    public ProfileMatchups(Filter filter, long timeStart, long timeEnd, long lastUpdate, List<Champion> champions) {
        this(filter, timeStart, timeEnd, lastUpdate, champions, null);
    }

    public static ProfileMatchups from(List<Match> matches, String puuid, Filter filter) {
        ProfileStatistics statistics = new ProfileStatistics(filter != null ? filter.timeStart() : 0);
        Map<Integer, Map<Integer, Stats<Integer>>> matchupStats = new LinkedHashMap<>();

        if (matches != null) for (Match match : matches) {
            if (!ProfileStatistics.matchesFilter(match, puuid, filter)) continue;
            statistics.add(match, puuid, filter);

            Participant player = participant(match, puuid);
            if (player == null || player.lane == null || player.lane == LaneType.NONE) continue;

            boolean arena = com.safjnest.lol.utils.GameQueueTypeUtils.isCherry(match.queue);
            int teamKills = kills(match, player, arena, false);
            int enemyTeamKills = arena ? 0 : kills(match, player, false, true);
            Map<Integer, Stats<Integer>> opponents = matchupStats.computeIfAbsent(player.champion, ignored -> new LinkedHashMap<>());
            for (Participant opponent : match.participants) {
                if (opponent == null || opponent == player || opponent.champion == 0) continue;
                if (opponent.team != player.team && opponent.lane == player.lane) {
                    Stats<Integer> stats = opponents.computeIfAbsent(opponent.champion, Stats::new);
                    stats.add(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
                }
            }
        }

        List<Stats<Integer>> championStats = new ArrayList<>(statistics.championStats);
        championStats.sort(Comparator.comparingLong((Stats<Integer> value) -> value.games).reversed()
            .thenComparingInt(value -> value.reference));
        List<Champion> champions = new ArrayList<>(championStats.size());
        for (Stats<Integer> champion : championStats) {
            List<Matchup> matchups = new ArrayList<>();
            Map<Integer, Stats<Integer>> opponents = matchupStats.get(champion.reference);
            if (opponents != null) for (Stats<Integer> opponent : opponents.values())
                matchups.add(new Matchup(opponent.reference, opponent));
            matchups.sort(Comparator.comparingLong((Matchup value) -> value.stats.games).reversed()
                .thenComparingInt(Matchup::champion));
            champions.add(new Champion(champion.reference, champion, List.copyOf(matchups)));
        }

        return new ProfileMatchups(
            filter,
            filter != null ? filter.timeStart() : 0,
            statistics.timeEnd,
            0,
            List.copyOf(champions)
        );
    }

    public ProfileMatchups withLastUpdate(long value) {
        return new ProfileMatchups(filter, timeStart, timeEnd, value, champions, metadata);
    }

    public ProfileMatchups withMinGames(int minGames) {
        List<Champion> filtered = new ArrayList<>(champions.size());
        for (Champion champion : champions) {
            List<Matchup> matchups = new ArrayList<>();
            for (Matchup matchup : champion.matchups)
                if (matchup.stats.games >= minGames) matchups.add(matchup);
            filtered.add(new Champion(champion.champion, champion.stats, List.copyOf(matchups)));
        }
        return new ProfileMatchups(filter, timeStart, timeEnd, lastUpdate, List.copyOf(filtered), metadata);
    }

    public ProfileMatchups withMetadata(ResponseMetadata value) {
        return new ProfileMatchups(filter, timeStart, timeEnd, lastUpdate, champions, value);
    }

    public record Champion(
        int champion,
        Stats<Integer> stats,
        List<Matchup> matchups
    ) {}

    public record Matchup(
        int champion,
        Stats<Integer> stats
    ) {}

    private static Participant participant(Match match, String puuid) {
        if (match == null || match.participants == null || puuid == null) return null;
        for (Participant participant : match.participants)
            if (participant != null && puuid.equals(participant.puuid)) return participant;
        return null;
    }

    private static int kills(Match match, Participant player, boolean sameArenaTeam, boolean enemyTeam) {
        int result = 0;
        if (match.participants == null) return result;
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
