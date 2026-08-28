package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.utils.GameQueueTypeUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public record ProfileMatchups(
    Filter filter,
    long timeStart,
    long timeEnd,
    long lastUpdate,
    Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champions,
    ResponseMetadata metadata
) {

    public ProfileMatchups(
        Filter filter,
        long timeStart,
        long timeEnd,
        long lastUpdate,
        Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champions
    ) {
        this(filter, timeStart, timeEnd, lastUpdate, champions, null);
    }

    public static ProfileMatchups from(Iterable<Match> matches, String puuid, Filter filter) {
        Accumulator accumulator = accumulator(filter);
        if (matches != null) for (Match match : matches)
            if (ProfileStatistics.matchesFilter(match, puuid, filter)) accumulator.accept(match, puuid);
        return accumulator.finish();
    }

    public static Accumulator accumulator(Filter filter) {
        return new Accumulator(filter);
    }

    public boolean hasLeafMatchups() {
        return champions != null;
    }

    public Map<Integer, Stats<Void>> aggregateMatchups() {
        Map<Integer, Stats<Void>> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queues : champions.values())
            if (queues != null) for (Map<String, ProfileMatchupLeaf> positions : queues.values())
                if (positions != null) for (ProfileMatchupLeaf leaf : positions.values())
                    if (leaf != null && leaf.matchups != null) for (Map.Entry<Integer, Stats<Void>> matchup : leaf.matchups.entrySet())
                        if (matchup.getKey() != null && matchup.getValue() != null)
                            result.computeIfAbsent(matchup.getKey(), ignored -> new Stats<>()).merge(matchup.getValue());
        return result;
    }

    public ProfileMatchups withLastUpdate(long value) {
        return new ProfileMatchups(filter, timeStart, timeEnd, value, champions, metadata);
    }

    public ProfileMatchups withMinGames(int minGames) {
        Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> values = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champion : champions.entrySet()) {
            Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queues = new LinkedHashMap<>();
            for (Map.Entry<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queue : champion.getValue().entrySet()) {
                Map<String, ProfileMatchupLeaf> positions = new LinkedHashMap<>();
                for (Map.Entry<String, ProfileMatchupLeaf> position : queue.getValue().entrySet())
                    positions.put(position.getKey(), copyLeaf(position.getValue(), minGames));
                queues.put(queue.getKey(), positions);
            }
            values.put(champion.getKey(), queues);
        }
        return new ProfileMatchups(filter, timeStart, timeEnd, lastUpdate, values, metadata);
    }

    public ProfileMatchups withMetadata(ResponseMetadata value) {
        return new ProfileMatchups(filter, timeStart, timeEnd, lastUpdate, champions, value);
    }

    public static final class Accumulator {
        private final Filter filter;
        private final Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champions = new LinkedHashMap<>();
        private long oldestMatchAt;
        private long newestMatchAt;

        private Accumulator(Filter filter) {
            this.filter = filter;
        }

        public void accept(Match match, String puuid) {
            if (match == null || match.participants == null || puuid == null) return;
            for (Participant participant : match.participants)
                if (participant != null && puuid.equals(participant.puuid)) {
                    boolean arena = GameQueueTypeUtils.isCherry(match.queue);
                    accept(match, participant, kills(match, participant, arena, false),
                        arena ? 0 : kills(match, participant, false, true), arena);
                    return;
                }
        }

        public void accept(Match match, Participant player, int teamKills, int enemyTeamKills, boolean arena) {
            if (match == null || player == null) return;
            ProfileMatchupLeaf leaf = leaf(player.champion, CanonicalQueue.from(match.queue), player.lane);
            leaf.addRaw(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
            if (player.lane != null && player.lane != LaneType.NONE && match.participants != null)
                for (Participant opponent : match.participants)
                    if (opponent != null && opponent != player && opponent.champion != 0
                        && opponent.team != player.team && opponent.lane == player.lane)
                        leaf.matchups.computeIfAbsent(opponent.champion, ignored -> new Stats<>())
                            .addRaw(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
            oldestMatchAt = oldestMatchAt == 0 ? match.timeStart : Math.min(oldestMatchAt, match.timeStart);
            newestMatchAt = Math.max(newestMatchAt, match.timeEnd);
        }

        public ProfileMatchups finish() {
            long start = filter != null && filter.timeStart() != 0 ? filter.timeStart() : oldestMatchAt;
            long end = filter != null && filter.timeEnd() != 0 ? filter.timeEnd() : newestMatchAt;
            return new ProfileMatchups(filter, start, end, 0, champions);
        }

        private ProfileMatchupLeaf leaf(int champion, CanonicalQueue queue, LaneType lane) {
            return champions.computeIfAbsent(champion, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(queue, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(position(lane), ignored -> new ProfileMatchupLeaf());
        }
    }

    private static ProfileMatchupLeaf copyLeaf(ProfileMatchupLeaf source, int minGames) {
        ProfileMatchupLeaf copy = new ProfileMatchupLeaf();
        copy.merge(source);
        if (source.matchups != null) for (Map.Entry<Integer, Stats<Void>> entry : source.matchups.entrySet())
            if (entry.getValue() != null && entry.getValue().games >= minGames) {
                Stats<Void> matchup = new Stats<>();
                matchup.merge(entry.getValue());
                copy.matchups.put(entry.getKey(), matchup);
            }
        return copy;
    }

    private static String position(LaneType lane) {
        return lane == null || lane == LaneType.NONE ? "UNKNOWN" : lane.name();
    }

    private static int kills(Match match, Participant player, boolean sameArenaTeam, boolean enemyTeam) {
        int result = 0;
        if (match.participants == null) return result;
        for (Participant participant : match.participants) {
            if (participant == null) continue;
            boolean selected = sameArenaTeam ? participant.subTeam == player.subTeam
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
