package com.safjnest.lol.model.statistics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.utils.GameQueueTypeUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ProfileStatistics {
    public long timeStart;
    public long timeEnd;
    public long lastUpdate;
    public long oldestMatchAt;
    public long newestMatchAt;
    public Stats<Void> total = new Stats<>();
    public List<Stats<GameQueueType>> queueStats = new ArrayList<>();
    public List<Stats<LaneType>> laneStats = new ArrayList<>();
    public List<Stats<Integer>> championStats = new ArrayList<>();
    public Map<Integer, Stats<Integer>> matchups = new LinkedHashMap<>();
    public Map<Integer, Stats<Integer>> duoStats = new LinkedHashMap<>();
    public Map<String, Long> pings = new LinkedHashMap<>();
    public Map<Integer, Long> spellOne = new LinkedHashMap<>();
    public Map<Integer, Long> spellTwo = new LinkedHashMap<>();

    public ProfileStatistics() {}

    public ProfileStatistics(long timeStart) {
        this.timeStart = timeStart;
        this.timeEnd = timeStart;
    }

    public void add(MatchResult match, GameQueueType queue, LaneType lane) {
        if (match == null || queue == null) return;
        total.add(match);
        stat(queueStats, queue).add(match);
        if (lane != null && lane != LaneType.NONE) stat(laneStats, lane).add(match);
        stat(championStats, match.championId()).add(match);
        timeEnd = Math.max(timeEnd, match.timeEnd());
        oldestMatchAt = oldestMatchAt == 0 ? match.timeStart() : Math.min(oldestMatchAt, match.timeStart());
        newestMatchAt = Math.max(newestMatchAt, match.timeStart());
    }

    public void add(Match match, String puuid, Filter filter) {
        if (match == null || puuid == null || puuid.isBlank() || match.participants == null) return;
        Participant player = participant(match, puuid);
        if (player == null || !matchesFilter(match, player, filter)) return;

        boolean arena = GameQueueTypeUtils.isCherry(match.queue);
        int teamKills = kills(match, player, arena, false);
        int enemyTeamKills = arena ? 0 : kills(match, player, false, true);
        add(total, match, player, teamKills, enemyTeamKills, arena);
        if (match.queue != null) add(stat(queueStats, match.queue), match, player, teamKills, enemyTeamKills, arena);
        if (player.lane != null && player.lane != LaneType.NONE)
            add(stat(laneStats, player.lane), match, player, teamKills, enemyTeamKills, arena);
        add(stat(championStats, player.champion), match, player, teamKills, enemyTeamKills, arena);

        if (player.pings != null) for (Map.Entry<String, Integer> entry : player.pings.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) pings.merge(entry.getKey(), entry.getValue().longValue(), Long::sum);
        }
        if (player.summonerSpell1 != 0) spellOne.merge(player.summonerSpell1, 1L, Long::sum);
        if (player.summonerSpell2 != 0) spellTwo.merge(player.summonerSpell2, 1L, Long::sum);

        if (player.lane != null && player.lane != LaneType.NONE) {
            for (Participant opponent : match.participants) {
                if (opponent == null || opponent == player || opponent.champion == 0) continue;
                if (opponent.team != player.team && opponent.lane == player.lane)
                    add(matchups.computeIfAbsent(opponent.champion, Stats::new), match, player, teamKills, enemyTeamKills, arena);
            }
        }

        for (Participant duo : match.participants) {
            if (duo == null || duo == player || duo.champion == 0 || duo.team != player.team) continue;
            boolean sameDuo = arena
                ? duo.subTeam == player.subTeam
                : (duo.lane == LaneType.BOT || duo.lane == LaneType.UTILITY)
                    && (player.lane == LaneType.BOT || player.lane == LaneType.UTILITY);
            if (sameDuo) add(duoStats.computeIfAbsent(duo.champion, Stats::new), match, player, teamKills, enemyTeamKills, arena);
        }

        timeEnd = Math.max(timeEnd, match.timeEnd);
        oldestMatchAt = oldestMatchAt == 0 ? match.timeStart : Math.min(oldestMatchAt, match.timeStart);
        newestMatchAt = Math.max(newestMatchAt, match.timeStart);
    }

    private static void add(Stats<?> stats, Match match, Participant player, int teamKills, int enemyTeamKills, boolean arena) {
        stats.add(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
    }

    private static <T> Stats<T> stat(List<Stats<T>> stats, T reference) {
        for (Stats<T> value : stats) if (Objects.equals(value.reference, reference)) return value;
        Stats<T> value = new Stats<>(reference);
        stats.add(value);
        return value;
    }

    private static Participant participant(Match match, String puuid) {
        for (Participant participant : match.participants)
            if (participant != null && puuid.equals(participant.puuid)) return participant;
        return null;
    }

    private static int kills(Match match, Participant player, boolean sameArenaTeam, boolean enemyTeam) {
        int result = 0;
        for (Participant participant : match.participants) {
            if (participant == null) continue;
            boolean selected = sameArenaTeam
                ? participant.subTeam == player.subTeam
                : enemyTeam ? participant.team != player.team : participant.team == player.team;
            if (selected) result += kills(participant.kda);
        }
        return result;
    }

    public static boolean matchesFilter(Match match, String puuid, Filter filter) {
        if (match == null || puuid == null || puuid.isBlank()) return false;
        Participant player = participant(match, puuid);
        return player != null && matchesFilter(match, player, filter);
    }

    private static boolean matchesFilter(Match match, Participant player, Filter filter) {
        if (filter == null) return true;
        if (filter.queue() != null && filter.queue() != match.queue) return false;
        if (filter.region() != null && filter.region() != match.leagueShard) return false;
        if (filter.champion() != 0 && filter.champion() != player.champion) return false;
        if (filter.lane() != null && filter.lane() != player.lane) return false;
        if (filter.patch() != null && !matchesPatch(match.patch, filter.patch())) return false;
        if (!matchesRank(match.rank, filter)) return false;
        if (filter.opponent() != 0 && !hasOpponent(match, player, filter.opponent())) return false;
        if (filter.duo() != 0 && !hasDuo(match, player, filter.duo())) return false;
        return inPeriod(match, filter);
    }

    private static boolean matchesRank(TierType rank, Filter filter) {
        if (filter.rank() == null) return true;
        if (rank == null) return false;
        return filter.rankBehavior() == Filter.RankBehavior.EXACT
            ? rank == filter.rank()
            : rank.ordinal() <= filter.rank().ordinal();
    }

    private static boolean inPeriod(Match match, Filter filter) {
        return (filter.timeStart() == 0 || match.timeStart >= filter.timeStart())
            && (filter.timeEnd() == 0 || match.timeEnd <= filter.timeEnd());
    }

    private static boolean matchesPatch(String patch, String filterPatch) {
        return patch != null && (patch.equals(filterPatch) || patch.startsWith(filterPatch + "."));
    }

    private static boolean hasOpponent(Match match, Participant player, int champion) {
        for (Participant participant : match.participants)
            if (participant != null && participant.team != player.team && participant.lane == player.lane && participant.champion == champion) return true;
        return false;
    }

    private static boolean hasDuo(Match match, Participant player, int champion) {
        boolean arena = GameQueueTypeUtils.isCherry(match.queue);
        for (Participant participant : match.participants) {
            if (participant == null || participant == player || participant.team != player.team || participant.champion != champion) continue;
            if (arena && participant.subTeam == player.subTeam) return true;
            if (!arena && (participant.lane == LaneType.BOT || participant.lane == LaneType.UTILITY)
                && (player.lane == LaneType.BOT || player.lane == LaneType.UTILITY)) return true;
        }
        return false;
    }

    private static int kills(String kda) {
        if (kda == null || kda.isBlank()) return 0;
        try { return Integer.parseInt(kda.split("/", 2)[0]); }
        catch (RuntimeException ignored) { return 0; }
    }
}
