package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.safjnest.lol.model.ChampionStatistics;
import com.safjnest.lol.model.statistics.CanonicalQueue;
import com.safjnest.lol.model.statistics.ProfileMatchupLeaf;
import com.safjnest.lol.model.statistics.ProfileMatchups;
import com.safjnest.lol.model.statistics.shared.ProfileLeafStats;
import com.safjnest.utils.JsonCodec;

import no.stelar7.api.r4j.basic.constants.types.lol.LaneType;

public class MatchupRankingAnalyzerTest {

    @Test
    public void championMatchupsUseDominanceMetricsAndKeepRawInputUntouched() {
        Map<Integer, ChampionStatistics.Matchup> matchups = new LinkedHashMap<>();
        matchups.put(1, matchup(100, 90, 600, 8, 0.30, 0.72, 0.99,
            8d, 500d, 0.02, 1200d, 2d, 1d, 2d));
        for (int id = 2; id <= 6; id++)
            matchups.put(id, matchup(100, 50, 0, 0, 0.10, 0.50, id == 2 ? 1.0 : 0.0));

        ChampionStatistics raw = championStatistics(matchups, List.of());
        ChampionStatistics ranked = MatchupRankingAnalyzer.rank(raw);

        assertEquals(Integer.valueOf(1), ranked.matchups().keySet().iterator().next());
        assertEquals("S+", ranked.matchups().get(1).tier());
        assertTrue(ranked.matchups().get(1).matchupScore() >= 2);
        assertNull(raw.matchups().get(1).tier());
        assertNull(raw.matchups().get(1).matchupScore());
        ChampionStatistics decoded = ChampionStatistics.fromJson(JsonCodec.toJson(ranked));
        assertEquals("S+", decoded.matchups().get(1).tier());
        assertEquals(ranked.matchups().get(1).xpDiffAt15(), decoded.matchups().get(1).xpDiffAt15());

        // Opponent ban rate is carried through but is not a scoring input.
        assertEquals(ranked.matchups().get(2).matchupScore(), ranked.matchups().get(3).matchupScore(), 0d);
    }

    @Test
    public void championMatchupsUseDForTheBottomTail() {
        Map<Integer, ChampionStatistics.Matchup> matchups = new LinkedHashMap<>();
        matchups.put(1, matchup(100, 10, -600, -8, 0.01, 0.30, 0));
        for (int id = 2; id <= 6; id++)
            matchups.put(id, matchup(100, 50, 0, 0, 0.10, 0.50, 0));

        ChampionStatistics ranked = MatchupRankingAnalyzer.rank(championStatistics(matchups, List.of()));
        List<Integer> order = ranked.matchups().keySet().stream().toList();

        assertEquals(Integer.valueOf(1), order.get(order.size() - 1));
        assertEquals("D", ranked.matchups().get(1).tier());
    }

    @Test
    public void lowSampleWinrateIsShrunkAndMarkedUnreliable() {
        Map<Integer, ChampionStatistics.Matchup> matchups = new LinkedHashMap<>();
        matchups.put(1, matchup(1, 1, 900, 20, 1, 1, 0));
        matchups.put(2, matchup(100, 60, 0, 0, 0, 0.5, 0));

        ChampionStatistics ranked = MatchupRankingAnalyzer.rank(championStatistics(matchups, List.of()));
        ChampionStatistics.Matchup lowSample = ranked.matchups().get(1);

        assertEquals(false, lowSample.reliable());
        assertTrue(lowSample.adjustedWinrate() < lowSample.winrate());
        assertTrue(lowSample.adjustedWinrate() > 0.5);
    }

    @Test
    public void missingTimelineMetricsRemainAbsentWhileObservedZeroHasSamples() {
        ChampionStatistics.Matchup missing = new ChampionStatistics.Matchup(100, 50);
        ChampionStatistics.Matchup observedZero = matchup(100, 50, 0, 0, 0.10, 0.50, 0,
            2d, 300d, 0.10, 0d, 0d, 0d, 0d);

        assertNull(missing.xpDiffAt15());
        assertEquals(0, missing.xpDiffAt15Games());
        assertEquals(Double.valueOf(0), observedZero.xpDiffAt15());
        assertEquals(100, observedZero.xpDiffAt15Games());
        assertEquals(0, missing.levelDiffAt15Games());
        assertEquals(100, observedZero.levelDiffAt15Games());
        assertTrue(JsonCodec.toJson(missing).contains("\"xpDiffAt15Games\":0"));
        assertTrue(JsonCodec.toJson(missing).contains("\"levelDiffAt15Games\":0"));
        assertTrue(JsonCodec.toJson(observedZero).contains("\"xpDiffAt15Games\":100"));
    }

    @Test
    public void championSynergiesAreScoredInsideTheirOwnAllyLaneAndIgnorePickrate() {
        List<ChampionStatistics.LaneSynergy> synergies = List.of(
            new ChampionStatistics.LaneSynergy(10, LaneType.BOT, 100, 80, 0.80, 0.01),
            new ChampionStatistics.LaneSynergy(11, LaneType.BOT, 100, 20, 0.20, 0.99),
            new ChampionStatistics.LaneSynergy(20, LaneType.UTILITY, 100, 20, 0.20, 0.50)
        );

        ChampionStatistics ranked = MatchupRankingAnalyzer.rank(
            championStatistics(Map.of(), synergies));

        assertEquals(3, ranked.laneSynergies().size());
        assertEquals(10, ranked.laneSynergies().get(0).allyChampion());
        assertEquals("A", ranked.laneSynergies().get(0).tier());
        assertEquals(11, ranked.laneSynergies().get(2).allyChampion());
        assertEquals("C", ranked.laneSynergies().get(2).tier());
    }

