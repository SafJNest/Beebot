package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    public Map<Integer, Map<CanonicalQueue, Map<String, Stats<Void>>>> champions = new LinkedHashMap<>();
    public Map<String, Long> pings = new LinkedHashMap<>();
    public Map<Integer, Long> spellOne = new LinkedHashMap<>();
    public Map<Integer, Long> spellTwo = new LinkedHashMap<>();
    @JsonIgnore public Stats<Void> total = new Stats<>();
    @JsonIgnore public java.util.List<Stats<GameQueueType>> queueStats = new java.util.ArrayList<>();
    @JsonIgnore public java.util.List<Stats<LaneType>> laneStats = new java.util.ArrayList<>();
    @JsonIgnore public java.util.List<Stats<Integer>> championStats = new java.util.ArrayList<>();

    public ProfileStatistics() {}

    public ProfileStatistics(long timeStart) {
        this.timeStart = timeStart;
        timeEnd = timeStart;
    }

    @JsonIgnore
    public boolean hasLeafStatistics() {
        finish();
        return champions != null;
    }

    public void add(MatchResult match, GameQueueType queue, LaneType lane) {
        if (match == null || queue == null) return;
        Participant participant = new Participant();
        participant.win = match.win();
        participant.kda = match.kda();
        participant.champion = match.championId();
        participant.lane = lane;
        participant.damage = match.damage();
        participant.visionScore = match.vision();
        participant.cs = match.cs();
        participant.goldEarned = match.gold();
        leaf(participant.champion, CanonicalQueue.from(queue), lane).add(participant,
            match.timeStart(), match.timeEnd(), match.teamKills(), 0, false);
        updateTime(match.timeStart(), match.timeEnd());
        finish();
    }

    public void add(Match match, String puuid, Filter filter) {
        add(match, puuid, filter, true);
    }

    public void accumulate(Match match, String puuid, Filter filter) {
        add(match, puuid, filter, false);
    }

    public void accumulate(Match match, Participant player, int teamKills, int enemyTeamKills, boolean arena) {
        if (match != null && player != null) add(match, player, teamKills, enemyTeamKills, arena, false);
    }

    public void finish() {
        total = total();
        queueStats = new java.util.ArrayList<>();
        for (Map.Entry<CanonicalQueue, Stats<Void>> entry : queueStats().entrySet()) {
            Stats<GameQueueType> stat = new Stats<>(legacyQueue(entry.getKey()));
            stat.merge(entry.getValue());
            queueStats.add(stat);
        }
        laneStats = new java.util.ArrayList<>();
        for (Map.Entry<String, Stats<Void>> entry : laneStats().entrySet()) {
            LaneType lane;
            try { lane = LaneType.valueOf(entry.getKey()); }
            catch (IllegalArgumentException ignored) { lane = LaneType.NONE; }
            Stats<LaneType> stat = new Stats<>(lane);
            stat.merge(entry.getValue());
            laneStats.add(stat);
        }
        championStats = new java.util.ArrayList<>();
        for (Map.Entry<Integer, Stats<Void>> entry : championStats().entrySet()) {
            Stats<Integer> stat = new Stats<>(entry.getKey());
            stat.merge(entry.getValue());
            championStats.add(stat);
        }
    }

    @JsonIgnore
    public Stats<Void> total() {
        Stats<Void> total = new Stats<>();
        forEachLeaf(total::merge);
        return total;
    }

    @JsonIgnore
    public Map<Integer, Stats<Void>> championStats() {
        Map<Integer, Stats<Void>> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map.Entry<Integer, Map<CanonicalQueue, Map<String, Stats<Void>>>> champion : champions.entrySet()) {
            Stats<Void> total = new Stats<>();
            forEachQueueLeaf(champion.getValue(), total::merge);
            result.put(champion.getKey(), total);
        }
        return result;
    }

    @JsonIgnore
    public Map<CanonicalQueue, Stats<Void>> queueStats() {
        Map<CanonicalQueue, Stats<Void>> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map<CanonicalQueue, Map<String, Stats<Void>>> queues : champions.values()) if (queues != null)
            for (Map.Entry<CanonicalQueue, Map<String, Stats<Void>>> queue : queues.entrySet()) {
                Stats<Void> total = result.computeIfAbsent(queue.getKey(), ignored -> new Stats<>());
                forEachLeaf(queue.getValue(), total::merge);
            }
        return result;
    }

    @JsonIgnore
    public Map<String, Stats<Void>> laneStats() {
        Map<String, Stats<Void>> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map<CanonicalQueue, Map<String, Stats<Void>>> queues : champions.values()) if (queues != null)
            for (Map<String, Stats<Void>> lanes : queues.values()) if (lanes != null)
                for (Map.Entry<String, Stats<Void>> lane : lanes.entrySet())
                    result.computeIfAbsent(lane.getKey(), ignored -> new Stats<>()).merge(lane.getValue());
        return result;
    }

    private void add(Match match, String puuid, Filter filter, boolean calculate) {
        if (match == null || puuid == null || puuid.isBlank() || match.participants == null) return;
        Participant player = participant(match, puuid);
        if (player == null || !matchesFilter(match, player, filter)) return;
        boolean arena = GameQueueTypeUtils.isCherry(match.queue);
        add(match, player, kills(match, player, arena, false), arena ? 0 : kills(match, player, false, true), arena, calculate);
    }

    private void add(Match match, Participant player, int teamKills, int enemyTeamKills, boolean arena, boolean calculate) {
        Stats<Void> leaf = leaf(player.champion, CanonicalQueue.from(match.queue), player.lane);
        if (calculate) leaf.add(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
        else leaf.accumulate(player, match.timeStart, match.timeEnd, teamKills, enemyTeamKills, arena);
        if (player.pings != null) for (Map.Entry<String, Integer> entry : player.pings.entrySet())
            if (entry.getKey() != null && entry.getValue() != null) pings.merge(entry.getKey(), entry.getValue().longValue(), Long::sum);
        if (player.summonerSpell1 != 0) spellOne.merge(player.summonerSpell1, 1L, Long::sum);
        if (player.summonerSpell2 != 0) spellTwo.merge(player.summonerSpell2, 1L, Long::sum);
        updateTime(match.timeStart, match.timeEnd);
    }

    private Stats<Void> leaf(int champion, CanonicalQueue queue, LaneType lane) {
        return champions.computeIfAbsent(champion, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(queue, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(laneKey(lane), ignored -> new Stats<>());
    }

    private static GameQueueType legacyQueue(CanonicalQueue queue) {
        return switch (queue) {
            case RANKED_SOLO -> GameQueueType.RANKED_SOLO_5X5;
            case RANKED_FLEX -> GameQueueType.RANKED_FLEX_SR;
            case NORMAL_DRAFT -> GameQueueType.TEAM_BUILDER_DRAFT_UNRANKED_5X5;
            case NORMAL_BLIND -> GameQueueType.NORMAL_5V5_BLIND_PICK;
            case ARAM -> GameQueueType.ARAM;
            case ARENA -> GameQueueType.CHERRY;
            case SWIFTPLAY -> GameQueueType.SWIFTPLAY;
            case URF -> GameQueueType.URF;
            case ULTBOOK -> GameQueueType.ULTBOOK;
            case NEXUS_BLITZ -> GameQueueType.NEXUS_BLITZ;
            case SWARM -> GameQueueType.STRAWBERRY;
            case SPECIAL -> GameQueueType.ONEFORALL_5X5;
            case OTHER -> GameQueueType.CUSTOM;
        };
    }

    private static String laneKey(LaneType lane) {
        return lane == null || lane == LaneType.NONE ? "UNKNOWN" : lane.name();
    }

    private void updateTime(long start, long end) {
        timeEnd = Math.max(timeEnd, end);
        oldestMatchAt = oldestMatchAt == 0 ? start : Math.min(oldestMatchAt, start);
        newestMatchAt = Math.max(newestMatchAt, start);
    }

    private void forEachLeaf(Consumer<Stats<?>> consumer) {
        if (champions != null) for (Map<CanonicalQueue, Map<String, Stats<Void>>> queues : champions.values()) forEachQueueLeaf(queues, consumer);
    }

    private static void forEachQueueLeaf(Map<CanonicalQueue, Map<String, Stats<Void>>> queues, Consumer<Stats<?>> consumer) {
        if (queues != null) for (Map<String, Stats<Void>> lanes : queues.values()) forEachLeaf(lanes, consumer);
    }

    private static void forEachLeaf(Map<String, Stats<Void>> lanes, Consumer<Stats<?>> consumer) {
        if (lanes != null) for (Stats<Void> stats : lanes.values()) if (stats != null) consumer.accept(stats);
    }

    private static Participant participant(Match match, String puuid) {
        for (Participant participant : match.participants) if (participant != null && puuid.equals(participant.puuid)) return participant;
        return null;
    }

    private static int kills(Match match, Participant player, boolean sameArenaTeam, boolean enemyTeam) {
        int result = 0;
        for (Participant participant : match.participants) {
            if (participant == null) continue;
            boolean selected = sameArenaTeam ? participant.subTeam == player.subTeam
                : enemyTeam ? participant.team != player.team : participant.team == player.team;
            if (selected) result += kills(participant.kda);
        }
        return result;
    }

    public static boolean matchesFilter(Match match, String puuid, Filter filter) {
        return match != null && puuid != null && !puuid.isBlank() && matchesFilter(match, participant(match, puuid), filter);
    }

    public static boolean matchesFilter(Match match, Participant player, Filter filter) {
        if (match == null || player == null) return false;
        if (filter == null) return true;
        if (filter.queue() != null && filter.queue() != match.queue) return false;
        if (filter.region() != null && filter.region() != match.leagueShard) return false;
        if (filter.champion() != 0 && filter.champion() != player.champion) return false;
        if (filter.lane() != null && filter.lane() != player.lane) return false;
        if (filter.patch() != null && !matchesPatch(match.patch, filter.patch())) return false;
        if (!matchesRank(match.rank, filter)) return false;
        if (filter.opponent() != 0 && !hasOpponent(match, player, filter.opponent())) return false;
        if (filter.duo() != 0 && !hasDuo(match, player, filter.duo())) return false;
        return (filter.timeStart() == 0 || match.timeStart >= filter.timeStart())
            && (filter.timeEnd() == 0 || match.timeEnd <= filter.timeEnd());
    }

    private static boolean matchesRank(TierType rank, Filter filter) {
        if (filter.rank() == null) return true;
        return rank != null && (filter.rankBehavior() == Filter.RankBehavior.EXACT
            ? rank == filter.rank() : rank.ordinal() <= filter.rank().ordinal());
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
