package com.safjnest.lol.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

public class MatchupTierAnalyzerTest {

    @Test
    public void championResponseRanksDominantMatchupAsSPlus() {
        Map<Integer, ChampionStatistics.Matchup> matchups = new LinkedHashMap<>();
        matchups.put(1, championMatchup(100, 90, 600, 8, 0.30, 0.72));
        for (int id = 2; id <= 6; id++)
            matchups.put(id, championMatchup(100, 50, 0, 0, 0.10, 0.50));

        ChampionStatistics ranked = MatchupTierAnalyzer.rank(championStatistics(matchups));

        assertEquals(Integer.valueOf(1), ranked.matchups().keySet().iterator().next());
        ChampionStatistics.Matchup free = ranked.matchups().get(1);
        assertEquals("S+", free.tier());
        assertTrue(free.adjustedWinrate() > 0.5);
        assertTrue(free.weightedDelta() > 0);
        assertTrue(free.reliable());
    }

    @Test
    public void championResponseRanksCollapsedMatchupAsF() {
        Map<Integer, ChampionStatistics.Matchup> matchups = new LinkedHashMap<>();
        matchups.put(1, championMatchup(100, 10, -600, -8, 0.01, 0.30));
        for (int id = 2; id <= 6; id++)
            matchups.put(id, championMatchup(100, 50, 0, 0, 0.10, 0.50));

        ChampionStatistics ranked = MatchupTierAnalyzer.rank(championStatistics(matchups));
        List<Integer> order = ranked.matchups().keySet().stream().toList();

        assertEquals(Integer.valueOf(1), order.get(order.size() - 1));
        assertEquals("F", ranked.matchups().get(1).tier());
        assertTrue(ranked.matchups().get(1).weightedDelta() < 0);
    }

    @Test
    public void profileResponseRanksNamedMatchupsAndKeepsOthersLast() {
        ProfileMatchupLeaf leaf = new ProfileMatchupLeaf();
        leaf.games = 600;
        leaf.wins = 300;
        for (int id = 1; id <= 6; id++) {
            ProfileLeafStats stats = profileStats(100, id == 1 ? 90 : 50, id == 1);
            leaf.matchups.put(String.valueOf(id), stats);
        }
        ProfileLeafStats others = profileStats(20, 10, false);
        leaf.matchups.put("others", others);

        Map<String, ProfileMatchupLeaf> positions = new LinkedHashMap<>();
        positions.put("TOP", leaf);
        Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>> queues = new LinkedHashMap<>();
        queues.put(CanonicalQueue.RANKED_SOLO, positions);
        Map<Integer, Map<CanonicalQueue, Map<String, ProfileMatchupLeaf>>> champions = new LinkedHashMap<>();
        champions.put(1, queues);

        ProfileMatchups profile = new ProfileMatchups(null, 0, 0, 0, champions);
        MatchupTierAnalyzer.rank(profile);

        List<String> order = leaf.matchups.keySet().stream().toList();
        assertEquals("1", order.get(0));
        assertEquals("others", order.get(order.size() - 1));
        assertEquals("S+", leaf.matchups.get("1").tier);
        assertTrue(leaf.matchups.get("1").matchupScore > 0);
        assertFalse(leaf.matchups.get("1").weightedDelta == null);
        assertNull(leaf.matchups.get("others").tier);
    }

    private static ChampionStatistics championStatistics(Map<Integer, ChampionStatistics.Matchup> matchups) {
        return new ChampionStatistics(
            null,
            new ChampionStatistics.Overview(1_000, 600, 0, 300, 0.5, 0.6, null, null, null, null, null),
            List.of(),
            matchups,
            List.of(),
            List.of(),
            null
        );
    }

    private static ChampionStatistics.Matchup championMatchup(
        int games,
        int wins,
        int goldDiff,
        double csDiff,
        double soloKillRate,
        double killParticipation
    ) {
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
            null,
            games
        );
    }

    private static ProfileLeafStats profileStats(int games, int wins, boolean dominant) {
        ProfileLeafStats stats = new ProfileLeafStats();
        stats.games = games;
        stats.wins = wins;
        stats.kills = dominant ? 600 : 250;
        stats.deaths = dominant ? 100 : 250;
        stats.assists = dominant ? 800 : 500;
        stats.gold = dominant ? 1_400_000 : 1_000_000;
        stats.playtime = games * 30L * 60_000L;
        stats.killParticipationSum = games * (dominant ? 75d : 50d);
        stats.deathShareSum = games * (dominant ? 10d : 20d);
        return stats;
    }
}