    @Test
    public void profileRanksMatchupsAndSynergiesSeparatelyAndKeepsSpecialRowsAtTheEnd() {
        ProfileMatchups raw = profile();
        ProfileMatchups projected = raw.withMinGames(2);
        MatchupRankingAnalyzer.rank(projected);

        ProfileMatchupLeaf rawLeaf = raw.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP");
        ProfileMatchupLeaf leaf = projected.champions().get(1).get(CanonicalQueue.RANKED_SOLO).get("TOP");

        assertNull(rawLeaf.matchups.get("2").tier);
        assertEquals("2", leaf.matchups.keySet().iterator().next());
        assertEquals("0", leaf.matchups.keySet().stream().toList().get(leaf.matchups.size() - 2));
        assertEquals("others", leaf.matchups.keySet().stream().toList().get(leaf.matchups.size() - 1));
        assertNull(leaf.matchups.get("0").tier);
        assertNull(leaf.matchups.get("others").tier);

        assertEquals("5", leaf.synergies.keySet().iterator().next());
        assertEquals("others", leaf.synergies.keySet().stream().toList().get(leaf.synergies.size() - 1));
        assertNull(leaf.synergies.get("others").tier);
    }

    private static ChampionStatistics championStatistics(
            Map<Integer, ChampionStatistics.Matchup> matchups,
            List<ChampionStatistics.LaneSynergy> synergies) {
        return new ChampionStatistics(
            null,
            new ChampionStatistics.Overview(1_000, 600, 0, 300, 0.5, 0.6, null, null, null, null, null),
            List.of(),
            matchups,
            synergies,
            List.of(),
            null
        );
    }

    private static ChampionStatistics.Matchup matchup(
            int games,
            int wins,
            int goldDiff,
            double csDiff,
            double soloKillRate,
            double killParticipation,
            double opponentBanRate) {
        return matchup(games, wins, goldDiff, csDiff, soloKillRate, killParticipation, opponentBanRate,
            2d, 300d, 0.10, 0d, 0d, 0d, 0d);
    }

    private static ChampionStatistics.Matchup matchup(
            int games,
            int wins,
            int goldDiff,
            double csDiff,
            double soloKillRate,
            double killParticipation,
            double opponentBanRate,
            double kda,
            double goldPerMinute,
            double deathShare,
            double xpDiff,
            double killDiff,
            double levelDiff,
            double plateDiff) {
        double winrate = (double) wins / games;
        return new ChampionStatistics.Matchup(
            games,
            wins,
            winrate,
            winrate - 0.5,
            goldDiff,
            csDiff,
            soloKillRate,
            killParticipation,
            opponentBanRate,
            games,
            kda,
            goldPerMinute,
            deathShare,
            xpDiff,
            killDiff,
            levelDiff,
            plateDiff,
            null,
            null,
            null,
            null,
            null,
            timelineGames(goldDiff, games),
            timelineGames(csDiff, games),
            timelineGames(xpDiff, games),
            timelineGames(killDiff, games),
            timelineGames(levelDiff, games),
            timelineGames(plateDiff, games)
        );
    }

    private static long timelineGames(Number value, int games) {
        return value == null ? 0 : games;
    }

    private static ProfileMatchups profile() {
        ProfileMatchupLeaf leaf = new ProfileMatchupLeaf();
        leaf.games = 305;
        leaf.wins = 153;

        leaf.matchups.put("2", profileStats(100, 80, true));
        leaf.matchups.put("3", profileStats(100, 50, false));
        leaf.matchups.put("4", profileStats(100, 30, false));
        leaf.matchups.put("0", profileStats(4, 2, false));
        leaf.matchups.put("99", profileStats(1, 1, true));

        leaf.synergies.put("5", profileStats(100, 80, true));
        leaf.synergies.put("6", profileStats(100, 40, false));
        leaf.synergies.put("98", profileStats(1, 1, true));

        Map<String, ProfileMatchupLeaf> positions = new LinkedHashMap<>();
        positions.put("TOP", leaf);
        Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queues = new LinkedHashMap<>();
        queues.put(CanonicalQueue.RANKED_SOLO, positions);
        Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champions = new LinkedHashMap<>();
        champions.put(1, queues);
        return new ProfileMatchups(null, 0, 0, 0, champions);
    }

    private static ProfileLeafStats profileStats(int games, int wins, boolean strong) {
        ProfileLeafStats stats = new ProfileLeafStats();
        stats.games = games;
        stats.wins = wins;
        stats.kills = strong ? games * 6L : games * 2L;
        stats.deaths = strong ? games : games * 2L;
        stats.assists = strong ? games * 8L : games * 4L;
        stats.gold = strong ? games * 14_000L : games * 10_000L;
        stats.playtime = games * 30L * 60_000L;
        stats.killParticipationSum = games * (strong ? 75d : 50d);
        stats.deathShareSum = games * (strong ? 10d : 20d);
        return stats;
    }
}
