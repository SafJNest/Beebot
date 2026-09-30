package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;
import com.safjnest.lol.utils.MatchupTimelineUtils;
import com.safjnest.lol.utils.MatchupTimelineUtils.Data;
import com.safjnest.lol.utils.MatchupTimelineUtils.Snapshot;
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
            Data timeline = MatchupTimelineUtils.read(match);
            if (!queue.arena() && player.lane != null && player.lane != LaneType.NONE && match.participants != null) {
                boolean foundOpponent = false;
                for (Participant opponent : match.participants)
                    if (opponent != null && opponent != player && opponent.champion != 0
                        && opponent.team != player.team && opponent.lane == player.lane) {
                        ProfileLeafStats relation = leaf.matchups.computeIfAbsent(String.valueOf(opponent.champion), ignored -> new ProfileLeafStats());
                        relation.accumulate(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
                        addMatchupTimeline(relation, leaf, player, opponent, timeline);
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
                ProfileLeafStats relation = leaf.synergies.computeIfAbsent(String.valueOf(allyChampion), ignored -> new ProfileLeafStats());
                relation.accumulate(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
                if (ally != null && !arena) {
                    Participant enemyPlayer = null;
                    Participant enemyAlly = null;
                    for (Participant participant : match.participants) {
                        if (participant == null || participant.team == player.team) continue;
                        if (participant.lane == player.lane) enemyPlayer = participant;
                        if (participant.lane == ally.lane) enemyAlly = participant;
                    }
                    addSynergyTimeline(relation, player, ally, enemyPlayer, enemyAlly, timeline);
                }
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

    private static void addMatchupTimeline(ProfileLeafStats target, ProfileLeafStats aggregate,
                                           Participant player, Participant opponent, Data timeline) {
        Snapshot own = timeline.snapshots().get(player.puuid);
        Snapshot enemy = timeline.snapshots().get(opponent.puuid);
        if (own != null && enemy != null) {
            if (own.gold() != null && enemy.gold() != null) addMatchupMetric(target, aggregate, own.gold() - enemy.gold(), Metric.GOLD);
            if (own.cs() != null && enemy.cs() != null) addMatchupMetric(target, aggregate, own.cs() - enemy.cs(), Metric.CS);
            if (own.xp() != null && enemy.xp() != null) addMatchupMetric(target, aggregate, own.xp() - enemy.xp(), Metric.XP);
            if (own.level() != null && enemy.level() != null) addMatchupMetric(target, aggregate, own.level() - enemy.level(), Metric.LEVEL);
        }
        if (timeline.killsAvailable() && player.puuid != null && opponent.puuid != null) {
            int playerKills = timeline.kills().getOrDefault(player.puuid, 0);
            int opponentKills = timeline.kills().getOrDefault(opponent.puuid, 0);
            addMatchupMetric(target, aggregate, playerKills - opponentKills, Metric.KILL);
        }
        if (player.team != null && opponent.team != null && player.lane != null) {
            int playerPlates = MatchupTimelineUtils.plates(timeline, player.team.name(), player.lane.name());
            int opponentPlates = MatchupTimelineUtils.plates(timeline, opponent.team.name(), opponent.lane.name());
            addMatchupMetric(target, aggregate, playerPlates - opponentPlates, Metric.PLATE);
        }
    }

    private static void addSynergyTimeline(ProfileLeafStats target, Participant player, Participant ally,
                                           Participant enemyPlayer, Participant enemyAlly, Data timeline) {
        if (enemyPlayer == null || enemyAlly == null) return;
        Snapshot own = timeline.snapshots().get(player.puuid);
        Snapshot ownAlly = timeline.snapshots().get(ally.puuid);
        Snapshot enemy = timeline.snapshots().get(enemyPlayer.puuid);
        Snapshot enemyAllySnapshot = timeline.snapshots().get(enemyAlly.puuid);
        if (own != null && ownAlly != null && enemy != null && enemyAllySnapshot != null) {
            if (own.gold() != null && ownAlly.gold() != null && enemy.gold() != null && enemyAllySnapshot.gold() != null)
                add(target, own.gold() + ownAlly.gold() - enemy.gold() - enemyAllySnapshot.gold(), Metric.GOLD);
            if (own.cs() != null && ownAlly.cs() != null && enemy.cs() != null && enemyAllySnapshot.cs() != null)
                add(target, own.cs() + ownAlly.cs() - enemy.cs() - enemyAllySnapshot.cs(), Metric.CS);
            if (own.xp() != null && ownAlly.xp() != null && enemy.xp() != null && enemyAllySnapshot.xp() != null)
                add(target, own.xp() + ownAlly.xp() - enemy.xp() - enemyAllySnapshot.xp(), Metric.XP);
            if (own.level() != null && ownAlly.level() != null && enemy.level() != null && enemyAllySnapshot.level() != null)
                add(target, own.level() + ownAlly.level() - enemy.level() - enemyAllySnapshot.level(), Metric.LEVEL);
        }
        if (timeline.killsAvailable() && player.puuid != null && ally.puuid != null
                && enemyPlayer.puuid != null && enemyAlly.puuid != null) {
            int ownKills = timeline.kills().getOrDefault(player.puuid, 0) + timeline.kills().getOrDefault(ally.puuid, 0);
            int enemyKills = timeline.kills().getOrDefault(enemyPlayer.puuid, 0) + timeline.kills().getOrDefault(enemyAlly.puuid, 0);
            add(target, ownKills - enemyKills, Metric.KILL);
        }
        if (player.team != null && enemyPlayer.team != null && player.lane != null && enemyPlayer.lane != null) {
            int ownPlates = MatchupTimelineUtils.plates(timeline, player.team.name(), player.lane.name());
            int enemyPlates = MatchupTimelineUtils.plates(timeline, enemyPlayer.team.name(), enemyPlayer.lane.name());
            add(target, ownPlates - enemyPlates, Metric.PLATE);
        }
    }

    private static void add(ProfileLeafStats target, double value, Metric metric) {
        switch (metric) {
            case GOLD -> { target.goldDiffAt15Sum += value; target.goldDiffAt15Games++; }
            case CS -> { target.csDiffAt15Sum += value; target.csDiffAt15Games++; }
            case XP -> { target.xpDiffAt15Sum += value; target.xpDiffAt15Games++; }
            case KILL -> { target.killDiffAt15Sum += value; target.killDiffAt15Games++; }
            case LEVEL -> { target.levelDiffAt15Sum += value; target.levelDiffAt15Games++; }
            case PLATE -> { target.plateDiffAt15Sum += value; target.plateDiffAt15Games++; }
        }
    }

    private static void addMatchupMetric(ProfileLeafStats target, ProfileLeafStats aggregate,
                                         double value, Metric metric) {
        add(target, value, metric);
        add(aggregate, value, metric);
    }

    private enum Metric { GOLD, CS, XP, KILL, LEVEL, PLATE }
}
