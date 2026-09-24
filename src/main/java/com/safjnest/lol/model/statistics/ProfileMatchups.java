package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.KdaUtils;
import com.safjnest.lol.utils.LaneTypeUtils;

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

    public Map<Integer, ProfileLeafStats> aggregateMatchups() {
        Map<Integer, ProfileLeafStats> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queues : champions.values())
            if (queues != null) for (Map<String, ProfileMatchupLeaf> positions : queues.values())
                if (positions != null) for (ProfileMatchupLeaf leaf : positions.values())
                    if (leaf != null && leaf.matchups != null) for (Map.Entry<String, ProfileLeafStats> matchup : leaf.matchups.entrySet())
                        if (matchup.getKey() != null && matchup.getValue() != null) try {
                            int champion = Integer.parseInt(matchup.getKey());
                            if (champion != 0) result.computeIfAbsent(champion, ignored -> new ProfileLeafStats()).merge(matchup.getValue());
                        } catch (NumberFormatException ignored) {}
        return result;
    }

    public ProfileMatchups withLastUpdate(long value) {
        return new ProfileMatchups(filter, timeStart, timeEnd, value, champions, metadata);
    }

    public ProfileMatchups withMinGames(int minGames) {
        if (champions == null) return this;
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
            CanonicalQueue queue = CanonicalQueue.from(match.queue);
            ProfileMatchupLeaf leaf = leaf(player.champion, queue, player.lane);
            leaf.accumulate(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
            if (!queue.arena() && player.lane != null && player.lane != LaneType.NONE && match.participants != null) {
                boolean foundOpponent = false;
                for (Participant opponent : match.participants)
                    if (opponent != null && opponent != player && opponent.champion != 0
                        && opponent.team != player.team && opponent.lane == player.lane) {
                        leaf.matchups.computeIfAbsent(String.valueOf(opponent.champion), ignored -> new ProfileLeafStats())
                            .accumulate(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
                        foundOpponent = true;
                        break;
                    }
                if (!foundOpponent) leaf.matchups.computeIfAbsent("0", ignored -> new ProfileLeafStats())
                    .accumulate(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
            }
            if (match.participants != null && (queue.arena() || LaneTypeUtils.isDuoLane(player.lane))) {
                Participant ally = null;
                for (Participant participant : match.participants) {
                    if (participant == null || participant == player || participant.team != player.team) continue;
                    boolean sameArenaTeam = queue.arena() && player.subTeam != 0 && participant.subTeam == player.subTeam;
                    boolean complementaryLane = !queue.arena() && LaneTypeUtils.isDuo(player.lane, participant.lane);
                    if (sameArenaTeam || complementaryLane) {
                        ally = participant;
                        break;
                    }
                }
                int allyChampion = ally == null ? 0 : ally.champion;
                leaf.synergies.computeIfAbsent(String.valueOf(allyChampion), ignored -> new ProfileLeafStats())
                    .accumulate(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
            }
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
        copy.matchups = filterBreakdown(source.matchups, minGames);
        copy.synergies = filterBreakdown(source.synergies, minGames);
        return copy;
    }

    private static Map<String, ProfileLeafStats> filterBreakdown(
            Map<String, ProfileLeafStats> source,
            int minGames) {
        Map<String, ProfileLeafStats> result = new LinkedHashMap<>();
        ProfileLeafStats others = null;
        if (source != null) for (Map.Entry<String, ProfileLeafStats> entry : source.entrySet()) {
            ProfileLeafStats value = entry.getValue();
            if (entry.getKey() == null || value == null) continue;
            if (!"others".equals(entry.getKey()) && value.games >= minGames) {
                ProfileLeafStats copy = new ProfileLeafStats();
                copy.merge(value);
                result.put(entry.getKey(), copy);
            } else {
                if (others == null) others = new ProfileLeafStats();
                others.merge(value);
            }
        }
        if (others != null && others.games > 0) result.put("others", others);
        return result;
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
            if (selected) result += KdaUtils.getKills(participant.kda);
        }
        return result;
    }
}
