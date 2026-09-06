package com.safjnest.lol.model.statistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.match.Match;
import com.safjnest.lol.model.match.MatchResult;
import com.safjnest.lol.model.match.Participant;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;
import com.safjnest.lol.utils.GameQueueTypeUtils;
import com.safjnest.lol.utils.OPTUtils;

import no.stelar7.api.r4j.basic.constants.types.lol.GameQueueType;
import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;
import no.stelar7.api.r4j.basic.constants.types.lol.TierType;

public class ProfileStatistics {
    public long timeStart;
    public long timeEnd;
    public long lastUpdate;
    public long oldestMatchAt;
    public long newestMatchAt;
    public Map<Integer, Map<CanonicalQueue, Map<String, ProfileLeafStats>>> champions = new LinkedHashMap<>();
    public Map<String, Long> pings = new LinkedHashMap<>();
    public Map<Integer, Long> spellOne = new LinkedHashMap<>();
    public Map<Integer, Long> spellTwo = new LinkedHashMap<>();
    @JsonIgnore public ProfileLeafStats total = new ProfileLeafStats();
    @JsonIgnore public java.util.List<ProfileLeafStats> queueStats = new java.util.ArrayList<>();
    @JsonIgnore public java.util.List<ProfileLeafStats> laneStats = new java.util.ArrayList<>();
    @JsonIgnore public java.util.List<ProfileLeafStats> championStats = new java.util.ArrayList<>();

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
        ProfileLeafStats leaf = leaf(participant.champion, CanonicalQueue.from(queue), lane);
        leaf.games++;
        if (participant.win) leaf.wins++;
        int[] kda = kda(participant.kda);
        leaf.kills += kda[0]; leaf.deaths += kda[1]; leaf.assists += kda[2];
        leaf.damage += participant.damage;
        leaf.cs += participant.cs;
        leaf.gold += participant.goldEarned;
        leaf.vision += participant.visionScore;
        leaf.playtime += Math.max(0, match.timeEnd() - match.timeStart());
        leaf.lastPlayedAt = Math.max(leaf.lastPlayedAt, match.timeStart());
        if (match.teamKills() > 0) leaf.killParticipationSum += ((double)(kda[0]+kda[2])/match.teamKills())*100;
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
        OPTUtils.refresh(champions);
        total = total();
        queueStats = new java.util.ArrayList<>();
        for (Map.Entry<CanonicalQueue, ProfileLeafStats> e : queueStats().entrySet()) {
            e.getValue().reference = e.getKey();
            queueStats.add(e.getValue());
        }
        laneStats = new java.util.ArrayList<>();
        for (Map.Entry<String, ProfileLeafStats> e : laneStats().entrySet()) {
            try { e.getValue().reference = LaneType.valueOf(e.getKey()); } catch (Exception ex) { e.getValue().reference = LaneType.NONE; }
            laneStats.add(e.getValue());
        }
        championStats = new java.util.ArrayList<>();
        for (Map.Entry<Integer, ProfileLeafStats> e : championStats().entrySet()) {
            e.getValue().reference = e.getKey();
            championStats.add(e.getValue());
        }
    }

    @JsonIgnore
    public ProfileLeafStats total() {
        ProfileLeafStats total = new ProfileLeafStats();
        forEachLeaf(total::merge);
        return total;
    }

    @JsonIgnore
    public Map<Integer, ProfileLeafStats> championStats() {
        Map<Integer, ProfileLeafStats> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map.Entry<Integer, Map<CanonicalQueue, Map<String, ProfileLeafStats>>> champion : champions.entrySet()) {
            ProfileLeafStats total = new ProfileLeafStats();
            forEachQueueLeaf(champion.getValue(), total::merge);
            result.put(champion.getKey(), total);
        }
        return result;
    }

    @JsonIgnore
    public Map<CanonicalQueue, ProfileLeafStats> queueStats() {
        Map<CanonicalQueue, ProfileLeafStats> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map<CanonicalQueue, Map<String, ProfileLeafStats>> queues : champions.values()) if (queues != null)
            for (Map.Entry<CanonicalQueue, Map<String, ProfileLeafStats>> queue : queues.entrySet()) {
                ProfileLeafStats total = result.computeIfAbsent(queue.getKey(), ignored -> new ProfileLeafStats());
                forEachLeaf(queue.getValue(), total::merge);
            }
        return result;
    }

    @JsonIgnore
    public Map<String, ProfileLeafStats> laneStats() {
        Map<String, ProfileLeafStats> result = new LinkedHashMap<>();
        if (champions == null) return result;
        for (Map<CanonicalQueue, Map<String, ProfileLeafStats>> queues : champions.values()) if (queues != null)
            for (Map<String, ProfileLeafStats> lanes : queues.values()) if (lanes != null)
                for (Map.Entry<String, ProfileLeafStats> lane : lanes.entrySet())
                    result.computeIfAbsent(lane.getKey(), ignored -> new ProfileLeafStats()).merge(lane.getValue());
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
        ProfileLeafStats leaf = leaf(player.champion, CanonicalQueue.from(match.queue), player.lane);
        leaf.games++;
        if (player.win) leaf.wins++;
        if (player.team == no.stelar7.api.r4j.basic.constants.types.lol.TeamType.BLUE) { leaf.blueGames++; if (player.win) leaf.blueWins++; }
        else if (player.team == no.stelar7.api.r4j.basic.constants.types.lol.TeamType.RED) { leaf.redGames++; if (player.win) leaf.redWins++; }
        int[] kda = kda(player.kda);
        leaf.kills += kda[0]; leaf.deaths += kda[1]; leaf.assists += kda[2];
        leaf.damage += player.damage; leaf.damageBuilding += player.damageBuilding;
        if (player.damageTaken != null) leaf.damageTaken = leaf.damageTaken == null ? (long)player.damageTaken : leaf.damageTaken + player.damageTaken;
        leaf.healing += player.healing; leaf.vision += player.visionScore; leaf.ward += player.ward; leaf.wardKilled += player.wardKilled;
        leaf.cs += player.cs; leaf.gold += player.goldEarned;
        if (player.rankProgress != null && player.rankProgress.gain != null) leaf.lpGain += player.rankProgress.gain;
        if (player.championLevel != null) leaf.championLevelTotal = leaf.championLevelTotal == null ? (long)player.championLevel : leaf.championLevelTotal + player.championLevel;
        leaf.doubles += player.doubles; leaf.triples += player.triples; leaf.quadruples += player.quadruples; leaf.pentas += player.pentas;
        leaf.q += player.q; leaf.w += player.w; leaf.e += player.e; leaf.r += player.r; leaf.d += player.d; leaf.f += player.f;
        if (arena) { if (player.subTeamPlacement==1) leaf.arenaFirst++; else if (player.subTeamPlacement==2) leaf.arenaSecond++; else if (player.subTeamPlacement==3) leaf.arenaThird++; leaf.arenaPlacementSum += player.subTeamPlacement; }
        leaf.playtime += Math.max(0, match.timeEnd - match.timeStart);
        leaf.lastPlayedAt = Math.max(leaf.lastPlayedAt, match.timeStart);
        if (teamKills>0) leaf.killParticipationSum += ((double)(kda[0]+kda[2])/teamKills)*100;
        if (enemyTeamKills>0) leaf.deathShareSum += ((double)kda[1]/enemyTeamKills)*100;
        if (player.pings != null) for (Map.Entry<String, Integer> entry : player.pings.entrySet())
            if (entry.getKey() != null && entry.getValue() != null) pings.merge(entry.getKey(), entry.getValue().longValue(), Long::sum);
        if (player.summonerSpell1 != 0) spellOne.merge(player.summonerSpell1, 1L, Long::sum);
        if (player.summonerSpell2 != 0) spellTwo.merge(player.summonerSpell2, 1L, Long::sum);
        updateTime(match.timeStart, match.timeEnd);
    }

    private ProfileLeafStats leaf(int champion, CanonicalQueue queue, LaneType lane) {
        return champions.computeIfAbsent(champion, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(queue, ignored -> new LinkedHashMap<>())
            .computeIfAbsent(laneKey(lane), ignored -> new ProfileLeafStats());
    }

    private static int[] kda(String v) {
        String[] a = v == null ? new String[0] : v.split("/");
        if (a.length != 3) return new int[3];
        return new int[]{ integer(a[0]), integer(a[1]), integer(a[2]) };
    }

    private static int integer(String v) {
        try { return Integer.parseInt(v); } catch (Exception ignored) { return 0; }
    }

    private static String laneKey(LaneType lane) {
        return lane == null || lane == LaneType.NONE ? "UNKNOWN" : lane.name();
    }

    private void updateTime(long start, long end) {
        timeEnd = Math.max(timeEnd, end);
        oldestMatchAt = oldestMatchAt == 0 ? start : Math.min(oldestMatchAt, start);
        newestMatchAt = Math.max(newestMatchAt, start);
    }

    private void forEachLeaf(Consumer<ProfileLeafStats> consumer) {
        if (champions != null) for (Map<CanonicalQueue, Map<String, ProfileLeafStats>> queues : champions.values()) forEachQueueLeaf(queues, consumer);
    }

    private static void forEachQueueLeaf(Map<CanonicalQueue, Map<String, ProfileLeafStats>> queues, Consumer<ProfileLeafStats> consumer) {
        if (queues != null) for (Map<String, ProfileLeafStats> lanes : queues.values()) forEachLeaf(lanes, consumer);
    }

    private static void forEachLeaf(Map<String, ProfileLeafStats> lanes, Consumer<ProfileLeafStats> consumer) {
        if (lanes != null) for (ProfileLeafStats stats : lanes.values()) if (stats != null) consumer.accept(stats);
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
